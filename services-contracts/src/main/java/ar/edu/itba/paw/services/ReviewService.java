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
     * Deja una reseña de un evento finalizado al que asistio. false (y no crea
     * nada) si no cumple alguna condicion: el evento no existe, todavia no
     * termino, nunca estuvo anotado a el, o ya lo habia reseñado antes (no se
     * puede editar una reseña ya mandada).
     */
    boolean create(long eventId, User user, int rating, String comment);

    /**
     * Todas las reseñas de todos los eventos que creo ese email: el promedio
     * (para el detalle de evento y "Mis eventos") sale de promediar esta lista.
     */
    List<Review> findByCreatorEmail(String creatorEmail);
}
