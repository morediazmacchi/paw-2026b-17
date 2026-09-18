// Contrato de persistencia para WaitlistInvitation. Implementacion:
// WaitlistInvitationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.WaitlistInvitation;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface WaitlistInvitationDao {

    WaitlistInvitation create(WaitlistInvitation invitation);

    /** Por el token del link del mail: es la unica forma de identificar a quien entra. */
    Optional<WaitlistInvitation> findByToken(String token);

    /**
     * Cuantos cupos de este evento estan RESERVADOS: invitaciones mandadas que
     * todavia nadie contesto. No los ocupa nadie, pero tampoco estan libres.
     */
    long countPending(long eventId);

    /** Invitaciones de este evento que siguen pendientes y ya se les paso la hora. */
    List<WaitlistInvitation> findExpired(long eventId, Date now);

    void markAccepted(long id);
}
