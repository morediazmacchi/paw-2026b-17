// Contrato de servicio para Review. Implementacion: ReviewServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;

import java.util.List;
import java.util.Optional;

public interface ReviewService {

    /** Para "Mis inscripciones": este usuario ya reseño este evento? */
    Optional<Review> findByEventAndUser(long eventId, long userId);

    /**
     * Deja una reseña de un evento finalizado al que asistio. Si no se puede,
     * no crea nada y el resultado dice por que (ver ReviewResult).
     */
    ReviewResult create(long eventId, User user, int rating, String comment);

    /** Puntaje del promotor: promedio de todas las reseñas de sus eventos (0 si no tiene). */
    double findCreatorAverageRating(long creatorId);

    /**
     * Todas las reseñas de todos los eventos que creo ese usuario: el promedio
     * (para el detalle de evento y "Mis eventos") sale de promediar esta lista.
     */
    List<Review> findByCreatorId(long creatorId);
}
