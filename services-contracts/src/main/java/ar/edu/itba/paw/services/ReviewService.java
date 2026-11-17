// Contrato de servicio para Review. Implementacion: ReviewServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.RatingSummary;
import ar.edu.itba.paw.model.Review;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// @Transactional aca documenta la garantia que promete cada metodo; el proxy
// de verdad lo arma Spring sobre ReviewServiceImpl (ver RegistrationService
// para el mismo criterio).
public interface ReviewService {

    // Puntaje valido (estrellas) y largo maximo del comentario. El form ya los valida para dar feedback; create() los
    // vuelve a chequear (el puntaje tambien lo exige un CHECK en la base).
    int MIN_RATING = 0;
    int MAX_RATING = 5;
    int MAX_COMMENT_LENGTH = 100;

    /** Para "Mis inscripciones": este usuario ya reseño este evento? */
    @Transactional(readOnly = true)
    Optional<Review> findByEventAndUser(long eventId, long userId);

    /**
     * Deja una reseña de un evento finalizado. Que quien la deja haya tenido su lugar CONFIRMADO lo decide
     * @PreAuthorize en el controller (ver EventSecurity.isConfirmedAttendee); aca se valida el estado del evento y
     * que no la haya dejado antes. Si no se puede, no crea nada y el resultado dice por que (ver ReviewResult).
     */
    @Transactional(rollbackFor = Exception.class)
    ReviewResult create(long eventId, long userId, int rating, String comment);

    /**
     * La reputacion del organizador ya armada para el detalle de un evento y "Mis eventos": una
     * pagina de sus reseñas (page empieza en 1; fuera de rango se ajusta), el total (COUNT), el
     * promedio (AVG) y los eventos de esa pagina (una sola consulta para todos). Sin reseñas,
     * devuelve una reputacion vacia (promedio 0).
     */
    @Transactional(readOnly = true)
    CreatorReputation findCreatorReputation(long creatorId, int page);

    /**
     * La reseña propia de esa persona en cada uno de esos eventos (los que no reseño no vienen), en UNA consulta. Es lo
     * que usa "Mis inscripciones" para mostrar la reseña de cada evento ya pasado.
     */
    @Transactional(readOnly = true)
    List<Review> findByEventsAndUser(List<Long> eventIds, long userId);

    /**
     * Cuantas reseñas tiene el organizador y su promedio de estrellas (0 si no tiene), en UNA consulta (COUNT + AVG
     * en la base, sin traerlas): lo que muestra su perfil publico.
     */
    @Transactional(readOnly = true)
    RatingSummary findRatingSummaryByCreator(long creatorId);
}
