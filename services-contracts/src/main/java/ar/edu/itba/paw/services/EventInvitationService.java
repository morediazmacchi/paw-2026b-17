// Contrato de servicio para invitar gente a eventos privados. Implementacion:
// EventInvitationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventInvitation;

import java.util.List;

public interface EventInvitationService {

    /**
     * El creador invita a alguien a su evento privado: genera un token y le
     * manda un mail con el link. No hace nada y devuelve NOT_ALLOWED si el
     * evento no existe, esta cancelado o ya paso, y EVENT_PUBLIC si es publico -- invitar solo
     * tiene sentido en un privado, un evento publico ya tiene un link que se puede
     * compartir sin necesitar esto. Que quien invita sea el creador NO se valida aca: lo decide
     * @PreAuthorize en el controller (ver EventSecurity.isOwner).
     */
    EventInvitationResult invite(long eventId, String inviteeEmail);

    /**
     * Si a este usuario se le mando una invitacion a este evento. Es lo que
     * deja ver (y anotarse a) un evento privado: el id es el del usuario
     * logueado (ver EventSecurity.canView / EventAccessService.canView). Por id y no por email: el
     * email de la invitacion puede haber quedado desactualizado si la
     * persona invitada cambio el suyo despues (ver linkUserId).
     */
    boolean isInvited(long eventId, long userId);

    /**
     * Pantalla "a quien invite" del creador. Que quien la pide sea el creador NO se
     * valida aca: lo decide @PreAuthorize en el controller (ver EventSecurity.isOwner).
     */
    List<EventInvitation> findByEvent(long eventId);

    /**
     * "Mis invitaciones": los eventos privados a los que invitaron a este
     * usuario, mas recientes primero, de a una pagina. No hace falta ningun
     * chequeo de dueño (a diferencia de findByEvent de arriba): es siempre tu
     * propio userId, el de la sesion logueada. Una pagina mayor a la ultima
     * devuelve la ultima, una menor a 1 la primera; sin invitaciones, la
     * pagina 1 vacia.
     */
    PageResult<EventInvitation> findPageByUser(long userId, int page);

    /** Para el badge de "Mis invitaciones" (ver ProfileController): sin traer las filas. */
    long countByUser(long userId);
}
