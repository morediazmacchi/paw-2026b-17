package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.services.PasswordNotSetException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class WebAuthConfig {

    // Los dos son beans de services, encontrados por el @ComponentScan de WebConfig.
    // El encoder vive alla (ver PasswordEncoderConfig) porque tambien lo usa
    // UserServiceImpl.register() para hashear al registrarse: es logica de
    // negocio, no configuracion de filtros HTTP.
    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    // PasswordNotSetException (usuario fantasma) no es una UsernameNotFoundException:
    // por eso el DaoAuthenticationProvider no la "esconde" como generica (eso es lo
    // que le pasaria si lo fuera, via hideUserNotFoundExceptions) y llega hasta aca
    // envuelta en InternalAuthenticationServiceException. Cualquier otra falla
    // (no existe, password incorrecta) cae en el else: mismo mensaje generico de siempre.
    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            if (exception.getCause() instanceof PasswordNotSetException) {
                final String email = ((PasswordNotSetException) exception.getCause()).getEmail();
                final String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
                response.sendRedirect(request.getContextPath() + "/login?needsPassword&email=" + encodedEmail);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/login?error");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http.authenticationProvider(authenticationProvider())
                .authorizeRequests(auth -> auth
                        .antMatchers("/css/**", "/js/**", "/images/**", "/image/**").permitAll()
                        .antMatchers("/login", "/login/nueva-contrasena", "/registro").permitAll()
                        .antMatchers("/error/**").permitAll()
                        .antMatchers("/favicon.ico").permitAll()
                        .antMatchers(HttpMethod.GET, "/", "/eventos").permitAll()
                        .antMatchers(HttpMethod.GET, "/eventos/{id:[0-9]+}").permitAll()
                        .antMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        // El campo del form se llama "email" (LoginController/login.jsp),
                        // no "username": el usuario piensa en termino de su email.
                        .usernameParameter("email")
                        .failureHandler(loginFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        // GET en vez del default POST-only: no rompe con un <a href>
                        // simple en la navbar, y el CSRF que ese default evita ya
                        // esta desactivado en toda la app (ver mas abajo).
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                        .permitAll())
                // Desactivado a proposito: la catedra lo pidio asi hasta ver
                // seguridad web (CLAUDE.md, seccion 11).
                .csrf().disable();
        return http.build();
    }
}
