// Pestañas del panel unificado "Reportes y seguimiento" (ver ModerationService.searchQueue()). NEW/IN_REVIEW
// son los dos subgrupos de ReportStatus.PENDING (segun si ya se abrio el detalle, ver EventReport.isInReview());
// CHANGES_REQUESTED es el mismo ReportStatus; RESOLVED junta DISMISSED y ACTIONED.
package ar.edu.itba.paw.model;

public enum ReportStatusFilter {
    ALL,
    NEW,
    IN_REVIEW,
    CHANGES_REQUESTED,
    RESOLVED
}
