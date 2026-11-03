// La relacion de una persona con un evento, ya resuelta para el detalle: si esta inscripta (y en que estado), si
// es la duenia, y si puede anotarse (la regla de visibilidad sin la excepcion de admin, ver
// EventAccessService.canRegister()). Lo arma EventDetailServiceImpl; el estado de accion resultante va en
// EventDetail.getAction().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.RegistrationStatus;

public class EventParticipation {

    // null si no tiene inscripcion.
    private final RegistrationStatus registrationStatus;
    private final boolean owner;
    private final boolean canRegister;

    public EventParticipation(final RegistrationStatus registrationStatus, final boolean owner,
                              final boolean canRegister) {
        this.registrationStatus = registrationStatus;
        this.owner = owner;
        this.canRegister = canRegister;
    }

    // Una inscripcion rechazada cuenta como "no anotada": la fila existe, pero no tiene lugar.
    public boolean isRegistered() {
        return registrationStatus != null && registrationStatus != RegistrationStatus.REJECTED;
    }

    public boolean isWaitlisted() {
        return registrationStatus == RegistrationStatus.WAITLISTED;
    }

    public boolean isPendingApproval() {
        return registrationStatus == RegistrationStatus.PENDING;
    }

    public boolean isRejected() {
        return registrationStatus == RegistrationStatus.REJECTED;
    }

    // Confirmada: se le ofrece unirse al grupo de chat.
    public boolean isChatConfirmed() {
        return registrationStatus != null && registrationStatus.isConfirmed();
    }

    // El creador no se anota a su propio evento.
    public boolean isOwner() {
        return owner;
    }

    // Puede anotarse: es la duenia, o el evento esta aprobado y es publico, o es privado y tiene inscripcion o
    // invitacion. Un admin puede VER un evento pendiente/rechazado o privado para moderarlo, pero no anotarse.
    public boolean canRegister() {
        return canRegister;
    }
}
