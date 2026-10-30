// La relacion de una persona con un evento, ya resuelta para el detalle: si esta inscripta (y en
// que estado), si es la dueña, y si la pantalla le tiene que ofrecer inscribirse o solo mostrarle
// el evento. Lo devuelve RegistrationService.findParticipation(); las reglas viven en el service,
// junto a register(), para que el detalle no repita en el controller lo que register() decide.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.RegistrationStatus;

public class EventParticipation {

    // null si no tiene inscripcion.
    private final RegistrationStatus registrationStatus;
    private final boolean owner;
    private final boolean adminReviewOnly;
    private final boolean viewOnly;

    public EventParticipation(final RegistrationStatus registrationStatus, final boolean owner,
                              final boolean adminReviewOnly, final boolean viewOnly) {
        this.registrationStatus = registrationStatus;
        this.owner = owner;
        this.adminReviewOnly = adminReviewOnly;
        this.viewOnly = viewOnly;
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
        return registrationStatus == RegistrationStatus.CONFIRMED;
    }

    // El creador no se anota a su propio evento.
    public boolean isOwner() {
        return owner;
    }

    // Un admin puede VER un evento todavia no aprobado (o ya rechazado) para revisarlo, pero no
    // inscribirse: register() lo trata como un id inexistente.
    public boolean isAdminReviewOnly() {
        return adminReviewOnly;
    }

    // Un admin puede VER un evento privado para moderarlo, pero no inscribirse si nadie lo invito:
    // register() lo trata como un id inexistente. La pantalla no le ofrece un boton que terminaria en 404.
    public boolean isViewOnly() {
        return viewOnly;
    }
}
