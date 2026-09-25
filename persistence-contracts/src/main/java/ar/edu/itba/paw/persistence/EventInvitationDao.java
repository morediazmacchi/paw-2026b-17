// Contrato de persistencia para EventInvitation. Implementacion:
// EventInvitationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;

import java.util.List;
import java.util.Optional;

public interface EventInvitationDao {

    EventInvitation create(EventInvitation invitation);

    /** Para no mandarle una invitacion nueva (y un mail nuevo) a quien ya esta invitado. */
    Optional<EventInvitation> findByEventAndEmail(long eventId, String email);

    /**
     * Invitaciones de este evento cuya persona todavia no se anoto (no tiene
     * ninguna inscripcion, del estado que sea): cada una tiene un lugar reservado
     * y cuenta como cupo ocupado. Cuando la persona se anota, el lugar pasa a ser
     * su inscripcion y la invitacion deja de contarse (no se cuenta dos veces).
     */
    long countOutstanding(long eventId);

    /** Si alguien que fue invitado se da de baja, su invitacion no sigue reteniendo el lugar. */
    void deleteByEventAndEmail(long eventId, String email);

    // Pantalla "a quien invite" del creador, mas recientes primero.
    List<EventInvitation> findByEvent(long eventId);
}
