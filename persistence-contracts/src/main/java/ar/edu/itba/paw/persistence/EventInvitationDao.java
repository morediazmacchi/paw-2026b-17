// Contrato de persistencia para EventInvitation. Implementacion:
// EventInvitationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;

import java.util.List;
import java.util.Optional;

public interface EventInvitationDao {

    EventInvitation create(EventInvitation invitation);

    /** Para no mandarle una invitacion nueva (y un mail nuevo) a quien ya esta invitado. */
    Optional<EventInvitation> findByEventAndEmail(long eventId, String email);

    // Solo si existe (un COUNT), sin cargar la invitacion: es lo que necesita EventAccessService.canView.
    boolean existsByEventAndUserId(long eventId, long userId);

    // Pantalla "a quien invite" del creador, mas recientes primero, de a una pagina (paginas desde 1);
    // countByEvent() da el total para el paginador.
    List<EventInvitation> findByEvent(long eventId, int page, int pageSize);

    long countByEvent(long eventId);

    // "Mis invitaciones" de quien las RECIBE (no del creador): los EVENTOS a los que lo invitaron,
    // con un JOIN (una sola consulta), del que invitaron mas recientemente al mas viejo, de a una
    // pagina (page empieza en 1). Por user_id, no por email: solo tiene sentido para alguien ya
    // logueado, y el id nunca queda desactualizado como el email podria.
    List<Event> findInvitedEventsByUserId(long userId, int page, int pageSize);

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
