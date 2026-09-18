package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.services.PasswordNotSetException;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
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
                String target = "/login?needsPassword&email=" + encodedEmail;
                // Si el login vino con ?redirect=... (boton "Inscribirme" anonimo),
                // lo arrastramos por todo el desvio de "elegir contraseña": si no,
                // al final del desvio se pierde y el usuario cae al home en vez de
                // volver a donde estaba.
                final String redirect = request.getParameter("redirect");
                if (SafeRedirect.isSafe(redirect)) {
                    target += "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8);
                }
                response.sendRedirect(request.getContextPath() + target);
                return;
            }
            // DisabledException: UserDetailsServiceImpl marca disabled=true si
            // verified=false (cuenta recien registrada, todavia no confirmo el
            // mail). A diferencia de PasswordNotSetException, esta SI la tira
            // directo el provider (no envuelta), asi que no hace falta getCause().
            if (exception instanceof DisabledException) {
                final String email = request.getParameter("email");
                final String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
                response.sendRedirect(request.getContextPath() + "/login?unverified&email=" + encodedEmail);
                return;
            }
            response.sendRedirect(request.getContextPath() + "/login?error");
        };
    }

    // Usado por el boton "Inscribirme" del detalle de evento cuando sos anonimo:
    // en vez de mandar directo a /login (que despues de loguearte te tira al
    // home), ese link agrega ?redirect=/eventos/{id} y esto te devuelve ahi. A
    // proposito NO reintenta la inscripcion sola: volves al detalle y apretas
    // el boton de nuevo, ya logueado.
    @Bean
    public AuthenticationSuccessHandler loginSuccessHandler() {
        final AuthenticationSuccessHandler defaultHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        return (request, response, authentication) -> {
            final String redirect = request.getParameter("redirect");
            if (SafeRedirect.isSafe(redirect)) {
                response.sendRedirect(request.getContextPath() + redirect);
                return;
            }
            defaultHandler.onAuthenticationSuccess(request, response, authentication);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http.authenticationProvider(authenticationProvider())
                .authorizeRequests(auth -> auth
                        .antMatchers("/css/**", "/js/**", "/images/**", "/image/**").permitAll()
                        .antMatchers("/login", "/login/nueva-contrasena", "/registro").permitAll()
                        .antMatchers("/login/recuperar", "/login/restablecer").permitAll()
                        .antMatchers("/login/reenviar-confirmacion", "/confirmar-cuenta").permitAll()
                        .antMatchers("/error/**").permitAll()
                        // El link de la lista de espera llega por mail: quien lo
                        // abre puede estar deslogueado o logueado con otra cuenta.
                        // El token que lleva la URL es la credencial.
                        .antMatchers("/invitacion/**").permitAll()
                        .antMatchers("/favicon.ico", "/favicon.svg").permitAll()
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
                        .successHandler(loginSuccessHandler())
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
