// Contrato de persistencia para Registration. Implementacion: RegistrationJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RegistrationDao {
    // Anota a la persona en el evento: el DAO arma la fila, el service solo dice quien, donde y con que estado.
    // email y name son los de la cuenta, copiados a la inscripcion.
    Registration create(long eventId, long userId, String email, String name, RegistrationStatus status);
    Optional<Registration> findByEventAndUserId(long eventId, long userId);

    Optional<Registration> findById(long id);

    // Lecturas acotadas para autorizar (ver EventSecurity): de quien es, de que evento y en que estado
    // (3 columnas), sin cargar la inscripcion. Vacias si no existe.
    Optional<RegistrationAccess> findAccessById(long id);

    Optional<RegistrationAccess> findAccessByEventAndUserId(long eventId, long userId);

    // Los ids (solo los ids: una columna, sin cargar las inscripciones) de los proximos de la lista de espera de
    // este evento, del que llego primero al ultimo, hasta "limit". Es lo minimo para decidir a quien le cambia el
    // estado (ver RegistrationServiceImpl.promoteFromWaitlist); a quien avisarle y como se busca despues, de a
    // tandas y en segundo plano (ver findByIds y MassNotifier).
    List<Long> findNextWaitlistedIds(long eventId, int limit);

    /** Cambia el estado: PENDING a CONFIRMED/REJECTED/WAITLISTED, WAITLISTED a CONFIRMED,
     *  CONFIRMED a REJECTED (el creador revoca). */
    boolean updateStatus(long id, RegistrationStatus status);

    /**
     * Le pone ese estado a las inscripciones de esos ids en UNA sola sentencia (UPDATE ... WHERE id IN), en vez de un
     * UPDATE por inscripcion al hacer commit (dirty checking de updateStatus(id, ...)). Para los procesos que mueven
     * a muchas de golpe (promover la lista de espera, pasar los pendientes a lista de espera) mientras el evento
     * esta bloqueado. Recibe ids y no entidades: quien llama no necesita cargarlas. La lista no puede estar vacia.
     * Devuelve cuantas filas cambio.
     */
    int updateStatusOfAll(Collection<Long> registrationIds, RegistrationStatus status);

    // Las inscripciones de esos ids, ordenadas por id. Para los avisos masivos en segundo plano (ver MassNotifier):
    // reciben la lista de ids que cambiaron de estado y las cargan de a una tanda por vez, nunca todas juntas.
    List<Registration> findByIds(Collection<Long> ids);

    // "Mis inscripciones", de a una pagina (page empieza en 1), en dos listas separadas por la fecha del
    // evento respecto de "now": las de eventos que todavia no pasaron (fecha >= now, la mas proxima
    // primero) y las de eventos pasados (fecha < now, la mas reciente primero). La division, el orden
    // y el corte se hacen en SQL; el desempate es el id de la inscripcion.
    List<Registration> findUpcomingByUserId(long userId, Date now, int page, int pageSize);

    long countUpcomingByUserId(long userId, Date now);

    List<Registration> findPastByUserId(long userId, Date now, int page, int pageSize);

    long countPastByUserId(long userId, Date now);

    /** Para el badge de "Mis inscripciones" (ver ProfileController): sin traer las filas. */
    long countByUserId(long userId);

    // Una tanda de los que tienen el lugar confirmado (RegistrationStatus.withConfirmedPlace()) en un evento: los
    // de id mayor a "afterId", de menor a mayor id, hasta "limit". A quienes se les manda el mail de "cambio el
    // evento", "se cancelo" y el recordatorio de 24 hs. Se recorre de a tandas (la siguiente arranca despues del
    // ultimo id de la anterior), en segundo plano y despues del commit (ver MassNotifier): nunca todas de golpe.
    List<Registration> findConfirmedByEventIdAfter(long eventId, long afterId, int limit);

    // "Ver inscriptos" del organizador, de a una pagina (un evento sin tope de cupos puede tener muchisimas):
    // todos los de un evento en orden de llegada (mas antiguos primero; el id desempata), paginas desde 1, y
    // countByEventId() da el total para el paginador.
    List<Registration> findByEventId(long eventId, int page, int pageSize);

    long countByEventId(long eventId);

    boolean delete(long id);

    // La inscripcion de esta persona (si tiene) en cada uno de esos eventos, en una sola consulta.
    List<Registration> findByUserIdAndEventIds(long userId, Collection<Long> eventIds);

    /** Cuantas inscripciones de este evento estan esperando que el creador las revise. */
    long countPending(long eventId);

    // Cuantas inscripciones con ese estado tiene cada uno de esos eventos, en UNA consulta
    // (GROUP BY event_id). Un evento sin ninguna no aparece en el mapa.
    Map<Long, Long> countByEventsAndStatus(Collection<Long> eventIds, RegistrationStatus status);

    // Los ids (solo los ids, sin cargar las inscripciones) de las pendientes de este evento, de la mas vieja a la
    // mas nueva. Es lo minimo para pasarlas a lista de espera cuando el evento se llena (ver
    // RegistrationServiceImpl.moveRemainingPendingToWaitlist); el aviso a esas personas se arma despues, de a
    // tandas y en segundo plano (ver findByIds y MassNotifier).
    List<Long> findPendingIdsByEvent(long eventId);

    // Pantalla de "aceptar o rechazar inscriptos": las pendientes de este evento de a una pagina, de la mas vieja a
    // la mas nueva (a quien espera hace mas tiempo se lo atiende primero; el id desempata). Paginas desde 1 y
    // countPending() da el total para el paginador.
    List<Registration> findPendingByEvent(long eventId, int page, int pageSize);
}
