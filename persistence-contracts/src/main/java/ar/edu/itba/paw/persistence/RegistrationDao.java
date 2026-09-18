// Contrato de persistencia para Registration. Implementacion: RegistrationJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;

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

    /** Para pasar de 'waitlisted' a 'confirmed' cuando alguien acepta su invitacion. */
    void updateStatus(long id, String status);

    // "Mis inscripciones": mas recientes primero.
    List<Registration> findByEmail(String email);

    void delete(long id);
}
