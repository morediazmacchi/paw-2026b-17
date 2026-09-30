package ar.edu.itba.paw.model;

import java.util.Date;

// Reporte de un evento hecho por un usuario (cualquiera, admins incluidos),
// revisado despues por un admin (ver ModerationService). No se borra nunca:
// queda como historial, con status distinto de PENDING una vez resuelto.
public class EventReport {

    private long id;
    private long eventId;
    private long reporterUserId;
    private String reason;
    private Date createdAt;
    private ReportStatus status = ReportStatus.PENDING;
    private Long resolvedByUserId;
    private Date resolvedAt;
    // Pedido de cambios (ver ModerationService.requestChanges()): distinto de
    // resolvedBy/resolvedAt, que son la resolucion FINAL (DISMISSED/ACTIONED).
    // Este reporte sigue abierto hasta que un admin lo revise de nuevo.
    private String changesRequestedMessage;
    private Date changesRequestedAt;
    private Long changesRequestedByUserId;

    public EventReport() {
    }

    public EventReport(final long eventId, final long reporterUserId, final String reason) {
        this.eventId = eventId;
        this.reporterUserId = reporterUserId;
        this.reason = reason;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public long getReporterUserId() { return reporterUserId; }
    public void setReporterUserId(final long reporterUserId) { this.reporterUserId = reporterUserId; }
    public String getReason() { return reason; }
    public void setReason(final String reason) { this.reason = reason; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(final ReportStatus status) { this.status = status; }
    public Long getResolvedByUserId() { return resolvedByUserId; }
    public void setResolvedByUserId(final Long resolvedByUserId) { this.resolvedByUserId = resolvedByUserId; }
    public Date getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(final Date resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getChangesRequestedMessage() { return changesRequestedMessage; }
    public void setChangesRequestedMessage(final String changesRequestedMessage) {
        this.changesRequestedMessage = changesRequestedMessage;
    }
    public Date getChangesRequestedAt() { return changesRequestedAt; }
    public void setChangesRequestedAt(final Date changesRequestedAt) { this.changesRequestedAt = changesRequestedAt; }
    public Long getChangesRequestedByUserId() { return changesRequestedByUserId; }
    public void setChangesRequestedByUserId(final Long changesRequestedByUserId) {
        this.changesRequestedByUserId = changesRequestedByUserId;
    }

    public boolean isPending() { return status == ReportStatus.PENDING; }

    // "Abierto": todavia no llego a una resolucion final (DISMISSED/ACTIONED).
    // dismiss()/removeEvent() actuan sobre cualquier reporte abierto, no solo
    // los PENDING sin revisar: uno con cambios pedidos tambien se puede
    // descartar (el creador ya corrigio) o dar de baja (no corrigio).
    public boolean isOpen() { return status == ReportStatus.PENDING || status == ReportStatus.CHANGES_REQUESTED; }
}
