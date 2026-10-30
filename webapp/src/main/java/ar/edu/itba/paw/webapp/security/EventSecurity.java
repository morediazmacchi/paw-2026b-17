package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

// Reglas de autorizacion sobre eventos, para usar desde @PreAuthorize en los controllers:
// @PreAuthorize("@eventSecurity.isOwner(#id, authentication)").
// Solo responde si se puede o no; lo que pasa despues (cargar el evento, validar su estado) lo
// hace el service. Un evento que no existe da false, igual que uno ajeno: ErrorAdvice convierte
// los dos en el mismo 404, asi no se revela que un evento privado existe.
// El usuario sale del principal de la sesion (PlancitoUserDetails, armado una sola vez en el
// login): estas reglas no consultan al usuario en la base.
public class EventSecurity {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final EventAccessService eventAccessService;
    private final ImageService imageService;

    public EventSecurity(final EventService eventService, final RegistrationService registrationService,
                         final EventAccessService eventAccessService, final ImageService imageService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.eventAccessService = eventAccessService;
        this.imageService = imageService;
    }

    // El usuario logueado es el creador del evento.
    public boolean isOwner(final long eventId, final Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> eventService.findById(eventId).map(event -> isCreator(event, user)))
                .orElse(false);
    }

    // Igual que isOwner, pero ademas el evento tiene que ser privado: solo a esos se invita
    // (uno publico ya tiene un link que se puede compartir).
    public boolean isOwnerOfPrivate(final long eventId, final Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> eventService.findById(eventId)
                        .map(event -> event.isPrivate() && isCreator(event, user)))
                .orElse(false);
    }

    // El usuario logueado es el creador del evento Y la inscripcion es de ese evento. El
    // segundo chequeo evita usar un evento propio en la URL con la inscripcion de otro evento.
    public boolean isOwnerOfRegistrationEvent(final long eventId, final long registrationId,
                                              final Authentication authentication) {
        if (!isOwner(eventId, authentication)) {
            return false;
        }
        return registrationService.findById(registrationId)
                .map(Registration::getEventId)
                .map(registrationEventId -> registrationEventId == eventId)
                .orElse(false);
    }

    // La inscripcion existe y es del usuario logueado. Una inscripcion que no existe da false,
    // igual que una ajena: ErrorAdvice los convierte en el mismo 404 (no se revela cuales ids existen).
    public boolean isRegistrant(final long registrationId, final Authentication authentication) {
        return currentUser(authentication)
                .flatMap(user -> registrationService.findById(registrationId)
                        .map(registration -> Long.valueOf(user.getId()).equals(registration.getUserId())))
                .orElse(false);
    }

    // El usuario logueado puede ver el evento (ver EventAccessService.canView).
    public boolean canView(final long eventId, final Authentication authentication) {
        final Optional<PlancitoUserDetails> user = currentUser(authentication);
        if (!user.isPresent()) {
            return false;
        }
        return eventService.findById(eventId)
                .map(event -> eventAccessService.canView(event, user.get().getId(), isAdmin(user.get())))
                .orElse(false);
    }

    // La imagen hereda la visibilidad de su evento (ver EventAccessService.canView). A diferencia
    // de canView(eventId), un visitante sin sesion puede pasar: las imagenes de eventos publicos
    // las ve cualquiera (la ruta /image/** es permitAll). Decide con una consulta de cuatro
    // columnas (EventAccessSummary), sin cargar el Event.
    public boolean canViewImage(final long imageId, final Authentication authentication) {
        final Optional<EventAccessSummary> access = imageService.findOwnerEventAccess(imageId);
        if (!access.isPresent()) {
            // No existe, o nunca se asocio a un evento: nadie la ve (mismo 404 en los dos casos).
            return false;
        }
        // Sin usuario solo se ve lo publico y aprobado. Si ya da true, no hace falta mirar quien es
        // (con usuario nunca se ve menos que sin usuario).
        if (eventAccessService.canView(access.get(), null)) {
            return true;
        }
        return currentUser(authentication)
                .map(user -> eventAccessService.canView(access.get(), user.getId(), isAdmin(user)))
                .orElse(false);
    }

    // El usuario de la sesion: el principal que armo UserDetailsServiceImpl en el login. Si no hay
    // sesion, o el principal es otra cosa (un anonimo llega "autenticado" con un principal que es
    // solo un String), no hay usuario.
    private Optional<PlancitoUserDetails> currentUser(final Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        final Object principal = authentication.getPrincipal();
        return principal instanceof PlancitoUserDetails
                ? Optional.of((PlancitoUserDetails) principal)
                : Optional.empty();
    }

    private static boolean isAdmin(final PlancitoUserDetails user) {
        return user.getAuthorities().stream()
                .anyMatch(authority -> Role.ADMIN.getAuthority().equals(authority.getAuthority()));
    }

    private static boolean isCreator(final Event event, final PlancitoUserDetails user) {
        return Long.valueOf(user.getId()).equals(event.getCreatorId());
    }
}
