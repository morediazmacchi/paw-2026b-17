package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.PasswordNotSetException;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
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
import org.springframework.web.servlet.LocaleResolver;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

// RootConfig no escanea webapp: el paquete de autenticacion (webapp.auth) se descubre desde aca.
@ComponentScan("ar.edu.itba.paw.webapp.auth")
@Configuration
@EnableWebSecurity
public class WebAuthConfig {

    // Como Spring Security busca las credenciales (patron Strategy): UserDetailsServiceImpl,
    // que esta en webapp.auth y lo encuentra el @ComponentScan de esta clase.
    @Autowired
    private UserDetailsService userDetailsService;

    // Bean de services, encontrado por el @ComponentScan de RootConfig. Vive alla (ver
    // PasswordEncoderConfig) porque tambien lo usa UserServiceImpl.register() para hashear
    // al registrarse: es logica de negocio, no configuracion de filtros HTTP.
    @Autowired
    private PasswordEncoder passwordEncoder;

    // @Lazy por la misma razon que en WebConfig: evitar construir UserService
    // (y su DAO, que necesita el DataSource) mientras esta config todavia se
    // esta armando.
    @Autowired
    @Lazy
    private UserService userService;

    // Bean definido en WebConfig (mismo contexto, @Import de por medio): para
    // que el login sincronice la cookie de idioma con el de la cuenta (ver
    // loginSuccessHandler() mas abajo).
    @Autowired
    private LocaleResolver localeResolver;

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
            // mail) O si un admin la suspendio (ver User.isSuspended()). A
            // diferencia de PasswordNotSetException, esta SI la tira directo el
            // provider (no envuelta), asi que no hace falta getCause() -- pero
            // hay que volver a mirar el usuario para saber cual de los dos motivos
            // es, porque el mensaje que corresponde no es el mismo.
            if (exception instanceof DisabledException) {
                final String email = request.getParameter("email");
                final String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
                final boolean suspended = email != null
                        && userService.findByEmail(email).map(User::isSuspended).orElse(false);
                final String param = suspended ? "suspended" : "unverified";
                response.sendRedirect(request.getContextPath() + "/login?" + param + "&email=" + encodedEmail);
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
            // PAW20-82: si esta cuenta ya habia elegido un idioma antes (desde
            // este navegador o desde cualquier otro), se lo reaplicamos a la
            // cookie de este navegador: sin esto, loguearse en un dispositivo
            // nuevo (o con la cookie borrada) te dejaba en el idioma que la
            // cookie/navegador diga, no en el que la cuenta tiene guardado.
            userService.findByEmail(authentication.getName())
                    .map(User::getLocale)
                    .ifPresent(locale -> localeResolver.setLocale(request, response, new Locale(locale)));
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
                        .antMatchers("/css/**", "/js/**", "/image/**").permitAll()
                        .antMatchers("/login", "/login/nueva-contrasena", "/registro").permitAll()
                        .antMatchers("/login/recuperar", "/login/restablecer").permitAll()
                        .antMatchers("/login/reenviar-confirmacion", "/confirmar-cuenta").permitAll()
                        // Boton de idioma de la navbar (PAW20-75): tiene que andar
                        // tambien para un visitante anonimo.
                        .antMatchers("/idioma").permitAll()
                        .antMatchers("/error/**").permitAll()
                        .antMatchers("/favicon.ico", "/favicon.svg").permitAll()
                        .antMatchers(HttpMethod.GET, "/", "/eventos").permitAll()
                        .antMatchers(HttpMethod.GET, "/eventos/{id:[0-9]+}").permitAll()
                        // Mas especifico primero: Spring Security usa el PRIMER
                        // antMatcher que matchea, asi que gestion de roles (solo
                        // superadmin) tiene que ir antes del "/admin/**" general
                        // (cualquier admin), si no el general lo captura primero.
                        .antMatchers("/admin/usuarios/*/promover", "/admin/usuarios/*/revocar")
                            .hasRole(Role.SUPERADMIN.name())
                        .antMatchers("/admin/**").hasRole(Role.ADMIN.name())
                        // Todo lo demas exige sesion. QUIEN puede hacer que sobre un evento (ser el
                        // organizador, poder verlo...) no se decide aca sino con @PreAuthorize en los
                        // controllers (ver EventSecurity y WebConfig, donde se activa).
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
                // Desactivado a proposito.
                .csrf().disable();
        return http.build();
    }
}
