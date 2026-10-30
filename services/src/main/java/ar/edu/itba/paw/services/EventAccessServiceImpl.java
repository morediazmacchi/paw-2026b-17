// Implementa el contrato EventAccessService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessData;
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
    public boolean canView(final EventAccessData event, final User user,
                           final Optional<Registration> myRegistration) {
        return isViewable(event, userIdOf(user), isAdmin(user), myRegistration);
    }

    private static Long userIdOf(final User user) {
        return user == null ? null : user.getId();
    }

    private static boolean isAdmin(final User user) {
        return user != null && user.isAdmin();
    }

    // La regla de visibilidad, en un solo lugar. Trabaja con el id y con "es admin" (no con la
    // entidad User) para que la usen tanto quien tiene el User como quien solo tiene el principal.
    private boolean isViewable(final EventAccessData event, final Long userId, final boolean isAdmin,
                               final Optional<Registration> myRegistration) {
        // El creador y un admin lo ven siempre, este aprobado o no (el admin
        // necesita verlo justamente para poder aprobarlo/rechazarlo).
        if (userId != null && userId.equals(event.getCreatorId())) {
            return true;
        }
        if (isAdmin) {
            return true;
        }
        // Pendiente o rechazado: invisible para cualquier otra persona, sea
        // publico o privado -- mismo criterio que un evento privado sin
        // invitacion, antes de llegar a chequear eso.
        if (!event.isApproved()) {
            return false;
        }
        if (!event.isPrivate()) {
            return true;
        }
        if (myRegistration.isPresent()) {
            return true;
        }
        return userId != null && eventInvitationService.isInvited(event.getId(), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(final EventAccessData event, final User user) {
        return canView(event, userIdOf(user), isAdmin(user));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canView(final EventAccessData event, final Long userId, final boolean isAdmin) {
        // La regla real vive en isViewable(). Esto solo evita pedir la inscripcion propia cuando
        // no hace falta: para un evento publico, la regla nunca mira "myRegistration" (solo
        // importa para el caso privado), asi que pasarle Optional.empty() es siempre correcto.
        if (!event.isPrivate()) {
            return isViewable(event, userId, isAdmin, Optional.empty());
        }
        final Optional<Registration> myRegistration = userId == null
                ? Optional.empty()
                : registrationService.findByEventAndUserId(event.getId(), userId);
        return isViewable(event, userId, isAdmin, myRegistration);
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
    public Event getEditableEvent(final long eventId) {
        final Event event = eventService.findById(eventId).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.isCancelled()) {
            throw new EventCancelledException(eventId);
        }
        return event;
    }
}
