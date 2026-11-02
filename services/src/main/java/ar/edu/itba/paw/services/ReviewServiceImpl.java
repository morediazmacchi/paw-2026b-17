// Implementa el contrato ReviewService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewServiceImpl implements ReviewService {

    // De a cuantas reseñas se muestran en el popup del organizador.
    static final int REVIEWS_PAGE_SIZE = 5;

    private final ReviewDao reviewDao;
    // EventDao directo (no EventService): hace falta findByIdForUpdate() para
    // el lock de create(), mismo criterio que RegistrationServiceImpl.register().
    private final EventDao eventDao;
    private final RegistrationService registrationService;

    @Autowired
    public ReviewServiceImpl(final ReviewDao reviewDao, final EventDao eventDao,
                             final RegistrationService registrationService) {
        this.reviewDao = reviewDao;
        this.eventDao = eventDao;
        this.registrationService = registrationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findByEventAndUser(final long eventId, final long userId) {
        return reviewDao.findByEventAndUser(eventId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResult create(final long eventId, final User user, final int rating, final String comment) {
        Objects.requireNonNull(user, "user");
        // Lockea el evento primero (mismo criterio que RegistrationServiceImpl.
        // register()): dos envios simultaneos del mismo usuario se serializan
        // aca, en vez de pasar los dos el chequeo de "ya la mando" y recien
        // chocar contra el UNIQUE(event_id, user_id) al insertar.
        final Optional<Event> event = eventDao.findByIdForUpdate(eventId);
        if (reviewDao.findByEventAndUser(eventId, user.getId()).isPresent()) {
            return ReviewResult.ALREADY_REVIEWED;
        }
        // Tiene que haber pasado y no haberse cancelado (uno cancelado no sucedio): ver Event.isReviewable().
        if (!event.isPresent() || !event.get().isReviewable()) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        // Solo quien tuvo su lugar confirmado fue al evento. Estar en lista de
        // espera, pendiente de aprobacion o rechazado no alcanza.
        final Optional<Registration> registration = registrationService.findByEventAndUserId(eventId, user.getId());
        if (!registration.isPresent() || registration.get().getStatus() != RegistrationStatus.CONFIRMED) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        try {
            reviewDao.create(new Review(event.get(), user, rating, comment));
        } catch (final DataIntegrityViolationException e) {
            // Dos envios al mismo tiempo: el SELECT de arriba no vio nada, pero el
            // otro ya inserto justo antes y choca contra el UNIQUE(event_id, user_id).
            // El contrato de create() promete que el resultado es el unico canal de
            // salida (ver ReviewService): no corresponde dejar escapar esto hacia el
            // controller.
            // Con JPA, el insert fallido ya dejo la transaccion marcada para rollback: se lo
            // decimos explicitamente, asi Spring hace rollback sin tirar UnexpectedRollbackException.
            RollbackOnly.ifInTransaction();
            return ReviewResult.ALREADY_REVIEWED;
        }
        return ReviewResult.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorReputation findCreatorReputation(final long creatorId, final int page) {
        // El total sale de un COUNT en la base: no hace falta traer las reseñas para contarlas.
        final long total = reviewDao.countByCreatorId(creatorId);
        if (total == 0) {
            return new CreatorReputation(new PageResult<>(Collections.emptyList(), 0, 1, REVIEWS_PAGE_SIZE),
                    0, Collections.emptyMap());
        }
        final int effectivePage = PageResult.clampPage(page, total, REVIEWS_PAGE_SIZE);
        final List<Review> reviews = reviewDao.findByCreatorId(creatorId, effectivePage, REVIEWS_PAGE_SIZE);
        // Una sola consulta para los eventos de las reseñas de esta pagina (findByIds()), en vez de
        // un findById() por cada reseña.
        final List<Long> eventIds = reviews.stream().map(Review::getEventId).distinct().collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventDao.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        return new CreatorReputation(new PageResult<>(reviews, total, effectivePage, REVIEWS_PAGE_SIZE),
                reviewDao.findAverageRatingByCreatorId(creatorId), eventsById);
    }
}
