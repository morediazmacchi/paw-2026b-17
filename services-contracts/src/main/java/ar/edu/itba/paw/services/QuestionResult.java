// Resultado de intentar hacer una pregunta (EventController.ask()).
package ar.edu.itba.paw.services;

public enum QuestionResult {
    CREATED,

    // El organizador no se pregunta a si mismo.
    OWN_EVENT,

    // El evento ya paso o se cancelo: no recibe preguntas nuevas.
    CLOSED,

    // El evento no existe, o existe pero quien pregunta no puede verlo (mismo
    // resultado en los dos casos: no se revela cual de las dos paso).
    // Desde la UI no se llega: EventQuestionController.ask() ya lo chequea antes.
    NOT_ALLOWED
}
