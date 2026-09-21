package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;

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

    /** Se creo un evento -> le avisa al creador. */
    void notifyEventCreated(Event event, String creatorName);

    /** Alguien se anoto -> le avisa a esa persona, con los datos del evento. */
    void notifyRegistrationConfirmed(Event event, Registration registration);

    /** Alguien se anoto -> le avisa al creador del evento. */
    void notifyNewRegistration(Event event, Registration registration);

    /** Se anoto pero el evento estaba lleno -> le avisa que quedo en lista de espera. */
    void notifyWaitlistJoined(Event event, Registration registration);

    /** Alguien entro a la lista de espera -> le avisa al creador del evento. */
    void notifyNewWaitlistEntry(Event event, Registration registration);

    /**
     * Se libero un lugar y le toca a esta persona -> mail con el link unico para
     * aceptarlo o rechazarlo, y hasta cuando tiene tiempo.
     */
    void notifyWaitlistInvitation(Event event, Registration registration, String token, java.util.Date expiresAt);

    /** Alguien se registro (o completo una cuenta fantasma) -> mail con el link para confirmar el mail. */
    void notifyAccountVerification(User user, String token);

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
}
