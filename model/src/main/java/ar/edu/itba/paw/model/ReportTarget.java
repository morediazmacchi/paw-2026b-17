package ar.edu.itba.paw.model;

// A que evento apunta un reporte y de quien es ese evento: lo minimo para decidir si un admin
// puede resolver el reporte (no puede si el evento es suyo, ver AdminSecurity.canResolveReport).
// Es una proyeccion (no una entidad): se arma con una consulta de dos columnas, sin cargar el
// reporte ni el evento. Lo devuelve EventReportDao.findTargetById().
public class ReportTarget {

    private final long eventId;
    private final Long creatorId;

    public ReportTarget(final long eventId, final Long creatorId) {
        this.eventId = eventId;
        this.creatorId = creatorId;
    }

    public long getEventId() {
        return eventId;
    }

    // null solo en una fila vieja que todavia no paso por el backfill de postgres-migrations.sql.
    public Long getCreatorId() {
        return creatorId;
    }
}
