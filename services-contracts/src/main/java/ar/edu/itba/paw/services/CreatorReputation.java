// La reputacion de un organizador, ya armada para el detalle de un evento: sus reseñas, el
// promedio, y los eventos reseñados (para mostrar de que evento es cada reseña).
// Lo devuelve ReviewService.findCreatorReputation().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Review;

import java.util.List;
import java.util.Map;

public class CreatorReputation {

    private final List<Review> reviews;
    private final double averageRating;
    private final Map<Long, Event> eventsById;

    public CreatorReputation(final List<Review> reviews, final double averageRating,
                             final Map<Long, Event> eventsById) {
        this.reviews = reviews;
        this.averageRating = averageRating;
        this.eventsById = eventsById;
    }

    public int getReviewCount() {
        return reviews.size();
    }

    public boolean hasReviews() {
        return !reviews.isEmpty();
    }

    public List<Review> getReviews() {
        return reviews;
    }

    // 0 si no tiene reseñas.
    public double getAverageRating() {
        return averageRating;
    }

    // Los eventos de esas reseñas, por id. Vacio si no tiene reseñas.
    public Map<Long, Event> getEventsById() {
        return eventsById;
    }
}
