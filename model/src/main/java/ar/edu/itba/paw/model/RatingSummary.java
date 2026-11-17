package ar.edu.itba.paw.model;

// Cuantas reseñas tienen en total los eventos de un organizador y su promedio de estrellas, juntos porque salen de UNA
// sola consulta (COUNT y AVG sobre las mismas filas). La devuelve ReviewDao.findRatingSummaryByCreatorId().
public class RatingSummary {

    private final long count;
    private final double average;

    public RatingSummary(final long count, final double average) {
        this.count = count;
        this.average = average;
    }

    public long getCount() {
        return count;
    }

    // 0 si no tiene reseñas.
    public double getAverage() {
        return average;
    }

    public boolean hasReviews() {
        return count > 0;
    }
}
