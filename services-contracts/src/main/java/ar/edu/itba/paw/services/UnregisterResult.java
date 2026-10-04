// Resultado de intentar cancelar una inscripcion propia (RegistrationService.cancel()).
package ar.edu.itba.paw.services;

public enum UnregisterResult {
    // Era CONFIRMED: se le aviso al organizador (y si correspondia, al primero
    // de la lista de espera que ocupo el lugar).
    CANCELLED,

    // Era WAITLISTED: no ocupaba cupo, al organizador no le cambia nada.
    CANCELLED_WAITLISTED,

    // Era PENDING: el organizador tenia ese pedido para responder y ya no esta.
    CANCELLED_PENDING,

    // Estado terminal: un rechazado no puede darse de baja (la fila es
    // justamente lo que le impide volver a anotarse).
    REJECTED,

    // No existe, o existe pero es de otro usuario.
    NOT_FOUND,

    // El evento ya termino: no tiene sentido "darse de baja" de algo que ya paso.
    FINISHED
}
