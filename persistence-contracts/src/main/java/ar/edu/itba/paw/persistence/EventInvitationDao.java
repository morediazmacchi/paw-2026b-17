// Contrato de persistencia para EventInvitation. Implementacion:
// EventInvitationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;

import java.util.List;
import java.util.Optional;

public interface EventInvitationDao {

    EventInvitation create(EventInvitation invitation);

    /** Por el token del link del mail: es la unica forma de identificar a quien entra. */
    Optional<EventInvitation> findByToken(String token);

    /** Para no mandarle una invitacion nueva (y un mail nuevo) a quien ya esta invitado. */
    Optional<EventInvitation> findByEventAndEmail(long eventId, String email);

    // Pantalla "a quien invite" del creador, mas recientes primero.
    List<EventInvitation> findByEvent(long eventId);
}
