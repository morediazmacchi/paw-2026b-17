// Puente entre nuestro User (email/password/roles) y el UserDetails que
// Spring Security necesita para autenticar. Vive en services porque solo
// depende de spring-security-core (nunca de spring-web/filtros).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
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

        // disabled=true si todavia no confirmo el mail: DaoAuthenticationProvider
        // chequea esto ANTES de comparar la contraseña y tira DisabledException
        // (WebAuthConfig.loginFailureHandler la distingue para mandarlo a
        // "confirmá tu cuenta" en vez del error generico).
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .disabled(!user.isVerified())
                .authorities(toAuthorities(user))
                .build();
    }

    private Collection<GrantedAuthority> toAuthorities(final User user) {
        return user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
