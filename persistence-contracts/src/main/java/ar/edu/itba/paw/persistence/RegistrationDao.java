// Contrato de persistencia para Registration. Implementacion: RegistrationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.List;
import java.util.Optional;

public interface RegistrationDao {
    Registration create(Registration registration);
    Optional<Registration> findByEventAndUserId(long eventId, long userId);
    long countConfirmed(long eventId);

    Optional<Registration> findById(long id);

    /** Los proximos de la lista de espera de este evento, del que llego primero al ultimo. */
    List<Registration> findNextWaitlisted(long eventId, int limit);

    /** Toda la lista de espera de un evento, en orden de llegada. */
    List<Registration> findWaitlistedByEvent(long eventId);

    /** Cambia el estado: PENDING a CONFIRMED/REJECTED/WAITLISTED, WAITLISTED a CONFIRMED,
     *  CONFIRMED a REJECTED (el creador revoca). */
    void updateStatus(long id, RegistrationStatus status);

    // "Mis inscripciones": mas recientes primero.
    List<Registration> findByUserId(long userId);

    // "Ver inscriptos" del organizador: todos los de un evento, en orden de
    // llegada (mas antiguos primero) -- al reves que findByEmail a proposito,
    // aca lo que importa es quien se anoto primero, no lo mas reciente.
    List<Registration> findByEventId(long eventId);

    void delete(long id);

    /** Cuantas inscripciones de este evento estan esperando que el creador las revise. */
    long countPending(long eventId);

    // Pantalla de "aceptar o rechazar inscriptos": las pendientes de este evento,
    // de la mas vieja a la mas nueva (a quien espera hace mas tiempo se lo atiende primero).
    List<Registration> findPendingByEvent(long eventId);
}
