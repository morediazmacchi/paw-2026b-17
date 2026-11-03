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

import java.util.List;

@Service
public class EventInvitationServiceImpl implements EventInvitationService {

    static final int PAGE_SIZE = 10;

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
    public EventInvitationResult invite(final long eventId, final String inviteeEmail) {
        // El form ya valida el formato (@Email) y el largo para dar feedback; esto es la defensa del service.
        if (inviteeEmail == null || inviteeEmail.trim().isEmpty() || inviteeEmail.trim().length() > MAX_EMAIL_LENGTH) {
            return EventInvitationResult.INVALID_EMAIL;
        }
        // Con lock: dos invitaciones simultaneas no pueden pasar el cupo (cada una
        // reserva un lugar y se cuenta contra el cupo).
        final Event event = eventDao.findByIdForUpdate(eventId).orElse(null);
        if (event == null || event.isCancelled()) {
            return EventInvitationResult.NOT_ALLOWED;
        }
        // Invitar solo tiene sentido en un evento privado: uno publico ya tiene un link que se puede
        // compartir. Es regla de negocio (el estado del evento), no de acceso: quien invita lo decide
        // @PreAuthorize en el controller (ver EventSecurity.isOwner).
        if (!event.isPrivate()) {
            return EventInvitationResult.EVENT_PUBLIC;
        }
        // Un evento que ya paso tampoco invita a nadie (saldria el mail de invitacion a algo que ya
        // sucedio); el cancelado, tampoco (ver arriba).
        if (event.isFinished()) {
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

        final EventInvitation invitation = new EventInvitation(event, normalizedEmail, event.getCreatorEmail());
        // Quien invita es el creador del evento (arriba ya se chequeo que creatorId lo sea).
        invitation.setInvitedByUser(event.getCreator());
        // Optimista (Grupo B, ver schema.sql): si el invitado ya tiene cuenta,
        // se enlaza de una. Si no, queda en null y se reconcilia solo cuando
        // se registre (ver UserServiceImpl.register()).
        userService.findByEmail(normalizedEmail).ifPresent(invitation::setUser);
        invitationDao.create(invitation);
        notificationService.notifyEventInvitation(event, invitation);
        inboxService.add(normalizedEmail, InboxMessageType.PRIVATE_INVITE, eventId, null, null);
        return EventInvitationResult.SENT;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInvited(final long eventId, final long userId) {
        return invitationDao.existsByEventAndUserId(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventInvitation> findByEvent(final long eventId) {
        return invitationDao.findByEvent(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findInvitedEventsPage(final long userId, final int page) {
        final long total = invitationDao.countByUserId(userId);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(invitationDao.findInvitedEventsByUserId(userId, effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByUser(final long userId) {
        return invitationDao.countByUserId(userId);
    }
}
