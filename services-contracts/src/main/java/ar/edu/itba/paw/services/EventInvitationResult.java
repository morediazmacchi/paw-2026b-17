// Resultado de invitar a alguien a un evento privado.
package ar.edu.itba.paw.services;

public enum EventInvitationResult {
    /** Se creo la invitacion y se mando el mail. */
    SENT,
    /** Ese mail ya estaba invitado a este evento. No se mando nada de nuevo. */
    ALREADY_INVITED,
    /**
     * El evento ya no tiene lugar: confirmados + invitaciones sin usar llegaron
     * al cupo, y cada invitacion reserva un lugar.
     */
    FULL,
    /** El evento no existe, no es de quien pide invitar, o no es privado. */
    NOT_ALLOWED
}
