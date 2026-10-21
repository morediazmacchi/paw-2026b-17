// Resultado de que un admin apruebe o rechace un evento (ver EventService.approve()/reject()).
package ar.edu.itba.paw.services;

public enum EventApprovalResult {
    OK,

    // No existe.
    NOT_FOUND,

    // Ya no estaba PENDING (otro admin ya lo aprobo o rechazo mientras tanto):
    // no se toca de nuevo, no se manda un segundo aviso al creador.
    ALREADY_DECIDED,

    // Mismo conflicto de intereses que ReportResolutionResult.OWN_EVENT: el
    // creador del evento no puede ser tambien quien lo aprueba o rechaza.
    OWN_EVENT
}
