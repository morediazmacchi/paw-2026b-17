package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

// Reporte de un evento hecho por un usuario (cualquiera, admins incluidos),
// revisado despues por un admin (ver ModerationService). No se borra nunca:
// queda como historial, con status distinto de PENDING una vez resuelto.
@Entity
@Table(name = "event_report")
public class EventReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Relacion con el evento (muchos N:1). LAZY: Hibernate trae el evento de la base recien
    // cuando alguien lo usa; mientras tanto deja un "representante" que ya conoce el id.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    // Quien hizo la denuncia.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;
    @Column(name = "reason", length = 500, nullable = false)
    private String reason;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReportStatus status = ReportStatus.PENDING;
    // Admin que resolvio la denuncia (null mientras esta abierta).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;
    @Column(name = "resolved_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date resolvedAt;
    // Pedido de cambios (ver ModerationService.requestChanges()): distinto de
    // resolvedBy/resolvedAt, que son la resolucion FINAL (DISMISSED/ACTIONED).
    // Este reporte sigue abierto hasta que un admin lo revise de nuevo.
    @Column(name = "changes_requested_message", length = 1000)
    private String changesRequestedMessage;
    @Column(name = "changes_requested_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date changesRequestedAt;
    // Admin que pidio cambios al creador (null si nunca se pidieron).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changes_requested_by")
    private User changesRequestedBy;

    public EventReport() {
    }

    public EventReport(final Event event, final User reporter, final String reason) {
        this.event = event;
        this.reporter = reporter;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    public long getEventId() { return event.getId(); }
    public User getReporter() { return reporter; }
    public void setReporter(final User reporter) { this.reporter = reporter; }
    public long getReporterUserId() { return reporter.getId(); }
    public String getReason() { return reason; }
    public void setReason(final String reason) { this.reason = reason; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(final ReportStatus status) { this.status = status; }
    public User getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(final User resolvedBy) { this.resolvedBy = resolvedBy; }
    public Long getResolvedByUserId() { return resolvedBy == null ? null : resolvedBy.getId(); }
    public Date getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(final Date resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getChangesRequestedMessage() { return changesRequestedMessage; }
    public void setChangesRequestedMessage(final String changesRequestedMessage) {
        this.changesRequestedMessage = changesRequestedMessage;
    }
    public Date getChangesRequestedAt() { return changesRequestedAt; }
    public void setChangesRequestedAt(final Date changesRequestedAt) { this.changesRequestedAt = changesRequestedAt; }
    public User getChangesRequestedBy() { return changesRequestedBy; }
    public void setChangesRequestedBy(final User changesRequestedBy) { this.changesRequestedBy = changesRequestedBy; }
    public Long getChangesRequestedByUserId() { return changesRequestedBy == null ? null : changesRequestedBy.getId(); }

    public boolean isPending() { return status == ReportStatus.PENDING; }

    // "Abierto": todavia no llego a una resolucion final (DISMISSED/ACTIONED).
    // dismiss()/removeEvent() actuan sobre cualquier reporte abierto, no solo
    // los PENDING sin revisar: uno con cambios pedidos tambien se puede
    // descartar (el creador ya corrigio) o dar de baja (no corrigio).
    public boolean isOpen() { return status == ReportStatus.PENDING || status == ReportStatus.CHANGES_REQUESTED; }
}
