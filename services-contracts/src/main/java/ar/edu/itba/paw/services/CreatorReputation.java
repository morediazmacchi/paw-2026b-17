// La reputacion de un organizador, ya armada para el detalle de un evento y "Mis eventos": una
// pagina de sus reseñas, cuantas tiene en total, el promedio, y los eventos de las reseñas de esa
// pagina (para mostrar de que evento es cada una). Lo devuelve ReviewService.findCreatorReputation().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Review;

import java.util.List;
import java.util.Map;

public class CreatorReputation {

    private final PageResult<Review> reviews;
    private final double averageRating;
    private final Map<Long, Event> eventsById;

    public CreatorReputation(final PageResult<Review> reviews, final double averageRating,
                             final Map<Long, Event> eventsById) {
        this.reviews = reviews;
        this.averageRating = averageRating;
        this.eventsById = eventsById;
    }

    // El total de reseñas (COUNT en la base), no solo las de esta pagina.
    public long getReviewCount() {
        return reviews.getTotalElements();
    }

    public boolean hasReviews() {
        return reviews.getTotalElements() > 0;
    }

    // Las reseñas de esta pagina.
    public List<Review> getReviews() {
        return reviews.getContent();
    }

    public int getPage() {
        return reviews.getPage();
    }

    public int getTotalPages() {
        return reviews.getTotalPages();
    }

    // 0 si no tiene reseñas.
    public double getAverageRating() {
        return averageRating;
    }

    // Los eventos de las reseñas de esta pagina, por id. Vacio si no tiene reseñas.
    public Map<Long, Event> getEventsById() {
        return eventsById;
    }
}
