// Contrato de las preguntas y respuestas de un evento. Implementacion: EventQuestionServiceImpl.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.User;

public interface EventQuestionService {

    /** Pregunta de "asker" sobre el evento. Si sale bien, le avisa al organizador (mail + bandeja). */
    QuestionResult ask(long eventId, User asker, String text);

    /** Respuesta del organizador. Si sale bien, le avisa a quien pregunto (mail + bandeja). */
    AnswerResult answer(long eventId, long questionId, String answererEmail, String text);

    /** Las preguntas del evento, mas nuevas primero. */
    PageResult<EventQuestion> findPage(long eventId, int page);
}
