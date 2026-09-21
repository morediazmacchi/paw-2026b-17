// Resultado de intentar registrar a alguien a un evento.
package ar.edu.itba.paw.services;

public enum RegistrationResult {
    /** Habia lugar: la persona quedo adentro. */
    CONFIRMED,
    /** El evento estaba lleno: la persona quedo en la lista de espera. */
    WAITLISTED,
    /** El evento requiere confirmacion del creador: la persona queda esperando que la revise. */
    PENDING,
    /** Ya tenia su lugar confirmado. No se creo nada. */
    ALREADY_REGISTERED,
    /** Ya estaba en la lista de espera. No se creo nada. */
    ALREADY_WAITLISTED,
    /** Ya tenia una inscripcion esperando revision. No se creo nada. */
    ALREADY_PENDING,
    /** El creador ya le habia rechazado una inscripcion a este evento. No se creo nada. */
    REJECTED
}
