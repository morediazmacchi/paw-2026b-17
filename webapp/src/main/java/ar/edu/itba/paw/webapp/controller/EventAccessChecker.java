package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventInvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

// Un evento publico lo puede ver cualquiera, como siempre. Uno privado solo
// su creador, alguien que ya tenga una fila de inscripcion (en cualquier
// estado, incluido rechazado: si te rechazaron es porque en algun momento
// tuviste acceso, no tiene sentido que ahora te vuelvas a topar con un 404),
// o alguien cuya cuenta (la de su sesion) fue invitada. El link del mail solo
// dice que evento es: quien sos lo dice tu sesion.
// Lo comparten EventController, EventRegistrationController y
// EventQuestionController: por eso vive en un componente aparte y no como
// metodo privado de un solo controller.
@Component
public class EventAccessChecker {

    private final EventInvitationService eventInvitationService;

    @Autowired
    public EventAccessChecker(final EventInvitationService eventInvitationService) {
        this.eventInvitationService = eventInvitationService;
    }

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
