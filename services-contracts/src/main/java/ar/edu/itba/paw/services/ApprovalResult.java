// Resultado de que el creador acepte una inscripcion pendiente.
package ar.edu.itba.paw.services;

public enum ApprovalResult {
    /** La inscripcion paso a confirmada y se le aviso a la persona. */
    APPROVED,
    /**
     * El evento ya no tiene lugar (confirmados + invitaciones llegaron al cupo):
     * no se acepta a nadie mas. La inscripcion no se toca.
     */
    FULL,
    /** No existe, no esta pendiente, o el evento no es de quien pide aceptar. */
    NOT_FOUND
}
