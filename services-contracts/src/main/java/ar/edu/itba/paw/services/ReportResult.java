// Resultado de reportar un evento (ModerationService.report()).
package ar.edu.itba.paw.services;

public enum ReportResult {
    /** Se creo el reporte, va a la cola de moderacion. */
    CREATED,

    /** Quien reporta ya tenia un reporte pendiente sobre este mismo evento. */
    ALREADY_REPORTED,

    /** El creador del evento no puede reportar su propio evento. */
    OWN_EVENT
}
