// Contrato de persistencia para Review. Implementacion: ReviewJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewDao {
    Review create(Review review);

    // Para "Mis inscripciones": este usuario ya reseño este evento?
    Optional<Review> findByEventAndUser(long eventId, long userId);

    // Para el promedio del promotor y el popup "Ver reseñas de este promotor":
    // todas las reseñas de todos los eventos que creo ese email.
    List<Review> findByCreatorEmail(String creatorEmail);

    // Promedio de estrellas de todas las reseñas de los eventos de ese email
    // (0 si no tiene ninguna). Es el UNICO lugar donde se calcula el promedio que
    // se muestra; el filtro "puntaje minimo" de EventJdbcDao usa el mismo AVG.
    double findAverageRatingByCreatorEmail(String creatorEmail);
}
