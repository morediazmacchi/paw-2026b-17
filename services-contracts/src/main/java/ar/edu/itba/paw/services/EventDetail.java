// Todo lo que muestra el detalle de un evento, ya resuelto para quien lo mira. Lo devuelve
// EventDetailService.getDetail(). Lo que depende de la sesion (participation, favorite, chatMember) es
// null / false para un visitante anonimo.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.List;
import java.util.Map;

public class EventDetail {

    private final Event event;
    private final boolean full;
    private final Integer availableSpots;
    private final EventParticipation participation;
    private final EventAction action;
    private final ChatAccess chatAccess;
    private final boolean canUnregister;
    private final boolean canAsk;
    private final boolean canAnswer;
    private final boolean canReport;
    private final boolean favorite;
    private final boolean chatMember;
    private final CreatorReputation creatorReputation;
    private final List<Event> relatedEvents;
    private final Map<Long, RegistrationStatus> relatedStatuses;
    private final PageResult<EventQuestion> questions;

    public EventDetail(final Event event, final boolean full, final Integer availableSpots,
                       final EventParticipation participation, final EventAction action,
                       final ChatAccess chatAccess, final boolean canUnregister, final boolean canAsk,
                       final boolean canAnswer, final boolean canReport,
                       final boolean favorite, final boolean chatMember,
                       final CreatorReputation creatorReputation, final List<Event> relatedEvents,
                       final Map<Long, RegistrationStatus> relatedStatuses,
                       final PageResult<EventQuestion> questions) {
        this.event = event;
        this.full = full;
        this.availableSpots = availableSpots;
        this.participation = participation;
        this.action = action;
        this.chatAccess = chatAccess;
        this.canUnregister = canUnregister;
        this.canAsk = canAsk;
        this.canAnswer = canAnswer;
        this.canReport = canReport;
        this.favorite = favorite;
        this.chatMember = chatMember;
        this.creatorReputation = creatorReputation;
        this.relatedEvents = relatedEvents;
        this.relatedStatuses = relatedStatuses;
        this.questions = questions;
    }

    public Event getEvent() { return event; }

    // Lleno, desde el punto de vista de quien mira (un invitado a un privado tiene su lugar reservado).
    public boolean isFull() { return full; }

    // null si el evento no tiene limite de cupo.
    public Integer getAvailableSpots() { return availableSpots; }

    // null si es un visitante sin sesion.
    public EventParticipation getParticipation() { return participation; }

    // Que se le ofrece en el detalle (inscribirse, darse de baja, nada...). Siempre viene, tambien sin sesion.
    public EventAction getAction() { return action; }

    // Acceso al grupo de chat (abrirlo, unirse, nada).
    public ChatAccess getChatAccess() { return chatAccess; }

    // Esta anotada (en cualquier estado menos rechazada) y el evento sigue vigente: puede retirarse.
    public boolean isCanUnregister() { return canUnregister; }

    // Se le ofrece el formulario de preguntar: el evento sigue abierto a preguntas y no es su organizador (ver
    // QuestionResult.CLOSED / OWN_EVENT). Un visitante sin sesion tambien: la pantalla lo manda a iniciar sesion.
    public boolean isCanAsk() { return canAsk; }

    // Se le ofrece responder las preguntas: es el organizador del evento (ver EventSecurity.canAnswerQuestion).
    public boolean isCanAnswer() { return canAnswer; }

    // Se le ofrece reportar el evento: no es su organizador (ver ReportResult.OWN_EVENT).
    public boolean isCanReport() { return canReport; }
    public boolean isFavorite() { return favorite; }
    public boolean isChatMember() { return chatMember; }

    // null si el evento no tiene organizador cargado.
    public CreatorReputation getCreatorReputation() { return creatorReputation; }

    // Vacia si el evento es privado o no esta aprobado.
    public List<Event> getRelatedEvents() { return relatedEvents; }

    // Estado de la inscripcion propia en cada evento relacionado (vacio sin sesion).
    public Map<Long, RegistrationStatus> getRelatedStatuses() { return relatedStatuses; }
    public PageResult<EventQuestion> getQuestions() { return questions; }
}
