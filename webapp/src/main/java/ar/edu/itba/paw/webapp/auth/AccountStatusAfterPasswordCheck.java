package ar.edu.itba.paw.webapp.auth;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsChecker;

// Estado de la cuenta (sin verificar, suspendida...) que el login evalua DESPUES de comprobar la
// contraseña (DaoAuthenticationProvider.setPostAuthenticationChecks), no antes como hace Spring por
// defecto. Asi, con una contraseña incorrecta, una cuenta suspendida, sin verificar, fantasma o inexistente
// dan todas el mismo error generico: el estado solo se le dice a quien demostro que es la persona.
// Son los mismos chequeos que hace Spring Security (bloqueada, deshabilitada, vencida), solo que despues.
public class AccountStatusAfterPasswordCheck implements UserDetailsChecker {

    @Override
    public void check(final UserDetails user) {
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Cuenta bloqueada");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Cuenta deshabilitada");
        }
        if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("Cuenta vencida");
        }
        if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("Credenciales vencidas");
        }
    }
}
