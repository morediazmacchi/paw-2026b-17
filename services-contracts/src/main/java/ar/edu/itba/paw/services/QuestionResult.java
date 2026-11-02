// Resultado de intentar hacer una pregunta (EventQuestionController.ask()).
package ar.edu.itba.paw.services;

public enum QuestionResult {
    CREATED,

    // El organizador no se pregunta a si mismo.
    OWN_EVENT,

    // El evento ya paso o se cancelo: no recibe preguntas nuevas.
    CLOSED,
    // El texto esta vacio o supera EventQuestionService.MAX_QUESTION_LENGTH. El form ya lo valida para dar
    // feedback; esto es la defensa del service.
    INVALID
    // (Un evento que no existe no es un resultado: ask() lanza EventNotFoundException, que ErrorAdvice
    // mapea a 404. Quien puede ver el evento y preguntar lo decide @PreAuthorize(canView) en el controller.)
}
