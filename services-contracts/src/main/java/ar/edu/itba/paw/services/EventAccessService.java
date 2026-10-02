// Contrato de servicio para EventAccessService. Implementacion: EventAccessServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;

import java.util.Optional;

public interface EventAccessService {

    /**
     * Un evento publico lo puede ver cualquiera, como siempre. Uno privado solo
     * su creador, alguien que ya tenga una fila de inscripcion (en cualquier
     * estado, incluido rechazado: si te rechazaron es porque en algun momento
     * tuviste acceso, no tiene sentido que ahora te vuelvas a topar con un 404),
     * o alguien cuya cuenta (la de su sesion) fue invitada. El link del mail solo
     * dice que evento es: quien sos lo dice tu sesion.
     * Lo comparten EventController, EventRegistrationController, EventQuestionController,
     * ProfileController e ImageController: por eso vive en un service aparte y no
     * como metodo privado de un solo controller.
     */
    boolean canView(Event event, User user, Optional<Registration> myRegistration);
}
