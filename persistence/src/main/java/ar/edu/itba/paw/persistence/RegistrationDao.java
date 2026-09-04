package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;

import java.util.Optional;

public interface RegistrationDao {
    Registration create(Registration registration);
    Optional<Registration> findByEventAndEmail(long eventId, String email);
    long countConfirmed(long eventId);
}
