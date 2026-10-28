package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

// Reglas de autorizacion sobre eventos, para usar desde @PreAuthorize en los controllers:
// @PreAuthorize("@eventSecurity.isOwner(#id, authentication)").
// Solo responde si se puede o no; lo que pasa despues (cargar el evento, validar su estado) lo
// hace el service. Un evento que no existe da false, igual que uno ajeno: ErrorAdvice convierte
// los dos en el mismo 404, asi no se revela que un evento privado existe.
public class EventSecurity {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final EventAccessService eventAccessService;
    private final ImageService imageService;
    private final UserService userService;

    public EventSecurity(final EventService eventService, final RegistrationService registrationService,
                         final EventAccessService eventAccessService, final ImageService imageService,
                         final UserService userService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.eventAccessService = eventAccessService;
        this.imageService = imageService;
        this.userService = userService;
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

    // El usuario logueado puede ver el evento (ver EventAccessService.canView).
    public boolean canView(final long eventId, final Authentication authentication) {
        final Optional<User> user = currentUser(authentication);
        if (!user.isPresent()) {
            return false;
        }
        return eventService.findById(eventId)
                .map(event -> eventAccessService.canView(event, user.get()))
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
        // Sin usuario solo se ve lo publico y aprobado. Si ya da true, se ahorra buscar al usuario
        // (con usuario nunca se ve menos que sin usuario).
        if (eventAccessService.canView(access.get(), null)) {
            return true;
        }
        return currentUser(authentication)
                .map(user -> eventAccessService.canView(access.get(), user))
                .orElse(false);
    }

    private Optional<User> currentUser(final Authentication authentication) {
        // Un anonimo tambien llega "autenticado" (AnonymousAuthenticationToken): no es un usuario.
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userService.findByEmail(authentication.getName());
    }

    private static boolean isCreator(final Event event, final User user) {
        return Long.valueOf(user.getId()).equals(event.getCreatorId());
    }
}
