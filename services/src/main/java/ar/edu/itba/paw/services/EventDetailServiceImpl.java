// Implementa el contrato EventDetailService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventDetailServiceImpl implements EventDetailService {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final EventChatService eventChatService;
    private final ReviewService reviewService;
    private final EventSimilarityService eventSimilarityService;
    private final EventQuestionService eventQuestionService;
    private final EventAccessService eventAccessService;
    private final EventInvitationService eventInvitationService;

    @Autowired
    public EventDetailServiceImpl(final EventService eventService, final RegistrationService registrationService,
                                  final FavoriteService favoriteService, final EventChatService eventChatService,
                                  final ReviewService reviewService,
                                  final EventSimilarityService eventSimilarityService,
                                  final EventQuestionService eventQuestionService,
                                  final EventAccessService eventAccessService,
                                  final EventInvitationService eventInvitationService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.eventChatService = eventChatService;
        this.reviewService = reviewService;
        this.eventSimilarityService = eventSimilarityService;
        this.eventQuestionService = eventQuestionService;
        this.eventAccessService = eventAccessService;
        this.eventInvitationService = eventInvitationService;
    }

    @Override
    @Transactional(readOnly = true)
    public EventDetail getDetail(final long eventId, final Long viewerId, final int questionsPage,
                                 final int reviewsPage) {
        final Event event = eventService.findByIdOrThrow(eventId);
        // Quien fue invitado a un evento privado tiene su lugar reservado: para esa persona el evento no esta
        // "lleno" aunque el cupo ya este contado entero.
        final boolean full = viewerId != null
                ? registrationService.isFullFor(eventId, viewerId)
                : registrationService.isFull(eventId);
        final EventParticipation participation = viewerId == null ? null : participationOf(event, viewerId);
        final boolean favorite = viewerId != null && favoriteService.isFavorite(viewerId, eventId);
        final boolean chatMember = viewerId != null && eventChatService.isMember(eventId, viewerId);

        // "Eventos que te pueden interesar": findSimilar ya devuelve vacio si el evento es privado o no esta
        // aprobado. Es evento contra evento: no depende de quien mira, salvo que sus propios eventos no se sugieren.
        final List<Event> related = eventSimilarityService.findSimilar(eventId, viewerId);
        final List<Long> relatedIds = related.stream().map(Event::getId).collect(Collectors.toList());
        final boolean relatedForViewer = viewerId != null && !relatedIds.isEmpty();
        final Map<Long, RegistrationStatus> relatedStatuses = relatedForViewer
                ? registrationService.findStatusesByEventIds(viewerId, relatedIds)
                : Collections.emptyMap();
        // El corazon de cada card relacionada: una consulta acotada a esos ids (un IN), no todos sus favoritos.
        final Set<Long> relatedFavoriteIds = relatedForViewer
                ? favoriteService.findFavoriteEventIds(viewerId, relatedIds)
                : Collections.emptySet();

        // Una invitacion sin usar a un evento con aprobacion cambia el resultado de anotarse (queda confirmada): solo
        // hace falta saberlo en ese caso.
        final boolean invited = viewerId != null && event.isRequiresApproval()
                && eventInvitationService.isInvited(eventId, viewerId);
        final boolean owner = event.isCreatedBy(viewerId);
        return new EventDetail(event, full, registrationService.availableSpots(eventId), participation,
                actionOf(event, participation, full, invited), chatAccessOf(event, participation, chatMember),
                participation != null && participation.isRegistered() && event.isActive(),
                event.isOpenForQuestions() && !owner, owner, !owner, favorite, chatMember,
                reputationOf(event, reviewsPage), related, relatedStatuses, relatedFavoriteIds,
                eventQuestionService.findPage(eventId, questionsPage));
    }

    // La relacion de esta persona con el evento. Si puede anotarse lo dice EventAccessService.canRegister(): la
    // misma regla que usa la autorizacion de "Quiero ir" (EventSecurity.canRegister), en un solo lugar.
    private EventParticipation participationOf(final Event event, final long viewerId) {
        final RegistrationStatus status = registrationService.findByEventAndUserId(event.getId(), viewerId)
                .map(Registration::getStatus).orElse(null);
        return new EventParticipation(status, event.isCreatedBy(viewerId),
                eventAccessService.canRegister(event, viewerId));
    }

    // Que se le ofrece en el detalle, en orden de prioridad: la duenia gana sobre todo, despues quien solo puede
    // mirar (admin), despues el estado del evento (cancelado, finalizado), despues su inscripcion, y recien al
    // final inscribirse. Sin sesion (participation null) no hay duenia ni "solo mirar".
    private static EventAction actionOf(final Event event, final EventParticipation participation, final boolean full,
                                        final boolean invited) {
        if (participation != null) {
            if (participation.isOwner()) {
                if (event.isPendingApproval()) {
                    return EventAction.OWNER_PENDING;
                }
                if (event.isChangesRequested()) {
                    return EventAction.OWNER_CHANGES_REQUESTED;
                }
                return event.isRejected() ? EventAction.OWNER_REJECTED : EventAction.OWNER;
            }
            if (!participation.canRegister()) {
                return event.isApproved() ? EventAction.VIEW_ONLY : EventAction.ADMIN_REVIEW_ONLY;
            }
        }
        if (event.isCancelled()) {
            return event.isRemovedByAdmin() ? EventAction.CLOSED_REMOVED_BY_ADMIN : EventAction.CLOSED_CANCELLED;
        }
        if (event.isFinished()) {
            return EventAction.CLOSED_FINISHED;
        }
        if (participation != null && participation.isRejected()) {
            return EventAction.REGISTRATION_REJECTED;
        }
        if (participation != null && participation.isRegistered()) {
            if (participation.isWaitlisted()) {
                return EventAction.LEAVE_WAITLIST;
            }
            return participation.isPendingApproval() ? EventAction.CANCEL_PENDING : EventAction.UNREGISTER;
        }
        // Lo que va a pasar al anotarse lo decide la MISMA funcion que usa register() (Event.registrationStatusFor).
        switch (event.registrationStatusFor(full, invited)) {
            case WAITLISTED:
                return EventAction.JOIN_WAITLIST;
            case PENDING:
                return EventAction.REQUEST_APPROVAL;
            default:
                return EventAction.REGISTER;
        }
    }

    // Quien ya es miembro ve el acceso al chat aunque el evento haya terminado (queda de solo lectura); quien tiene
    // el lugar confirmado puede unirse solo mientras el evento este vigente (misma regla que EventChatService.join()).
    private static ChatAccess chatAccessOf(final Event event, final EventParticipation participation,
                                           final boolean chatMember) {
        if (chatMember) {
            return ChatAccess.OPEN;
        }
        return participation != null && participation.isChatConfirmed() && event.isActive()
                ? ChatAccess.JOIN : ChatAccess.NONE;
    }

    // Puntaje del promotor (todas sus reseñas de todos sus eventos) + una pagina de reseñas. Un evento viejo
    // puede no tener organizador cargado: en ese caso no hay reputacion que mostrar.
    private CreatorReputation reputationOf(final Event event, final int reviewsPage) {
        return event.getCreatorId() == null ? null
                : reviewService.findCreatorReputation(event.getCreatorId(), reviewsPage);
    }
}
