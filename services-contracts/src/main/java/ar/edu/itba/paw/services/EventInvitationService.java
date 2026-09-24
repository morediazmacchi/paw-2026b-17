// Contrato de servicio para invitar gente a eventos privados. Implementacion:
// EventInvitationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventInvitation;

import java.util.List;

public interface EventInvitationService {

    /**
     * El creador invita a alguien a su evento privado: genera un token y le
     * manda un mail con el link. No hace nada (y devuelve NOT_ALLOWED) si el
     * evento no existe, no es de ese creador, o no es privado -- invitar solo
     * tiene sentido ahi, un evento publico ya tiene un link que se puede
     * compartir sin necesitar esto.
     */
    EventInvitationResult invite(long eventId, String creatorEmail, String inviteeEmail);

    /**
     * Si a este email se le mando una invitacion a este evento. Es lo que deja
     * ver (y anotarse a) un evento privado: el email es el del usuario logueado
     * (ver EventController.canView).
     */
    boolean isInvited(long eventId, String email);

    /**
     * Pantalla "a quien invite" del creador. Vacia si el evento no existe o
     * no es de ese email (no revela cual de las dos cosas paso).
     */
    List<EventInvitation> findByEvent(long eventId, String creatorEmail);
}
