package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

// Lo que les pasa a los inscriptos cuando el evento cambia o se da de baja. Vive aca y no en EventServiceImpl porque
// necesita RegistrationService, que depende de EventService: EventService anuncia el hecho (EventUpdatedEvent /
// EventCancelledEvent) y este listener, que nadie inyecta, reacciona. Corre en la misma transaccion de quien publica
// (listener comun, no @TransactionalEventListener), en el mismo punto donde antes lo hacia EventServiceImpl.
@Component
public class RegistrantsEventListener {

    // A quienes les llega a la bandeja que el evento se cancelo: todos menos los rechazados.
    private static final Set<RegistrationStatus> CANCELLATION_NOTICE_STATUSES =
            EnumSet.complementOf(EnumSet.of(RegistrationStatus.REJECTED));

    private final RegistrationService registrationService;
    private final NotificationService notificationService;
    private final InboxService inboxService;

    @Autowired
    public RegistrantsEventListener(final RegistrationService registrationService,
                                    final NotificationService notificationService,
                                    final InboxService inboxService) {
        this.registrationService = registrationService;
        this.notificationService = notificationService;
        this.inboxService = inboxService;
    }

    @EventListener
    public void onEventUpdated(final EventUpdatedEvent updated) {
        final Event event = updated.getEvent();
        final long eventId = event.getId();
        // Solo a los confirmados, mismo criterio que la cancelacion: a alguien pendiente/rechazado/en lista de espera
        // todavia no tiene un lugar asegurado en base al cual le importe que cambio el evento. Los mails salen todos en
        // UNA tarea (en otro hilo, despues del commit); los avisos de la bandeja, todos juntos en una sola sentencia.
        notificationService.notifyEventUpdated(event, registrationService.findConfirmedByEventId(eventId));
        inboxService.addForRegistrants(eventId, InboxMessageType.EVENT_UPDATED, RegistrationStatus.withConfirmedPlace());
        // Si subio el cupo (o se lo saco), los primeros de la lista de espera entran. Despues del aviso de cambios: a los
        // que entran ahora les llega ya el mail de que estan confirmados, no hace falta ademas contarles que el evento cambio.
        registrationService.promoteFromWaitlist(eventId);
    }

    // Mail: solo a los confirmados (son los que tenian un lugar que pierden). Bandeja: tambien a quien estaba en lista
    // de espera o esperando que lo acepten, que si no se enteraria recien al ver el evento. Al rechazado no: ese ya no
    // esperaba nada de este evento. Igual para una cancelacion comun y para una baja de un admin: para quien esta anotado
    // el motivo no cambia el aviso que le corresponde (el creador, en cambio, recibe un aviso aparte con el motivo).
    @EventListener
    public void onEventCancelled(final EventCancelledEvent cancelled) {
        final Event event = cancelled.getEvent();
        notificationService.notifyEventCancelled(event, registrationService.findConfirmedByEventId(event.getId()));
        inboxService.addForRegistrants(event.getId(), InboxMessageType.EVENT_CANCELLED, CANCELLATION_NOTICE_STATUSES);
    }
}
