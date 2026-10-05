// Resultado de que el creador acepte una inscripcion pendiente.
package ar.edu.itba.paw.services;

public class ApprovalResult {

    public enum Status {
        /** La inscripcion paso a confirmada y se le aviso a la persona. */
        APPROVED,
        /**
         * El evento ya no tiene lugar (confirmados + invitaciones llegaron al cupo):
         * no se acepta a nadie mas. La inscripcion no se toca.
         */
        FULL,
        /** No existe, no esta pendiente, el evento esta cancelado o ya paso, o no es de quien pide aceptar. */
        NOT_FOUND
    }

    private final Status status;
    // true solo si ESTA llamada a approve() disparo notificationService.notifyEventFull()
    // (esta aceptacion ocupo el ultimo lugar). Mismo criterio que
    // RegistrationResult.isNotificationSent(): confirma que se DISPARO el aviso
    // (notifyEventFull es @Async, fire-and-forget), no que el mail ya salio. Existe
    // para poder testear "se avisa en el momento justo" sin verify().
    private final boolean notificationSent;

    public ApprovalResult(final Status status, final boolean notificationSent) {
        this.status = status;
        this.notificationSent = notificationSent;
    }

    public static ApprovalResult of(final Status status) {
        return new ApprovalResult(status, false);
    }

    public Status getStatus() { return status; }
    public boolean isNotificationSent() { return notificationSent; }
}
