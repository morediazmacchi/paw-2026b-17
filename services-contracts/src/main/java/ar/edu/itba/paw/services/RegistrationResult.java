// Resultado de intentar registrar a alguien a un evento.
package ar.edu.itba.paw.services;

public enum RegistrationResult {
    /** Habia lugar: la persona quedo adentro. */
    CONFIRMED,
    /** El evento estaba lleno: la persona quedo en la lista de espera. */
    WAITLISTED,
    /** Ya tenia su lugar confirmado. No se creo nada. */
    ALREADY_REGISTERED,
    /** Ya estaba en la lista de espera. No se creo nada. */
    ALREADY_WAITLISTED
}
