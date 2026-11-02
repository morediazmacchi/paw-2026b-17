// Resultado de intentar responder una pregunta (EventQuestionController.answer()).
package ar.edu.itba.paw.services;

public enum AnswerResult {
    ANSWERED,

    // Ya tenia respuesta (doble click, dos pestañas): no se pisa.
    ALREADY_ANSWERED,

    // La pregunta ya no existe (un admin la eliminó justo en el medio). Quién puede responder
    // lo decide @PreAuthorize en el controller, no es un resultado.
    NOT_FOUND
}
