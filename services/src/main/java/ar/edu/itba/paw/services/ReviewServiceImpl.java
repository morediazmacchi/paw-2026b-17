// Implementa el contrato ReviewService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.RatingSummary;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewServiceImpl implements ReviewService {

    // De a cuantas reseñas se muestran en el popup del organizador.
    static final int REVIEWS_PAGE_SIZE = 5;

    private final ReviewDao reviewDao;
    // EventService: el lock de create() (findByIdForUpdate), mismo criterio que RegistrationServiceImpl.register().
    private final EventService eventService;

    @Autowired
    public ReviewServiceImpl(final ReviewDao reviewDao, final EventService eventService) {
        this.reviewDao = reviewDao;
        this.eventService = eventService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findByEventAndUser(final long eventId, final long userId) {
        return reviewDao.findByEventAndUser(eventId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResult create(final long eventId, final long userId, final int rating, final String comment) {
        // Se valida ANTES de escribir (y antes del lock). El form ya lo chequea para dar feedback.
        if (rating < MIN_RATING || rating > MAX_RATING
                || (comment != null && comment.length() > MAX_COMMENT_LENGTH)) {
            return ReviewResult.INVALID;
        }
        // Lockea el evento primero (mismo criterio que RegistrationServiceImpl.
        // register()): dos envios simultaneos del mismo usuario se serializan
        // aca, en vez de pasar los dos el chequeo de "ya la mando" y recien
        // chocar contra el UNIQUE(event_id, user_id) al insertar.
        final Optional<Event> event = eventService.findByIdForUpdate(eventId);
        if (reviewDao.findByEventAndUser(eventId, userId).isPresent()) {
            return ReviewResult.ALREADY_REVIEWED;
        }
        // Tiene que haber pasado y no haberse cancelado (uno cancelado no sucedio): ver Event.isReviewable().
        if (!event.isPresent() || !event.get().isReviewable()) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        // Que haya tenido su lugar CONFIRMADO (no lista de espera, pendiente ni rechazado) lo decide @PreAuthorize en
        // el controller (ver EventSecurity.isConfirmedAttendee).
        try {
            reviewDao.create(eventId, userId, rating, comment);
        } catch (final DataIntegrityViolationException e) {
            // Solo la violacion del UNIQUE(event_id, user_id) es "ya la mando": dos envios al mismo tiempo, el SELECT
            // de arriba no vio nada pero el otro ya inserto justo antes. Cualquier otra violacion (un CHECK, un largo)
            // es un error de verdad: no se la disfraza de ALREADY_REVIEWED.
            if (!UniqueViolation.isUniqueViolation(e)) {
                throw e;
            }
            // El contrato de create() promete que el resultado es el unico canal de salida (ver ReviewService): no
            // corresponde dejar escapar esto hacia el controller.
            // Con JPA, el insert fallido ya dejo la transaccion marcada para rollback: se lo decimos
            // explicitamente, asi Spring hace rollback sin tirar UnexpectedRollbackException.
            RollbackOnly.ifInTransaction();
            return ReviewResult.ALREADY_REVIEWED;
        }
        return ReviewResult.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public CreatorReputation findCreatorReputation(final long creatorId, final int page) {
        // El total y el promedio salen de UNA consulta (COUNT + AVG): no hace falta traer las reseñas para contarlas.
        final RatingSummary summary = reviewDao.findRatingSummaryByCreatorId(creatorId);
        final long total = summary.getCount();
        if (total == 0) {
            return new CreatorReputation(new PageResult<>(Collections.emptyList(), 0, 1, REVIEWS_PAGE_SIZE), 0);
        }
        final int effectivePage = PageResult.clampPage(page, total, REVIEWS_PAGE_SIZE);
        // Cada reseña ya trae su evento (lo carga la misma consulta del DAO).
        final List<Review> reviews = reviewDao.findByCreatorId(creatorId, effectivePage, REVIEWS_PAGE_SIZE);
        return new CreatorReputation(new PageResult<>(reviews, total, effectivePage, REVIEWS_PAGE_SIZE),
                summary.getAverage());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findByEventsAndUser(final List<Long> eventIds, final long userId) {
        return reviewDao.findByEventsAndUser(eventIds, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingSummary findRatingSummaryByCreator(final long creatorId) {
        return reviewDao.findRatingSummaryByCreatorId(creatorId);
    }
}
