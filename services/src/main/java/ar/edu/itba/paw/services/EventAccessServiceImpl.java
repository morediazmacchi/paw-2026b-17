// Implementa el contrato EventAccessService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class EventAccessServiceImpl implements EventAccessService {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final EventInvitationService eventInvitationService;

    @Autowired
    public EventAccessServiceImpl(final EventService eventService, final RegistrationService registrationService,
                                  final EventInvitationService eventInvitationService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.eventInvitationService = eventInvitationService;
    }

    @Override
    public boolean canView(final Event event, final User user, final Optional<Registration> myRegistration) {
        if (!event.isPrivate()) {
            return true;
        }
        if (user != null && Long.valueOf(user.getId()).equals(event.getCreatorId())) {
            return true;
        }
        if (myRegistration.isPresent()) {
            return true;
        }
        return user != null && eventInvitationService.isInvited(event.getId(), user.getId());
    }

    @Override
    public void requireViewable(final Event event, final User user, final Optional<Registration> myRegistration) {
        if (!canView(event, user, myRegistration)) {
            // Mismo 404 que un id inexistente: no hay que revelar que el
            // evento existe pero que no es para vos.
            throw new EventNotFoundException(event.getId());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(final Event event, final User user) {
        if (!event.isPrivate()) {
            return true;
        }
        final Optional<Registration> myRegistration = user == null
                ? Optional.empty()
                : registrationService.findByEventAndUserId(event.getId(), user.getId());
        return canView(event, user, myRegistration);
    }

    @Override
    @Transactional(readOnly = true)
    public Event getViewableEvent(final long eventId, final User user) {
        final Event event = eventService.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (!canView(event, user)) {
            throw new EventNotFoundException(eventId);
        }
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public Event getOwnedEvent(final long eventId, final User user) {
        final Event event = eventService.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (user == null || !Long.valueOf(user.getId()).equals(event.getCreatorId())) {
            throw new EventNotFoundException(eventId);
        }
        return event;
    }

    @Override
    @Transactional(readOnly = true)
    public Event getEditableEvent(final long eventId, final User user) {
        final Event event = getOwnedEvent(eventId, user);
        if (event.isCancelled()) {
            throw new EventCancelledException(eventId);
        }
        return event;
    }
}
