// Contrato de servicio para Review. Implementacion: ReviewServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// @Transactional aca documenta la garantia que promete cada metodo; el proxy
// de verdad lo arma Spring sobre ReviewServiceImpl (ver RegistrationService
// para el mismo criterio).
public interface ReviewService {

    /** Para "Mis inscripciones": este usuario ya reseño este evento? */
    @Transactional(readOnly = true)
    Optional<Review> findByEventAndUser(long eventId, long userId);

    /**
     * Deja una reseña de un evento finalizado al que asistio. Si no se puede,
     * no crea nada y el resultado dice por que (ver ReviewResult).
     */
    @Transactional(rollbackFor = Exception.class)
    ReviewResult create(long eventId, User user, int rating, String comment);

    /** Puntaje del promotor: promedio de todas las reseñas de sus eventos (0 si no tiene). */
    @Transactional(readOnly = true)
    double findCreatorAverageRating(long creatorId);

    /**
     * Todas las reseñas de todos los eventos que creo ese usuario: el promedio
     * (para el detalle de evento y "Mis eventos") sale de promediar esta lista.
     */
    @Transactional(readOnly = true)
    List<Review> findByCreatorId(long creatorId);
}
