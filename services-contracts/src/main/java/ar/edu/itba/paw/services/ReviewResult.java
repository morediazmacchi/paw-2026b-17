// Resultado de intentar dejar una reseña (ProfileController.leaveReview()).
package ar.edu.itba.paw.services;

public enum ReviewResult {
    CREATED,

    // Ya habia reseñado este evento: una reseña no se edita ni se vuelve a mandar.
    ALREADY_REVIEWED,

    // No le corresponde reseñar: el evento no existe, todavia no termino, esta
    // cancelado, o no tuvo su lugar confirmado. Desde la UI no se llega (el
    // boton no aparece), solo con un POST armado a mano: no hace falta un
    // mensaje distinto para cada caso.
    NOT_ELIGIBLE
}
