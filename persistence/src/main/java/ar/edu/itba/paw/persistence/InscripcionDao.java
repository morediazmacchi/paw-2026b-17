package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Inscripcion;

import java.util.Optional;

public interface InscripcionDao {
    Inscripcion create(Inscripcion inscripcion);
    Optional<Inscripcion> findByEventoAndEmail(long eventoId, String email);
    long countConfirmadas(long eventoId);
}
