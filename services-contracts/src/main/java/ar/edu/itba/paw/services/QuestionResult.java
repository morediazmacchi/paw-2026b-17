// Resultado de intentar hacer una pregunta (EventQuestionController.ask()).
package ar.edu.itba.paw.services;

public enum QuestionResult {
    CREATED,

    // El evento ya paso o se cancelo: no recibe preguntas nuevas.
    CLOSED,
    // El texto esta vacio o supera EventQuestionService.MAX_QUESTION_LENGTH. El form ya lo valida para dar
    // feedback; esto es la defensa del service.
    INVALID
    // (Un evento que no existe no es un resultado: ask() lanza EventNotFoundException, que ErrorAdvice
    // mapea a 404. Quien puede preguntar (ver el evento sin ser su organizador) lo decide @PreAuthorize en el
    // controller.)
}
