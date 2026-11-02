// Resultado de intentar hacer una pregunta (EventController.ask()).
package ar.edu.itba.paw.services;

public enum QuestionResult {
    CREATED,

    // El organizador no se pregunta a si mismo.
    OWN_EVENT,

    // El evento ya paso o se cancelo: no recibe preguntas nuevas.
    CLOSED
    // (Un evento que no existe no es un resultado: ask() lanza EventNotFoundException, que ErrorAdvice
    // mapea a 404. Quien puede ver el evento y preguntar lo decide @PreAuthorize(canView) en el controller.)
}
