package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;

import java.util.List;

/**
 * Avisos que la aplicacion manda cuando pasa algo. Hoy son mails, pero quien
 * llama a esta interfaz no lo sabe: pide "avisale al creador que su evento se
 * publico", no "mandale un mail con tal plantilla".
 *
 * Por eso EventServiceImpl y RegistrationServiceImpl dependen de esto y no de
 * MailService directamente: el dia que se sumen notificaciones push o un SMS,
 * esas clases no se tocan.
 *
 * Todos los metodos son @Async en la implementacion: devuelven enseguida y el
 * mail se manda en otro hilo. El usuario no espera a que conteste Gmail.
 */
public interface NotificationService {

    /**
     * Se creo un evento -> le avisa al creador. Todo evento nuevo arranca
     * PENDING (ver EventApprovalStatus), asi que este aviso es siempre "lo
     * recibimos, esta en revision" -- nunca "ya esta publicado": ese otro
     * aviso es notifyEventApproved(), y llega recien cuando un admin lo revise.
     */
    void notifyEventPendingApproval(Event event, String creatorName);

    /** Alguien se anoto -> le avisa a esa persona, con los datos del evento. */
    void notifyRegistrationConfirmed(Event event, Registration registration);

    /** Alguien se anoto -> le avisa al creador del evento. */
    void notifyNewRegistration(Event event, Registration registration);

    /** Se anoto pero el evento estaba lleno -> le avisa que quedo en lista de espera. */
    void notifyWaitlistJoined(Event event, Registration registration);

    /**
     * Varios pasaron a la lista de espera a la vez (el evento se lleno y los pendientes ya no tienen lugar) ->
     * le avisa a cada uno. Un solo envio asincronico para toda la lista (ver notifyEventCancelled()).
     */
    void notifyWaitlistJoined(Event event, List<Registration> registrations);

    /** Alguien entro a la lista de espera -> le avisa al creador del evento. */
    void notifyNewWaitlistEntry(Event event, Registration registration);

    /**
     * Se ocupo el ultimo lugar -> le avisa al creador que su evento esta completo.
     * Se manda una sola vez, en el pedido que lo lleno: no es un estado que se
     * recuerde, es el momento en que se cruzo el limite.
     */
    void notifyEventFull(Event event);

    /**
     * Alguien que tenia lugar confirmado se dio de baja -> le avisa al creador.
     * Solo para inscripciones confirmadas: que alguien salga de la lista de
     * espera no le cambia el cupo al organizador.
     *
     * @param offeredToWaitlist true si ese lugar ya se lo llevo alguien de la
     *                          lista de espera, false si quedo abierto
     */
    void notifyRegistrationCancelled(Event event, Registration registration, boolean offeredToWaitlist);

    /** Se libero un lugar y le tocaba a esta persona -> mail de que ya esta confirmada. */
    void notifyWaitlistPromoted(Event event, Registration registration);

    /** El creador le saco el lugar a alguien ya confirmado -> le avisa a esa persona. */
    void notifyRegistrationRevoked(Event event, Registration registration);

    /**
     * Alguien se registro (o completo una cuenta fantasma) -> mail con el link para
     * confirmar el mail. redirectAfterConfirm (puede ser null) viaja en el link.
     */
    void notifyAccountVerification(User user, String token, String redirectAfterConfirm);

    /** Alguien pidio "olvide mi contraseña" -> mail con el link para elegir una nueva. */
    void notifyPasswordReset(User user, String token);

    /** Se anoto a un evento con requiresApproval -> le avisa que su pedido queda esperando revision. */
    void notifyRegistrationPending(Event event, Registration registration);

    /** Alguien pidio anotarse a un evento con requiresApproval -> le avisa al creador que tiene que revisarlo. */
    void notifyNewPendingRegistration(Event event, Registration registration);

    /** El creador acepto una inscripcion pendiente -> le avisa a esa persona que ya tiene su lugar. */
    void notifyRegistrationApproved(Event event, Registration registration);

    /** El creador rechazo una inscripcion pendiente -> le avisa a esa persona. */
    void notifyRegistrationRejected(Event event, Registration registration);

    /** El creador invito a alguien a un evento privado -> mail con el link para verlo y anotarse. */
    void notifyEventInvitation(Event event, EventInvitation invitation);

    /**
     * El creador cancelo el evento -> le avisa a cada inscripto confirmado de la lista. Es UN solo envio
     * asincronico para toda la lista (no uno por persona): asi un evento con muchos inscriptos no llena el
     * pool de mails ni deja a quien cancela esperando, y los idiomas se buscan todos juntos.
     */
    void notifyEventCancelled(Event event, List<Registration> registrations);

    /**
     * El creador edito el evento -> le avisa a cada inscripto confirmado de la lista (un solo envio
     * asincronico para todos, ver notifyEventCancelled()).
     * "event" son los datos YA actualizados (lo que cambio es justo lo que
     * el mail tiene que mostrar).
     */
    void notifyEventUpdated(Event event, List<Registration> registrations);

    /**
     * El evento empieza en menos de 24 hs -> recordatorio a cada inscripto confirmado de la lista (un solo
     * envio asincronico para todos, ver notifyEventCancelled()).
     */
    void notifyEventReminder(Event event, List<Registration> registrations);

    /** El evento empieza en menos de 24 hs -> recordatorio a su organizador. */
    void notifyOrganizerEventReminder(Event event);

    /**
     * Alguien pregunto en el evento -> mail al organizador. El nombre y el mail de quien pregunto
     * llegan ya resueltos (no se leen de la pregunta): el envio es @Async, otro hilo y sin sesion.
     */
    void notifyNewQuestion(Event event, EventQuestion question, String askerName, String askerEmail);

    /**
     * El organizador respondio -> mail a quien pregunto. El nombre y el mail de quien pregunto llegan
     * ya resueltos (no se leen de la pregunta): el envio es @Async, otro hilo y sin sesion.
     */
    void notifyQuestionAnswered(Event event, EventQuestion question, String askerName, String askerEmail);

    /** Un admin dio de baja el evento (tras revisar un reporte) -> le avisa al creador. */
    void notifyEventRemovedByAdmin(Event event, String reason);

    /** Un admin aprobo el evento recien creado (ver EventApprovalStatus) -> le avisa al creador. */
    void notifyEventApproved(Event event);

    /** Un admin rechazo el evento recien creado -> le avisa al creador con el motivo. */
    void notifyEventRejected(Event event, String reason);

    /**
     * Se aprobaron newEventsCount eventos nuevos que cumplen la busqueda guardada savedSearchId de user -> le avisa,
     * mostrando example (uno de ellos) y con el link para desuscribirse de esa busqueda.
     */
    void notifySavedSearchMatches(User user, long savedSearchId, Event example, long newEventsCount);

    /** Un admin le pidio al creador que corrija algo del evento -> le avisa con el mensaje. */
    void notifyEventChangesRequested(Event event, String message);

    /**
     * Un organizador que sigue (ver FollowService) publico un evento nuevo y quedo aprobado -> le avisa a cada
     * persona de la lista que lo sigue. Un solo envio asincronico para toda la lista (ver notifyEventCancelled()):
     * un organizador con muchos seguidores no deja esperando al admin que aprueba.
     */
    void notifyFollowedUserNewEvent(List<FollowerContact> followers, Event event, String organizerName);

    /** Un admin suspendio la cuenta -> le avisa a esa persona. */
    void notifyUserSuspended(User user);

    /** Un admin reactivo la cuenta -> le avisa a esa persona. */
    void notifyUserReactivated(User user);
}
