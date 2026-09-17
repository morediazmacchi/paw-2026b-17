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

    // "Mis inscripciones": mas recientes primero.
    List<Registration> findByEmail(String email);

    void delete(long id);
}
