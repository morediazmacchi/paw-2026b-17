// Contrato de persistencia para Review. Implementacion: ReviewJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.RatingSummary;
import ar.edu.itba.paw.model.Review;

import java.util.List;
import java.util.Optional;

public interface ReviewDao {
    Review create(long eventId, long userId, int rating, String comment);

    // Para "Mis inscripciones": este usuario ya reseño este evento?
    Optional<Review> findByEventAndUser(long eventId, long userId);

    // Como findByEventAndUser(), pero para varios eventos a la vez (ver
    // RegistrationService.findMyRegistrations()): una sola consulta para todas las
    // reseñas propias en vez de un findByEventAndUser() por evento finalizado.
    List<Review> findByEventsAndUser(List<Long> eventIds, long userId);

    // Para el popup "Ver reseñas de este promotor" (detalle de evento y "Mis eventos"): una
    // pagina de las reseñas de todos los eventos que creo ese usuario, de la mas nueva a la mas
    // vieja. El corte se hace en la base (page empieza en 1).
    List<Review> findByCreatorId(long creatorId, int page, int pageSize);

    // Cuantas reseñas tienen en total los eventos de ese creador y su promedio de estrellas (0 si no tiene ninguna),
    // en UNA sola consulta (COUNT y AVG sobre las mismas filas, sin traerlas). Es el UNICO lugar donde se calcula el
    // promedio que se muestra; el filtro "puntaje minimo" de EventJpaDao usa el mismo AVG.
    RatingSummary findRatingSummaryByCreatorId(long creatorId);
}
