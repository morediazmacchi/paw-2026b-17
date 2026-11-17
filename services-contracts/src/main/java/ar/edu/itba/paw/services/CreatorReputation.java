// La reputacion de un organizador, ya armada para el detalle de un evento y "Mis eventos": una
// pagina de sus reseñas (cada una con su evento ya cargado: review.getEvent()), cuantas tiene en total y el
// promedio. Lo devuelve ReviewService.findCreatorReputation().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Review;

import java.util.List;

public class CreatorReputation {

    private final PageResult<Review> reviews;
    private final double averageRating;

    public CreatorReputation(final PageResult<Review> reviews, final double averageRating) {
        this.reviews = reviews;
        this.averageRating = averageRating;
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
}
