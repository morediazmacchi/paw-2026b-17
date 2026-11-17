package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.webapp.auth.AccountStatusAfterPasswordCheck;
import ar.edu.itba.paw.webapp.auth.AccessDeniedAsNotFoundHandler;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

// RootConfig no escanea webapp: el paquete de autenticacion (webapp.auth) se descubre desde aca.
@ComponentScan("ar.edu.itba.paw.webapp.auth")
@Configuration
@EnableWebSecurity
public class WebAuthConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAuthConfig.class);

    // "Recordarme": la cookie dura 30 dias.
    private static final int REMEMBER_ME_SECONDS = 60 * 60 * 24 * 30;
    // Largo minimo de la clave que firma esa cookie: una mas corta se descarta (ver rememberMeKey()).
    static final int MIN_REMEMBER_ME_KEY_LENGTH = 32;

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

    // La clave que firma la cookie de "recordarme". Nunca va escrita en el codigo ni en un archivo versionado (quien la
    // conozca puede fabricar la cookie de cualquier cuenta): sale de la propiedad de sistema app.remember-me-key o de
    // la variable de entorno APP_REMEMBER_ME_KEY. Si no esta (o es muy corta) se genera una aleatoria al arrancar:
    // es segura, pero al reiniciar la app las cookies de "recordarme" ya emitidas dejan de valer y hay que volver a
    // iniciar sesion. En produccion conviene fijarla para que sobreviva a los reinicios.
    static String rememberMeKey(final String configured) {
        if (configured != null && configured.trim().length() >= MIN_REMEMBER_ME_KEY_LENGTH) {
            return configured.trim();
        }
        LOGGER.warn("No hay una clave de 'recordarme' valida (app.remember-me-key / APP_REMEMBER_ME_KEY, minimo {} "
                + "caracteres): se genera una al azar y las cookies emitidas se pierden al reiniciar.",
                MIN_REMEMBER_ME_KEY_LENGTH);
        return UUID.randomUUID() + UUID.randomUUID().toString();
    }

    private static String configuredRememberMeKey() {
        final String fromProperty = System.getProperty("app.remember-me-key");
        return fromProperty != null ? fromProperty : System.getenv("APP_REMEMBER_ME_KEY");
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // El estado de la cuenta (suspendida, sin verificar...) se evalua DESPUES de verificar la clave:
        // los chequeos previos de Spring (que corren antes) se reemplazan por uno vacio.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(new AccountStatusAfterPasswordCheck());
        return provider;
    }

    // Con la contraseña INCORRECTA todos los casos (cuenta inexistente, suspendida, sin verificar, fantasma) llegan
    // aca como BadCredentialsException y terminan en el mismo "/login?error": no se revela si la cuenta existe
    // ni en que estado esta. El estado solo se informa si la contraseña era correcta (ver
    // AccountStatusAfterPasswordCheck, que corre despues de comparar la clave).
    @Bean
    public AuthenticationFailureHandler loginFailureHandler() {
        return (request, response, exception) -> {
            // Solo llega LockedException (cuenta suspendida por un admin) o DisabledException (todavia no confirmo el
            // mail) con la contraseña CORRECTA: el estado se mira despues de comparar la clave (ver
            // AccountStatusAfterPasswordCheck), asi que no filtra nada a quien no es la persona. El tipo de la
            // excepcion ya dice cual de los dos es: no hace falta volver a buscar al usuario.
            if (exception instanceof LockedException || exception instanceof DisabledException) {
                final String email = request.getParameter("email");
                final String encodedEmail = email == null ? "" : URLEncoder.encode(email, StandardCharsets.UTF_8);
                final String param = exception instanceof LockedException ? "suspended" : "unverified";
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

    // Registro de las sesiones abiertas, para poder cerrar las de un usuario al quitarle acceso
    // (suspension, rol ADMIN revocado): ver SessionExpiringListener. Se alimenta solo, via
    // sessionManagement().maximumSessions(...) de filterChain y HttpSessionEventPublisher (ver
    // el listener HttpSessionEventPublisher de web.xml). Vive en memoria de esta JVM.
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // StrictHttpFirewall rechaza URLs raras ("//", ";", "%2F", "/./") lanzando RequestRejectedException; sin
    // esto el contenedor la convierte en un 500. Spring Security toma este bean solo. El firewall bloquea
    // igual: esto solo cambia el status (400) y el ruido en el log.
    @Bean
    public RequestRejectedHandler requestRejectedHandler() {
        return new HttpStatusRequestRejectedHandler(HttpServletResponse.SC_BAD_REQUEST);
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
                        // Perfil publico de una persona: lo ve cualquiera (editar la biografia, un POST, exige sesion).
                        .antMatchers(HttpMethod.GET, "/usuarios/{id:[0-9]+}", "/usuarios/{id:[0-9]+}/portada").permitAll()
                        // Mas especifico primero: Spring Security usa el PRIMER
                        // antMatcher que matchea, asi que sacar el rol admin (solo
                        // superadmin) tiene que ir antes del "/admin/**" general
                        // (cualquier admin), si no el general lo captura primero.
                        // Dar el rol admin ("/promover") NO esta aca: lo puede hacer
                        // cualquier admin, asi que cae en el "/admin/**" de abajo.
                        .antMatchers("/admin/usuarios/*/revocar")
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
                // maximumSessions(-1) = sin limite de logins simultaneos: solo sirve para rastrear las
                // sesiones en el registro e instalar el ConcurrentSessionFilter, que corta (antes de
                // llegar a cualquier controller) una sesion marcada como expirada y manda al login.
                // Denegacion A NIVEL FILTRO (usuario logueado sin el rol de un antMatcher): la misma respuesta
                // generica que cualquier otra cosa a la que no se pueda acceder (404), con cualquier verbo.
                // "Mantener la sesion iniciada" (casilla remember-me del login): una cookie firmada, de 30 dias, que
                // vuelve a cargar a la persona con UserDetailsServiceImpl. Al volver a entrar por la cookie Spring
                // aplica los mismos estados de cuenta (suspendida, sin confirmar) y los roles de ahora, y la firma
                // incluye el hash de la clave: cambiar la contrasena invalida las cookies viejas.
                .rememberMe(remember -> remember
                        .key(rememberMeKey(configuredRememberMeKey()))
                        .rememberMeParameter("remember-me")
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .userDetailsService(userDetailsService))
                .exceptionHandling(e -> e.accessDeniedHandler(new AccessDeniedAsNotFoundHandler("/error/404")))
                .sessionManagement(sm -> sm
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry())
                        .expiredUrl("/login?expired"))
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
