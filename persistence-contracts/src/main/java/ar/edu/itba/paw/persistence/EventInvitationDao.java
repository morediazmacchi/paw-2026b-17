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
     * Igual que findByEventAndEmail, pero para cuando quien pregunta ya esta
     * logueado: busca por user_id en vez de por email. A diferencia del email
     * de la invitacion (que puede haber quedado desactualizado si la persona
     * cambio el suyo despues), el id no cambia nunca.
     */
    Optional<EventInvitation> findByEventAndUserId(long eventId, long userId);

    /**
     * Invitaciones de este evento cuya persona todavia no se anoto (no tiene
     * ninguna inscripcion, del estado que sea): cada una tiene un lugar reservado
     * y cuenta como cupo ocupado. Cuando la persona se anota, el lugar pasa a ser
     * su inscripcion y la invitacion deja de contarse (no se cuenta dos veces).
     */
    long countOutstanding(long eventId);

    // Pantalla "a quien invite" del creador, mas recientes primero.
    List<EventInvitation> findByEvent(long eventId);

    // "Mis invitaciones" de quien las RECIBE (no del creador), mas recientes
    // primero. Por user_id, no por email: solo tiene sentido para alguien ya
    // logueado, y el id nunca queda desactualizado como el email podria.
    List<EventInvitation> findByUserId(long userId);

    /** Para el badge de "Mis invitaciones" (ver ProfileController): sin traer las filas. */
    long countByUserId(long userId);

    /**
     * Reconciliacion (ver UserServiceImpl.register()): alguien invitado sin
     * cuenta todavia acaba de crearse una. Completa el user_id de todas sus
     * invitaciones que quedaron sin resolver (user_id NULL), sin tocar las que
     * ya lo tenian.
     */
    int linkUserId(String email, long userId);
}
