// Implementa el contrato ReviewService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.ReviewDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class ReviewServiceImpl implements ReviewService {

    private final ReviewDao reviewDao;
    private final EventService eventService;
    private final RegistrationService registrationService;

    @Autowired
    public ReviewServiceImpl(final ReviewDao reviewDao, final EventService eventService,
                             final RegistrationService registrationService) {
        this.reviewDao = reviewDao;
        this.eventService = eventService;
        this.registrationService = registrationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Review> findByEventAndUser(final long eventId, final long userId) {
        return reviewDao.findByEventAndUser(eventId, userId);
    }

    @Override
    @Transactional
    public ReviewResult create(final long eventId, final User user, final int rating, final String comment) {
        // Ya la mando: no se puede editar ni volver a mandar (chequeo primero,
        // antes de pedir el evento, es el caso mas comun de "no eligible").
        if (reviewDao.findByEventAndUser(eventId, user.getId()).isPresent()) {
            return ReviewResult.ALREADY_REVIEWED;
        }
        final Optional<Event> event = eventService.findById(eventId);
        if (!event.isPresent() || !event.get().getEventDate().before(new Date())) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        // Un evento cancelado no sucedio: no hay nada que reseñar.
        if (event.get().isCancelled()) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        // Solo quien tuvo su lugar confirmado fue al evento. Estar en lista de
        // espera, pendiente de aprobacion o rechazado no alcanza.
        final Optional<Registration> registration = registrationService.findByEventAndUserId(eventId, user.getId());
        if (!registration.isPresent() || registration.get().getStatus() != RegistrationStatus.CONFIRMED) {
            return ReviewResult.NOT_ELIGIBLE;
        }
        try {
            reviewDao.create(new Review(eventId, user.getId(), rating, comment));
        } catch (final DataIntegrityViolationException e) {
            // Dos envios al mismo tiempo: el SELECT de arriba no vio nada, pero el
            // otro ya inserto justo antes y choca contra el UNIQUE(event_id, user_id).
            // El contrato de create() promete que el resultado es el unico canal de
            // salida (ver ReviewService): no corresponde dejar escapar esto hacia el
            // controller.
            return ReviewResult.ALREADY_REVIEWED;
        }
        return ReviewResult.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public double findCreatorAverageRating(final long creatorId) {
        return reviewDao.findAverageRatingByCreatorId(creatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Review> findByCreatorId(final long creatorId) {
        return reviewDao.findByCreatorId(creatorId);
    }
}
