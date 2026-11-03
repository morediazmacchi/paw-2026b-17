// Resultado de resolver un reporte (ModerationService.dismiss()/removeEvent()).
package ar.edu.itba.paw.services;

public enum ReportResolutionResult {
    /** Se resolvio (descartado o accionado, segun el metodo). */
    RESOLVED,

    /** No existe ese reporte, o ya estaba resuelto de antes. */
    NOT_FOUND,
    /** El mensaje esta vacio o supera ModerationService.MAX_CHANGES_MESSAGE_LENGTH. */
    INVALID
}
