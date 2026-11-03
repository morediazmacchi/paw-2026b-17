// Puente entre nuestro User (email/password/roles) y el UserDetails que
// Spring Security necesita para autenticar. Vive en webapp porque es parte de la
// capa de seguridad: services no sabe nada de como se autentica alguien, solo expone
// UserService. Este paquete (webapp.auth) es el de AUTENTICACION: lo descubre el
// @ComponentScan de WebAuthConfig (RootConfig no escanea webapp). La AUTORIZACION
// (@PreAuthorize, EventSecurity) esta en webapp.security.
package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

@Component
public class UserDetailsServiceImpl implements UserDetailsService {

    // Hash BCrypt con formato valido que no corresponde a ninguna contraseña: lo usan las cuentas sin clave.
    private static final String UNUSABLE_PASSWORD_HASH = "$2a$10$.FMTahov29ELSZgnu18DKRYfmt07CJQXelsz6BIPWdkry5AHOVcjq";

    private final UserService userService;

    @Autowired
    public UserDetailsServiceImpl(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public PlancitoUserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
        final User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email o contraseña invalidos"));

        // password == null: es uno de los usuarios creados por publicar/inscribirse
        // antes de que existiera login. A pedido, este caso SI se distingue de
        // "no existe": el login lo detecta (LoginFailureHandler, webapp) y manda a
        // elegir una contraseña en vez del error generico.
        // Usuario fantasma (sin contraseña): NO se distingue de una cuenta con otra clave. Se devuelve con un
        // hash que ninguna contraseña verifica, asi que el login falla con el error generico como si la clave
        // fuera incorrecta (no revela que la cuenta existe). Completar la cuenta se hace desde /registro.
        final String passwordHash = user.getPassword() == null ? UNUSABLE_PASSWORD_HASH : user.getPassword();

        // enabled = tiene clave y confirmo el mail; accountNonLocked = un admin NO la suspendio (ver User.isActive(),
        // que es la suma de las dos). DaoAuthenticationProvider NO decide con esto antes de comparar la clave (ver
        // WebAuthConfig): despues, AccountStatusAfterPasswordCheck lanza LockedException si esta suspendida o
        // DisabledException si no confirmo el mail, y el failure handler mira solo el tipo de excepcion.
        return new PlancitoUserDetails(user.getId(), user.getEmail(), user.getName(), passwordHash,
                user.hasPassword() && user.isVerified(), !user.isSuspended(), toAuthorities(user));
    }

    // El nombre que espera Spring Security ("ROLE_ADMIN") sale del enum (Role.getAuthority()).
    private Collection<GrantedAuthority> toAuthorities(final User user) {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getAuthority()))
                .collect(Collectors.toList());
    }
}
