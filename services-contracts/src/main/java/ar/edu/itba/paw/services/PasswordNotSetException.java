// Lanzada por UserDetailsServiceImpl (modulo services) cuando el email existe
// pero es un usuario fantasma (sin password). Vive en services-contracts, no en
// services: webapp la necesita en tiempo de compilacion (WebAuthConfig la
// distingue via instanceof en el failure handler), y solo depende de
// services-contracts, nunca de services (ver pom.xml raiz).
package ar.edu.itba.paw.services;

import org.springframework.security.core.AuthenticationException;

public class PasswordNotSetException extends AuthenticationException {

    private final String email;

    public PasswordNotSetException(final String email) {
        super("El usuario existe pero todavia no tiene contraseña");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
