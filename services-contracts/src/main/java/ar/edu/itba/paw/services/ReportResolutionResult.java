// Resultado de resolver un reporte (ModerationService.dismiss()/removeEvent()).
package ar.edu.itba.paw.services;

public enum ReportResolutionResult {
    /** Se resolvio (descartado o accionado, segun el metodo). */
    RESOLVED,

    /** No existe ese reporte, o ya estaba resuelto de antes. */
    NOT_FOUND,

    // Quien modera es el creador del evento reportado: mismo conflicto de
    // intereses que ReportResult.OWN_EVENT en report(), pero del lado de
    // quien resuelve en vez de quien reporta.
    OWN_EVENT
}
