// Resultado de intentar hacer una pregunta (EventController.ask()).
package ar.edu.itba.paw.services;

public enum QuestionResult {
    CREATED,

    // El organizador no se pregunta a si mismo.
    OWN_EVENT,

    // El evento ya paso o se cancelo: no recibe preguntas nuevas.
    CLOSED,

    // El evento no existe. Desde la UI no se llega.
    NOT_ALLOWED
}
