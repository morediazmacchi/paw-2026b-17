package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.EventAccessData;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.HasCreator;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
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
    private final EventChatService eventChatService;
    private final EventQuestionService eventQuestionService;

    public EventSecurity(final EventService eventService, final RegistrationService registrationService,
                         final EventAccessService eventAccessService, final ImageService imageService,
                         final EventChatService eventChatService,
                         final EventQuestionService eventQuestionService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.eventAccessService = eventAccessService;
        this.imageService = imageService;
        this.eventChatService = eventChatService;
        this.eventQuestionService = eventQuestionService;
    }

    // El usuario logueado es el creador del evento.
    public boolean isOwner(final long eventId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(user -> eventService.findAccessById(eventId).map(event -> isCreator(event, user)))
                .orElse(false);
    }

    // El usuario logueado es el organizador del evento AL QUE PERTENECE la pregunta. Se decide solo con
    // el id de la pregunta (el id del evento de la URL es solo ruteo y no se usa para autorizar). Una
    // pregunta que no existe da false, igual que una de un evento ajeno. Lectura acotada: solo el id
    // del organizador.
    public boolean canAnswerQuestion(final long questionId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(user -> eventQuestionService.findEventCreatorId(questionId)
                        .map(creatorId -> HasCreator.isCreator(user.getId(), creatorId)))
                .orElse(false);
    }

    // El usuario logueado es el creador del evento Y la inscripcion es de ese evento. El
    // segundo chequeo evita usar un evento propio en la URL con la inscripcion de otro evento.
    public boolean isOwnerOfRegistrationEvent(final long eventId, final long registrationId,
                                              final Authentication authentication) {
        if (!isOwner(eventId, authentication)) {
            return false;
        }
        return registrationService.findAccessById(registrationId)
                .map(RegistrationAccess::getEventId)
                .map(registrationEventId -> registrationEventId == eventId)
                .orElse(false);
    }

    // La inscripcion existe y es del usuario logueado. Una inscripcion que no existe da false,
    // igual que una ajena: ErrorAdvice los convierte en el mismo 404 (no se revela cuales ids existen).
    public boolean isRegistrant(final long registrationId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(user -> registrationService.findAccessById(registrationId)
                        .map(registration -> Long.valueOf(user.getId()).equals(registration.getUserId())))
                .orElse(false);
    }

    // Se puede ver el evento (ver EventAccessService.canView). Un visitante sin sesion tambien
    // puede: ve los eventos publicos y aprobados (el detalle es permitAll). Un evento que no
    // existe da false, igual que uno que no se puede ver.
    public boolean canView(final long eventId, final Authentication authentication) {
        final Optional<PlancitoUserDetails> user = PlancitoUserDetails.from(authentication);
        final Long userId = user.map(PlancitoUserDetails::getId).orElse(null);
        final boolean admin = user.map(u -> u.hasRole(Role.ADMIN)).orElse(false);
        return eventService.findAccessById(eventId)
                .map(event -> eventAccessService.canView(event, userId, admin))
                .orElse(false);
    }

    // La persona se puede anotar al evento: igual que canView pero SIN la excepcion de admin. Un
    // admin puede VER un evento privado o pendiente para moderarlo, pero no inscribirse; si lo
    // invitaron a un privado, la invitacion cuenta como a cualquiera. Un evento que no existe da
    // false. Hace falta sesion (la URL ya la exige).
    public boolean canRegister(final long eventId, final Authentication authentication) {
        final Optional<PlancitoUserDetails> user = PlancitoUserDetails.from(authentication);
        if (!user.isPresent()) {
            return false;
        }
        // La regla vive en EventAccessService.canRegister(): la misma que usa el detalle para decidir que ofrecer.
        return eventService.findAccessById(eventId)
                .map(event -> eventAccessService.canRegister(event, user.get().getId()))
                .orElse(false);
    }

    // La persona tiene su lugar CONFIRMADO en el evento (no pendiente, ni en espera, ni rechazado):
    // es lo que hace falta para unirse al grupo de chat. Sin inscripcion, o sin sesion, da false;
    // tampoco el organizador (no se anota a su propio evento).
    public boolean isConfirmedAttendee(final long eventId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(user -> registrationService.findAccessByEventAndUserId(eventId, user.getId()))
                .map(registration -> registration.getStatus().isConfirmed())
                .orElse(false);
    }

    // La persona es miembro activo del grupo de chat del evento (la pantalla del chat, la consulta
    // de mensajes y el envio son solo para miembros). Sin sesion o sin ser miembro da false.
    public boolean isChatMember(final long eventId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .map(user -> eventChatService.isMember(eventId, user.getId()))
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
        return PlancitoUserDetails.from(authentication)
                .map(user -> eventAccessService.canView(access.get(), user.getId(), user.hasRole(Role.ADMIN)))
                .orElse(false);
    }

    private static boolean isCreator(final EventAccessData event, final PlancitoUserDetails user) {
        return event.isCreatedBy(user.getId());
    }
}
