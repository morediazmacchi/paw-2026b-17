// Contrato de las preguntas y respuestas de un evento. Implementacion: EventQuestionServiceImpl.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.User;

import java.util.Optional;

public interface EventQuestionService {

    /** Pregunta de "asker" sobre el evento. Si sale bien, le avisa al organizador (mail + bandeja). */
    QuestionResult ask(long eventId, User asker, String text);

    /**
     * Respuesta del organizador. Si sale bien, le avisa a quien pregunto (mail + bandeja). El evento
     * es el de la pregunta. Que quien responde sea el organizador de ese evento NO se valida aca: lo
     * decide @PreAuthorize en el controller (ver EventSecurity.canAnswerQuestion).
     */
    AnswerResult answer(long questionId, String text);

    /**
     * Id del organizador del evento al que pertenece la pregunta, para decidir quien puede responderla
     * (ver EventSecurity.canAnswerQuestion). Lectura acotada: no carga la pregunta ni el evento.
     */
    Optional<Long> findEventCreatorId(long questionId);

    /** Las preguntas del evento, mas nuevas primero. */
    PageResult<EventQuestion> findPage(long eventId, int page);

    /**
     * Moderacion (ver AdminController): un admin elimina la pregunta (con su
     * respuesta si tenia). No hay validacion de ownership -- es una accion de
     * admin, no del organizador -- y no se puede deshacer.
     */
    boolean deleteByAdmin(long questionId);
}
