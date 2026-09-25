// Contrato de persistencia para Registration. Implementacion: RegistrationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.List;
import java.util.Optional;

public interface RegistrationDao {
    Registration create(Registration registration);
    Optional<Registration> findByEventAndEmail(long eventId, String email);
    long countConfirmed(long eventId);

    Optional<Registration> findById(long id);

    /**
     * Los proximos de la lista de espera de este evento a los que todavia NO se
     * les ofrecio el lugar, del que llego primero al que llego ultimo.
     */
    List<Registration> findNextWaitlisted(long eventId, int limit);

    /** Para pasar de WAITLISTED a CONFIRMED cuando alguien acepta su invitacion
     *  (o de PENDING a CONFIRMED/REJECTED cuando el creador decide). */
    void updateStatus(long id, RegistrationStatus status);

    /**
     * Ids de los eventos futuros, con cupo limitado, que tienen gente en lista de
     * espera. Es lo unico que necesita mirar la tarea periodica: si no hay nadie
     * esperando, no hay nada que reconciliar.
     */
    List<Long> findEventIdsWithWaitlist(java.util.Date now);

    // "Mis inscripciones": mas recientes primero.
    List<Registration> findByEmail(String email);

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
