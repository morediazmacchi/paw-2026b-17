// Resultado de intentar responder una pregunta (EventController.answer()).
package ar.edu.itba.paw.services;

public enum AnswerResult {
    ANSWERED,

    // Ya tenia respuesta (doble click, dos pestañas): no se pisa.
    ALREADY_ANSWERED,

    // No existe, no es de este evento, o quien responde no es el organizador.
    // Desde la UI no se llega (el form solo le aparece al organizador).
    NOT_ALLOWED
}
