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

    // "Ver inscriptos" del organizador: todos los de un evento, en orden de
    // llegada (mas antiguos primero) -- al reves que findByEmail a proposito,
    // aca lo que importa es quien se anoto primero, no lo mas reciente.
    List<Registration> findByEventId(long eventId);

    void delete(long id);
}
