// Puente entre nuestro User (email/password/roles) y el UserDetails que
// Spring Security necesita para autenticar. Vive en webapp porque es parte de la
// capa de seguridad: services no sabe nada de como se autentica alguien, solo expone
// UserService. Este paquete (webapp.auth) es el de AUTENTICACION: lo descubre el
// @ComponentScan de WebAuthConfig (RootConfig no escanea webapp). La AUTORIZACION
// (@PreAuthorize, EventSecurity) esta en webapp.security.
package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.PasswordNotSetException;
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

    private final UserService userService;

    @Autowired
    public UserDetailsServiceImpl(final UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
        final User user = userService.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Email o contraseña invalidos"));

        // password == null: es uno de los usuarios creados por publicar/inscribirse
        // antes de que existiera login. A pedido, este caso SI se distingue de
        // "no existe": el login lo detecta (LoginFailureHandler, webapp) y manda a
        // elegir una contraseña en vez del error generico.
        if (user.getPassword() == null) {
            throw new PasswordNotSetException(user.getEmail());
        }

        // disabled=true si todavia no confirmo el mail, O si un admin suspendio
        // la cuenta (ver User.isSuspended()/UserService.suspend()):
        // DaoAuthenticationProvider chequea esto ANTES de comparar la
        // contraseña y tira DisabledException (WebAuthConfig.loginFailureHandler
        // distingue los dos casos, mirando el usuario de nuevo, para mandar a
        // "confirmá tu cuenta" o al aviso de cuenta suspendida segun corresponda).
        final boolean enabled = user.isVerified() && !user.isSuspended();
        return new PlancitoUserDetails(user.getId(), user.getEmail(), user.getName(), user.getPassword(),
                enabled, toAuthorities(user));
    }

    private Collection<GrantedAuthority> toAuthorities(final User user) {
        return user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
