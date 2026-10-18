// Si un admin ya revisó este evento antes de que sea visible publicamente (ver
// Event.approvalStatus). NO tiene nada que ver con Event.requiresApproval (eso
// es si el ORGANIZADOR tiene que aceptar/rechazar las INSCRIPCIONES a su propio
// evento) ni con el sistema de reportes de ModerationService (eventos ya
// publicados que alguien denuncia): esto es la revisión previa a publicarse.
package ar.edu.itba.paw.model;

public enum EventApprovalStatus {
    PENDING,
    APPROVED,
    // Definitivo (mismo criterio que un evento dado de baja por moderación):
    // no hay reenvío a revisión, si el creador quiere publicarlo tiene que
    // crear un evento nuevo.
    REJECTED
}
