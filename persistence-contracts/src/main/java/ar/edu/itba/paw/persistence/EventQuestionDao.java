// Contrato de persistencia para EventQuestion. Implementacion: EventQuestionJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventQuestion;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface EventQuestionDao {
    EventQuestion create(EventQuestion question);

    Optional<EventQuestion> findById(long id);

    // Mas nuevas primero, paginadas.
    List<EventQuestion> findByEvent(long eventId, int page, int pageSize);

    long countByEvent(long eventId);

    // true solo si ESTA llamada la respondio: si ya tenia respuesta devuelve
    // false y no la pisa (mismo truco que markReminderSent).
    boolean answer(long id, String answer, Date when);

    // Moderacion (ver EventQuestionService.deleteByAdmin()): borrado duro, con
    // su respuesta si tenia (no hay soft-delete para preguntas, a diferencia
    // de event/registration). No se puede deshacer.
    boolean delete(long id);
}
