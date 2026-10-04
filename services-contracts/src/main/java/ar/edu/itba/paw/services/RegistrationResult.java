// Resultado de intentar registrar a alguien a un evento.
package ar.edu.itba.paw.services;

public class RegistrationResult {

    public enum Status {
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
        REJECTED,
        /** Quien pide registrarse es el creador del evento: no se lo puede, no se creo nada. */
        OWN_EVENT,
        /** El creador cancelo el evento: no se puede anotar nadie, no se creo nada. */
        CANCELLED,
        /** El evento ya paso: no se puede anotar nadie, no se creo nada. */
        FINISHED
    }

    private final Status status;
    // true solo si ESTA llamada a register() disparo notificationService.notifyEventFull()
    // (el cupo se completo justo con esta inscripcion). "Enviado" es un poco
    // generoso: notifyEventFull es @Async (fire-and-forget), asi que esto
    // confirma que se DISPARO el aviso, no que el mail ya salio de verdad -- eso
    // no se puede saber de forma sincronica. Existe para poder testear "se avisa
    // en el momento justo" sin verify() sobre el mock de NotificationService.
    private final boolean notificationSent;

    public RegistrationResult(final Status status, final boolean notificationSent) {
        this.status = status;
        this.notificationSent = notificationSent;
    }

    public static RegistrationResult of(final Status status) {
        return new RegistrationResult(status, false);
    }

    public Status getStatus() { return status; }
    public boolean isNotificationSent() { return notificationSent; }
}
