// Implementa el contrato EventAccessService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EventAccessServiceImpl implements EventAccessService {

    private final EventInvitationService eventInvitationService;

    @Autowired
    public EventAccessServiceImpl(final EventInvitationService eventInvitationService) {
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
}
