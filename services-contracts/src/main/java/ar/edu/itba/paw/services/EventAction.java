// Que se le ofrece a una persona en el detalle de un evento, ya decidido por el service (ver
// EventDetailService.getDetail()). La vista solo hace un switch sobre este valor: no re-decide ninguna regla.
package ar.edu.itba.paw.services;

public enum EventAction {
    // Es el organizador. Nunca se anota a su propio evento.
    OWNER,
    // Es el organizador y el evento todavia espera la revision de un admin.
    OWNER_PENDING,
    // Es el organizador y un admin rechazo el evento.
    OWNER_REJECTED,
    // Es el organizador y un admin le pidio cambios (no es definitivo, ver EventApprovalStatus.CHANGES_REQUESTED).
    OWNER_CHANGES_REQUESTED,
    // Un admin ve un evento todavia no aprobado (o rechazado) para revisarlo: no puede anotarse.
    ADMIN_REVIEW_ONLY,
    // Un admin ve un evento privado al que nadie lo invito: puede verlo para moderar, no anotarse.
    VIEW_ONLY,
    // Evento cancelado por el organizador.
    CLOSED_CANCELLED,
    // Evento dado de baja por un admin (moderacion).
    CLOSED_REMOVED_BY_ADMIN,
    // El evento ya paso.
    CLOSED_FINISHED,
    // El organizador rechazo la inscripcion de esta persona: no hay nada que ofrecer.
    REGISTRATION_REJECTED,
    // Puede anotarse (hay lugar).
    REGISTER,
    // Puede anotarse y el organizador tiene que aceptarla (hay lugar).
    REQUEST_APPROVAL,
    // El evento esta lleno (para esta persona): puede anotarse, pero en la lista de espera.
    JOIN_WAITLIST,
    // Esta anotada con lugar confirmado: puede darse de baja.
    UNREGISTER,
    // Esta en la lista de espera: puede salir de ella.
    LEAVE_WAITLIST,
    // Su pedido espera la respuesta del organizador: puede retirarlo.
    CANCEL_PENDING
}
