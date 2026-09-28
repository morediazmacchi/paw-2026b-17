// Implementa el contrato EventInvitationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class EventInvitationServiceImpl implements EventInvitationService {

    private final EventInvitationDao invitationDao;
    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final NotificationService notificationService;
    private final InboxService inboxService;
    private final UserService userService;

    @Autowired
    public EventInvitationServiceImpl(final EventInvitationDao invitationDao, final EventDao eventDao,
                                       final RegistrationDao registrationDao,
                                       final NotificationService notificationService,
                                       final InboxService inboxService,
                                       final UserService userService) {
        this.invitationDao = invitationDao;
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.notificationService = notificationService;
        this.inboxService = inboxService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public EventInvitationResult invite(final long eventId, final long creatorId, final String inviteeEmail) {
        // Con lock: dos invitaciones simultaneas no pueden pasar el cupo (cada una
        // reserva un lugar y se cuenta contra el cupo).
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        if (event == null || !event.isPrivate() || !Long.valueOf(creatorId).equals(event.getCreatorId())) {
            return EventInvitationResult.NOT_ALLOWED;
        }

        final String normalizedEmail = inviteeEmail.trim().toLowerCase();
        // El invitado (Group B) todavia se identifica por mail, no por user_id:
        // puede no tener cuenta. Para el autoinvite se compara igual contra el
        // mail del creador (event.getCreatorEmail() sigue poblado en paralelo).
        if (normalizedEmail.equals(event.getCreatorEmail().trim().toLowerCase())) {
            return EventInvitationResult.SELF_INVITE;
        }
        if (invitationDao.findByEventAndEmail(eventId, normalizedEmail).isPresent()) {
            return EventInvitationResult.ALREADY_INVITED;
        }

        // Cada invitacion reserva un lugar (cuenta como cupo ocupado): si ya no
        // queda ninguno, no se puede invitar a nadie mas.
        if (event.getMaxCapacity() != null && registrationDao.countConfirmed(eventId)
                + invitationDao.countOutstanding(eventId) >= event.getMaxCapacity()) {
            return EventInvitationResult.FULL;
        }

        final EventInvitation invitation = new EventInvitation(eventId, normalizedEmail, event.getCreatorEmail());
        invitation.setInvitedByUserId(creatorId);
        // Optimista (Grupo B, ver schema.sql): si el invitado ya tiene cuenta,
        // se enlaza de una. Si no, queda en null y se reconcilia solo cuando
        // se registre (ver UserServiceImpl.register()).
        userService.findByEmail(normalizedEmail).ifPresent(invitee -> invitation.setUserId(invitee.getId()));
        invitationDao.create(invitation);
        notificationService.notifyEventInvitation(event, invitation);
        inboxService.add(normalizedEmail, InboxMessageType.PRIVATE_INVITE, eventId, null, null);
        return EventInvitationResult.SENT;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInvited(final long eventId, final long userId) {
        return invitationDao.findByEventAndUserId(eventId, userId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventInvitation> findByEvent(final long eventId, final long creatorId) {
        return eventDao.findById(eventId)
                .filter(event -> Long.valueOf(creatorId).equals(event.getCreatorId()))
                .map(event -> invitationDao.findByEvent(eventId))
                .orElse(Collections.emptyList());
    }
}
