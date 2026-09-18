// Resultado de responder una invitacion de lista de espera.
package ar.edu.itba.paw.services;

public enum InvitationResult {
    /** Aceptada a tiempo: la persona pasa a tener su lugar confirmado. */
    ACCEPTED,
    /** Rechazada: sale de la lista y el cupo se le ofrece al siguiente. */
    DECLINED,
    /** Ese token no existe (link mal copiado, o invitacion ya borrada). */
    NOT_FOUND,
    /** Ya la habia aceptado antes. Apretar dos veces no rompe nada. */
    ALREADY_USED,
    /** Se le paso la hora: queda afuera y el cupo vuelve a la lista. */
    EXPIRED,
    /** El evento ya sucedio, no hay nada que aceptar. */
    EVENT_PASSED
}
