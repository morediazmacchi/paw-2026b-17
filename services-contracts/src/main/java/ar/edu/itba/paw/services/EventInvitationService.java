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
    EventInvitationResult invite(long eventId, long creatorId, String inviteeEmail);

    /**
     * Si a este usuario se le mando una invitacion a este evento. Es lo que
     * deja ver (y anotarse a) un evento privado: el id es el del usuario
     * logueado (ver EventController.canView). Por id y no por email: el
     * email de la invitacion puede haber quedado desactualizado si la
     * persona invitada cambio el suyo despues (ver linkUserId).
     */
    boolean isInvited(long eventId, long userId);

    /**
     * Pantalla "a quien invite" del creador. Vacia si el evento no existe o
     * no es de ese creador (no revela cual de las dos cosas paso).
     */
    List<EventInvitation> findByEvent(long eventId, long creatorId);

    /**
     * "Mis invitaciones": los eventos privados a los que invitaron a este
     * usuario, mas recientes primero. No hace falta ningun chequeo de dueño
     * (a diferencia de findByEvent de arriba): es siempre tu propio userId,
     * el de la sesion logueada.
     */
    List<EventInvitation> findByUser(long userId);

    /** Para el badge de "Mis invitaciones" (ver ProfileController): sin traer las filas. */
    long countByUser(long userId);
}
