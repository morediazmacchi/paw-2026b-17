// Resultado de invitar a alguien a un evento privado.
package ar.edu.itba.paw.services;

public enum EventInvitationResult {
    /** Se creo la invitacion y se mando el mail. */
    SENT,
    /** Ese mail ya estaba invitado a este evento. No se mando nada de nuevo. */
    ALREADY_INVITED,
    /** El creador puso su propio mail: no tiene sentido invitarse a si mismo. */
    SELF_INVITE,
    /**
     * El evento ya no tiene lugar: confirmados + invitaciones sin usar llegaron
     * al cupo, y cada invitacion reserva un lugar.
     */
    FULL,
    /**
     * El evento es publico: invitar solo tiene sentido en uno privado (un evento publico ya tiene un
     * link que se puede compartir). Es una regla de negocio, no de acceso: la pantalla lo muestra
     * como cartel.
     */
    EVENT_PUBLIC,
    /** El evento no existe, esta cancelado o ya paso. (Quien invita lo decide @PreAuthorize.) */
    NOT_ALLOWED
}
