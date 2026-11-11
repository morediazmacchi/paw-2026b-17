// Contrato de persistencia para Registration. Implementacion: RegistrationJdbcDao (modulo persistence).
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
    Registration create(Registration registration);
    Optional<Registration> findByEventAndUserId(long eventId, long userId);
    long countConfirmed(long eventId);

    Optional<Registration> findById(long id);

    // Lecturas acotadas para autorizar (ver EventSecurity): de quien es, de que evento y en que estado
    // (3 columnas), sin cargar la inscripcion. Vacias si no existe.
    Optional<RegistrationAccess> findAccessById(long id);

    Optional<RegistrationAccess> findAccessByEventAndUserId(long eventId, long userId);

    /** Los proximos de la lista de espera de este evento, del que llego primero al ultimo. */
    List<Registration> findNextWaitlisted(long eventId, int limit);

    /** Cambia el estado: PENDING a CONFIRMED/REJECTED/WAITLISTED, WAITLISTED a CONFIRMED,
     *  CONFIRMED a REJECTED (el creador revoca). */
    boolean updateStatus(long id, RegistrationStatus status);

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

    // "Ver inscriptos" del organizador: todos los de un evento, en orden de
    // llegada (mas antiguos primero) -- al reves que findByEmail a proposito,
    // aca lo que importa es quien se anoto primero, no lo mas reciente.
    List<Registration> findByEventId(long eventId);

    // Lo mismo de a una pagina, para las pantallas "Ver inscriptos" (un evento sin tope de cupos puede tener
    // muchisimas): paginas desde 1, mismo orden (el id desempata) y countByEventId() da el total para el paginador.
    // findByEventId() de arriba queda para quien necesita recorrer a todos (avisos, recordatorios).
    List<Registration> findByEventId(long eventId, int page, int pageSize);

    long countByEventId(long eventId);

    boolean delete(long id);

    // Los eventos a los que esta persona "asistio": inscripcion CONFIRMED, fecha anterior a "now"
    // y evento no cancelado (no hay un campo de asistencia: se deriva de eso). Solo los ids, del
    // mas reciente al mas viejo, como mucho "limit".
    List<Long> findAttendedEventIds(long userId, Date now, int limit);

    // La inscripcion de esta persona (si tiene) en cada uno de esos eventos, en una sola consulta.
    List<Registration> findByUserIdAndEventIds(long userId, Collection<Long> eventIds);

    /** Cuantas inscripciones de este evento estan esperando que el creador las revise. */
    long countPending(long eventId);

    // Cuantas inscripciones con ese estado tiene cada uno de esos eventos, en UNA consulta
    // (GROUP BY event_id). Un evento sin ninguna no aparece en el mapa.
    Map<Long, Long> countByEventsAndStatus(Collection<Long> eventIds, RegistrationStatus status);

    // Pantalla de "aceptar o rechazar inscriptos": las pendientes de este evento,
    // de la mas vieja a la mas nueva (a quien espera hace mas tiempo se lo atiende primero).
    List<Registration> findPendingByEvent(long eventId);

    // Lo mismo de a una pagina (un evento con aprobacion y sin tope de cupos puede juntar muchisimos pedidos):
    // paginas desde 1, mismo orden (el id desempata) y countPending() da el total para el paginador.
    // findPendingByEvent() de arriba queda para quien necesita recorrerlas todas (ver
    // RegistrationServiceImpl.moveRemainingPendingToWaitlist).
    List<Registration> findPendingByEvent(long eventId, int page, int pageSize);
}
