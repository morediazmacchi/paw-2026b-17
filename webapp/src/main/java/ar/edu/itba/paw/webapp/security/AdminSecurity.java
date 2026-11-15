package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.core.Authentication;

// Reglas de autorizacion del area de admin sobre OTROS usuarios (quien puede hacerle que a quien),
// para usar desde @PreAuthorize en AdminController:
// @PreAuthorize("@adminSecurity.canSuspend(#id, authentication)").
// Eso no lo puede expresar un antMatcher de WebAuthConfig: depende de QUIEN es el objetivo puntual,
// no solo del rol de quien actua. Un usuario que no existe da false, igual que uno no permitido.
public class AdminSecurity {

    private final UserService userService;
    private final EventService eventService;
    private final ModerationService moderationService;

    public AdminSecurity(final UserService userService, final EventService eventService,
                         final ModerationService moderationService) {
        this.userService = userService;
        this.eventService = eventService;
        this.moderationService = moderationService;
    }

    // El admin puede suspender a ese usuario: existe, NO es el mismo admin, y si es admin o superadmin
    // solo lo suspende un superadmin.
    public boolean canSuspend(final long targetId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(caller -> userService.findAccessById(targetId).map(target ->
                        caller.getId() != targetId && target.canBeSuspendedBy(caller.hasRole(Role.SUPERADMIN))))
                .orElse(false);
    }

    // El superadmin puede sacarle el rol admin a ese usuario: existe y NO es el mismo (quedaria afuera de
    // /admin/** sin forma de deshacerlo). Que sea superadmin quien lo pide lo decide WebAuthConfig; que el
    // objetivo no sea otro superadmin es regla de negocio del service (RevokeAdminResult.TARGET_SUPERADMIN).
    public boolean canRevokeAdmin(final long targetId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(caller -> userService.findAccessById(targetId).map(target -> caller.getId() != targetId))
                .orElse(false);
    }

    // El admin puede aprobar o rechazar este evento: existe y NO es suyo. Conflicto de intereses: el
    // creador no puede ser tambien quien lo revisa. El admin tiene el permiso; lo que se restringe es
    // sobre que evento puede actuar (el ejemplo de la clase, "editas solo las propias", al reves).
    public boolean canModerateEvent(final long eventId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(admin -> eventService.findAccessById(eventId)
                        .map(event -> event.canBeModeratedBy(admin.getId())))
                .orElse(false);
    }

    // El admin puede resolver este reporte (descartar, pedir cambios, dar de baja): el reporte existe
    // y su evento NO es del admin. Solo se mira de quien es el evento, NO el estado del reporte: uno
    // ya resuelto por otro admin igual llega al service, que responde con su cartel ("ya estaba
    // resuelto"); convertirlo en una denegacion seria incorrecto. Un id que no existe da false.
    public boolean canResolveReport(final long reportId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(admin -> moderationService.findReportTarget(reportId)
                        .map(target -> target.canBeModeratedBy(admin.getId())))
                .orElse(false);
    }
}
