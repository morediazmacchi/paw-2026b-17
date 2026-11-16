// Los 5 contadores de las pestañas del panel "Reportes y seguimiento" (ver EventReportDao.countStatuses()):
// cuantos reportes hay en total y en cada pestaña, sin filtrar por texto (para que el numero de cada pestaña
// no cambie mientras se escribe en el buscador).
package ar.edu.itba.paw.model;

public class ReportStatusCounts {

    private final long total;
    private final long nuevos;
    private final long enRevision;
    private final long modificacionSolicitada;
    private final long resueltos;

    public ReportStatusCounts(final long total, final long nuevos, final long enRevision,
                              final long modificacionSolicitada, final long resueltos) {
        this.total = total;
        this.nuevos = nuevos;
        this.enRevision = enRevision;
        this.modificacionSolicitada = modificacionSolicitada;
        this.resueltos = resueltos;
    }

    public long getTotal() { return total; }
    public long getNuevos() { return nuevos; }
    public long getEnRevision() { return enRevision; }
    public long getModificacionSolicitada() { return modificacionSolicitada; }
    public long getResueltos() { return resueltos; }
}
