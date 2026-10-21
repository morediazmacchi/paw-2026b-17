// Contrato de persistencia para Registration. Implementacion: RegistrationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface RegistrationDao {
    Registration create(Registration registration);
    Optional<Registration> findByEventAndUserId(long eventId, long userId);
    long countConfirmed(long eventId);

    Optional<Registration> findById(long id);

    /** Los proximos de la lista de espera de este evento, del que llego primero al ultimo. */
    List<Registration> findNextWaitlisted(long eventId, int limit);

    /** Cambia el estado: PENDING a CONFIRMED/REJECTED/WAITLISTED, WAITLISTED a CONFIRMED,
     *  CONFIRMED a REJECTED (el creador revoca). */
    boolean updateStatus(long id, RegistrationStatus status);

    // "Mis inscripciones", de a una pagina (page empieza en 1). Primero las de
    // eventos que todavia no pasaron (el mas proximo primero) y despues las de
    // eventos pasados (el mas reciente primero); el orden y el corte se hacen en SQL.
    List<Registration> findByUserId(long userId, int page, int pageSize);

    /** Para el badge de "Mis inscripciones" (ver ProfileController): sin traer las filas. */
    long countByUserId(long userId);

    // "Ver inscriptos" del organizador: todos los de un evento, en orden de
    // llegada (mas antiguos primero) -- al reves que findByEmail a proposito,
    // aca lo que importa es quien se anoto primero, no lo mas reciente.
    List<Registration> findByEventId(long eventId);

    boolean delete(long id);

    // Los eventos a los que esta persona "asistio": inscripcion CONFIRMED, fecha anterior a "now"
    // y evento no cancelado (no hay un campo de asistencia: se deriva de eso). Solo los ids, del
    // mas reciente al mas viejo, como mucho "limit".
    List<Long> findAttendedEventIds(long userId, Date now, int limit);

    // La inscripcion de esta persona (si tiene) en cada uno de esos eventos, en una sola consulta.
    List<Registration> findByUserIdAndEventIds(long userId, Collection<Long> eventIds);

    /** Cuantas inscripciones de este evento estan esperando que el creador las revise. */
    long countPending(long eventId);

    // Pantalla de "aceptar o rechazar inscriptos": las pendientes de este evento,
    // de la mas vieja a la mas nueva (a quien espera hace mas tiempo se lo atiende primero).
    List<Registration> findPendingByEvent(long eventId);
}
