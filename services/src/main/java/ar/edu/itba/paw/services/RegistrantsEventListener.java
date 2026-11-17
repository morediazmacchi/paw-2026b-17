package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

// Lo que les pasa a los inscriptos, DENTRO de la transaccion, cuando el evento cambia o se da de baja. Vive aca y no en
// EventServiceImpl porque necesita RegistrationService, que depende de EventService: EventService anuncia el hecho
// (EventUpdatedEvent / EventCancelledEvent) y este listener, que nadie inyecta, reacciona. Corre en la misma
// transaccion de quien publica (listener comun, no @TransactionalEventListener) y hace solo lo que tiene que quedar
// junto con el cambio: el aviso de la bandeja (UNA sentencia INSERT ... SELECT, no se trae a nadie a memoria) y, al
// editar, hacer avanzar la lista de espera (cambia estados y necesita el lock del evento).
// Los MAILS a los inscriptos no salen de aca: los manda MassNotifier, que escucha los mismos anuncios en segundo
// plano, despues del commit, buscando a los destinatarios de a tandas.
@Component
public class RegistrantsEventListener {

    // A quienes les llega a la bandeja que el evento se cancelo: todos menos los rechazados.
    private static final Set<RegistrationStatus> CANCELLATION_NOTICE_STATUSES =
            EnumSet.complementOf(EnumSet.of(RegistrationStatus.REJECTED));

    private final RegistrationService registrationService;
    private final InboxService inboxService;

    @Autowired
    public RegistrantsEventListener(final RegistrationService registrationService, final InboxService inboxService) {
        this.registrationService = registrationService;
        this.inboxService = inboxService;
    }

    @EventListener
    public void onEventUpdated(final EventUpdatedEvent updated) {
        final long eventId = updated.getEventId();
        // Bandeja: solo a los confirmados, mismo criterio que la cancelacion (a alguien pendiente/rechazado/en lista de
        // espera todavia no tiene un lugar asegurado en base al cual le importe que cambio el evento).
        inboxService.addForRegistrants(eventId, InboxMessageType.EVENT_UPDATED, RegistrationStatus.withConfirmedPlace());
        // Si subio el cupo (o se lo saco), los primeros de la lista de espera entran (sus avisos: ver
        // RegistrationsMovedEvent). Despues del aviso de la bandeja: a los que entran ahora les llega en la bandeja
        // el de que estan confirmados, no ademas el de que el evento cambio. (El MAIL de "cambio el evento" si les
        // llega: MassNotifier lee a los confirmados despues del commit, y ellos ya lo son; como se anotaron antes
        // del cambio, esta bien que se enteren.)
        registrationService.promoteFromWaitlist(eventId);
    }

    // Bandeja: a los confirmados y tambien a quien estaba en lista de espera o esperando que lo acepten, que si no se
    // enteraria recien al ver el evento. Al rechazado no: ese ya no esperaba nada de este evento. Igual para una
    // cancelacion comun y para una baja de un admin (el creador, en cambio, recibe un aviso aparte con el motivo).
    @EventListener
    public void onEventCancelled(final EventCancelledEvent cancelled) {
        inboxService.addForRegistrants(cancelled.getEventId(), InboxMessageType.EVENT_CANCELLED,
                CANCELLATION_NOTICE_STATUSES);
    }
}
