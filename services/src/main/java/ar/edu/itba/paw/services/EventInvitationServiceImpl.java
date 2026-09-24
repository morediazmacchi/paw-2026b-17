// Implementa el contrato EventInvitationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Service
public class EventInvitationServiceImpl implements EventInvitationService {

    // Mismo criterio que WaitlistServiceImpl: 32 bytes de azar criptografico,
    // no UUID ni nada secuencial. El token es la unica credencial de quien
    // abre el link, tiene que ser imposible de adivinar.
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EventInvitationDao invitationDao;
    private final EventDao eventDao;
    private final NotificationService notificationService;

    @Autowired
    public EventInvitationServiceImpl(final EventInvitationDao invitationDao, final EventDao eventDao,
                                       final NotificationService notificationService) {
        this.invitationDao = invitationDao;
        this.eventDao = eventDao;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public EventInvitationResult invite(final long eventId, final String creatorEmail, final String inviteeEmail) {
        final Event event = eventDao.findById(eventId).orElse(null);
        if (event == null || !event.isPrivate() || !event.getCreatorEmail().equalsIgnoreCase(creatorEmail.trim())) {
            return EventInvitationResult.NOT_ALLOWED;
        }

        final String normalizedEmail = inviteeEmail.trim().toLowerCase();
        if (invitationDao.findByEventAndEmail(eventId, normalizedEmail).isPresent()) {
            return EventInvitationResult.ALREADY_INVITED;
        }

        final EventInvitation invitation = new EventInvitation(eventId, normalizedEmail, newToken(), creatorEmail);
        invitationDao.create(invitation);
        notificationService.notifyEventInvitation(event, invitation);
        return EventInvitationResult.SENT;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInvited(final long eventId, final String email) {
        if (email == null) {
            return false;
        }
        return invitationDao.findByEventAndEmail(eventId, email.trim().toLowerCase()).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventInvitation> findByEvent(final long eventId, final String creatorEmail) {
        return eventDao.findById(eventId)
                .filter(event -> event.getCreatorEmail().equalsIgnoreCase(creatorEmail.trim()))
                .map(event -> invitationDao.findByEvent(eventId))
                .orElse(Collections.emptyList());
    }

    private static String newToken() {
        final byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        // Sin padding y en el alfabeto de URLs: entra tal cual en un link.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
