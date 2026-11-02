package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.services.UserAccessRevokedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Cierra las sesiones abiertas de un usuario al que se le quito acceso (lo suspendieron o le sacaron
// el rol ADMIN). Escucha UserAccessRevokedEvent, que publica UserServiceImpl DENTRO de su transaccion:
// AFTER_COMMIT garantiza que los cambios ya estan en la base cuando se expira (si no, el usuario
// podria volver a loguearse en el hueco y ver todavia los datos viejos) y que un rollback no expira
// nada. Marcar la sesion como expirada no la mata en el momento: el ConcurrentSessionFilter la corta
// en el proximo request de esa persona y la manda a /login?expired.
//
// Vive en webapp.auth, que descubre el @ComponentScan de WebAuthConfig (contexto raiz, el mismo desde
// donde publican los services).
@Component
public class SessionExpiringListener {

    private final SessionRegistry sessionRegistry;

    @Autowired
    public SessionExpiringListener(final SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAccessRevoked(final UserAccessRevokedEvent event) {
        expireSessionsOf(event.getUserId());
    }

    // Se busca por el id del principal y no por equals(): el equals del User de Spring Security
    // compara solo el username. Las comparaciones de ids son entre long primitivos.
    private void expireSessionsOf(final long userId) {
        for (final Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof PlancitoUserDetails && ((PlancitoUserDetails) principal).getId() == userId) {
                for (final SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    session.expireNow();
                }
            }
        }
    }
}
