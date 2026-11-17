package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

// Los avisos por mail a MUCHAS personas (los inscriptos de un evento, los seguidores de un organizador, los que se
// movieron de la lista de espera), completos en segundo plano y despues del commit.
//
// Como funciona: quien cambia algo (EventService, RegistrationService) publica un anuncio con ids, dentro de su
// transaccion, y sigue. Los metodos on*() de aca son @Async: el executor (AfterCommitTaskExecutor) los larga recien
// cuando esa transaccion hace commit (si hace rollback, no corren). Ya en el otro hilo, cada uno:
// 1. vuelve a leer el evento (ya guardado);
// 2. busca a los destinatarios de a TANDAS de BATCH_SIZE (cada tanda es una consulta corta, en su propia transaccion
//    de solo lectura; la siguiente arranca despues del ultimo id de la anterior), nunca todos de golpe;
// 3. manda los mails de esa tanda en este mismo hilo (los notify* de listas de NotificationService no son @Async:
//    no se abre otra tarea por tanda).
// En el hilo de la operacion original no se busca ni se recorre a nadie.
//
// No es un service: nadie lo inyecta (todos le hablan con anuncios), asi que no arma ciclos aunque dependa de
// EventService y RegistrationService.
//
// Los notify*() sin @Async (los que devuelven a cuantos se les aviso) hacen el trabajo y se pueden testear llamandolos
// directo; los on*() son solo la entrada asincronica.
@Component
public class MassNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(MassNotifier.class);

    // De a cuantos destinatarios se busca y se manda por vez.
    static final int BATCH_SIZE = 100;

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FollowService followService;
    private final NotificationService notificationService;

    @Autowired
    public MassNotifier(final EventService eventService, final RegistrationService registrationService,
                        final FollowService followService, final NotificationService notificationService) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.followService = followService;
        this.notificationService = notificationService;
    }

    // ---- Entradas asincronicas (corren en otro hilo, despues del commit de quien publico) ----

    @Async
    @EventListener
    public void onEventUpdated(final EventUpdatedEvent updated) {
        notifyEventUpdated(updated.getEventId());
    }

    @Async
    @EventListener
    public void onEventCancelled(final EventCancelledEvent cancelled) {
        notifyEventCancelled(cancelled.getEventId());
    }

    @Async
    @EventListener
    public void onEventApproved(final EventApprovedEvent approved) {
        notifyFollowersOfNewEvent(approved.getEventId(), approved.getOrganizerId(), approved.getOrganizerName());
    }

    @Async
    @EventListener
    public void onRegistrationsMoved(final RegistrationsMovedEvent moved) {
        notifyRegistrationsMoved(moved.getEventId(), moved.getRegistrationIds(), moved.getNewStatus());
    }

    // El recordatorio de 24 hs a los confirmados (lo anuncia EventReminderServiceImpl.sendReminder(), que corre en el
    // scheduler).
    @Async
    @EventListener
    public void onEventReminderDue(final EventReminderDueEvent due) {
        notifyEventReminder(due.getEventId());
    }

    // ---- El trabajo (sincronico; devuelve a cuantos se les aviso) ----

    // A los confirmados: el evento cambio (los datos nuevos se leen ya guardados).
    public int notifyEventUpdated(final long eventId) {
        return notifyConfirmed(eventId, notificationService::notifyEventUpdated, "evento editado");
    }

    // A los confirmados: el evento se cancelo (o lo saco un admin).
    public int notifyEventCancelled(final long eventId) {
        return notifyConfirmed(eventId, notificationService::notifyEventCancelled, "evento cancelado");
    }

    // A los confirmados: el evento es en menos de 24 hs.
    public int notifyEventReminder(final long eventId) {
        return notifyConfirmed(eventId, notificationService::notifyEventReminder, "recordatorio de 24 hs");
    }

    // A los seguidores del organizador: publico un evento nuevo.
    public int notifyFollowersOfNewEvent(final long eventId, final long organizerId, final String organizerName) {
        final Optional<Event> event = eventService.findById(eventId);
        if (!event.isPresent()) {
            return 0;
        }
        int notified = 0;
        long lastId = 0;
        List<FollowerContact> batch;
        do {
            batch = followService.findFollowersAfter(organizerId, lastId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            final List<FollowerContact> current = batch;
            sendSafely(() -> notificationService.notifyFollowedUserNewEvent(current, event.get(), organizerName),
                    "evento nuevo de un organizador seguido", eventId);
            notified += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
        } while (batch.size() == BATCH_SIZE);
        return notified;
    }

    // A los que cambiaron de estado de golpe (ver RegistrationsMovedEvent). Los ids ya los tiene el anuncio: se cargan
    // de a una tanda de ids por vez.
    public int notifyRegistrationsMoved(final long eventId, final List<Long> registrationIds,
                                        final RegistrationStatus newStatus) {
        final Optional<Event> event = eventService.findById(eventId);
        if (!event.isPresent() || registrationIds.isEmpty()) {
            return 0;
        }
        // Que mails corresponden segun a que estado pasaron. REJECTED no se mueve de golpe: no hay aviso masivo.
        final BiConsumer<Event, List<Registration>> send;
        switch (newStatus) {
            case WAITLISTED:
                send = notificationService::notifyWaitlistJoined;
                break;
            case PENDING:
                send = (e, batch) -> {
                    notificationService.notifyRegistrationPending(e, batch);
                    notificationService.notifyNewPendingRegistration(e, batch);
                };
                break;
            case CONFIRMED:
                send = notificationService::notifyWaitlistPromoted;
                break;
            default:
                return 0;
        }
        int notified = 0;
        for (int from = 0; from < registrationIds.size(); from += BATCH_SIZE) {
            final List<Long> ids = registrationIds.subList(from, Math.min(from + BATCH_SIZE, registrationIds.size()));
            final List<Registration> batch = registrationService.findByIds(ids);
            sendSafely(() -> send.accept(event.get(), batch), "inscripciones que cambiaron a " + newStatus, eventId);
            notified += batch.size();
        }
        return notified;
    }

    // Recorre de a tandas a los que tienen el lugar confirmado y le pasa cada tanda a "send".
    private int notifyConfirmed(final long eventId, final BiConsumer<Event, List<Registration>> send,
                                final String what) {
        final Optional<Event> event = eventService.findById(eventId);
        if (!event.isPresent()) {
            return 0;
        }
        int notified = 0;
        long lastId = 0;
        List<Registration> batch;
        do {
            batch = registrationService.findConfirmedByEventIdAfter(eventId, lastId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            final List<Registration> current = batch;
            sendSafely(() -> send.accept(event.get(), current), what, eventId);
            notified += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
        } while (batch.size() == BATCH_SIZE);
        return notified;
    }

    // Una tanda que falla (por ejemplo, no se pudieron buscar los idiomas) no frena a las demas: es un hilo sin nadie
    // esperando, si no se loguea aca no se ve. (Los mails de cada persona ya tienen su propio try/catch en
    // NotificationServiceImpl.)
    private static void sendSafely(final Runnable send, final String what, final long eventId) {
        try {
            send.run();
        } catch (final RuntimeException e) {
            LOGGER.error("Fallo una tanda del aviso masivo ({}), evento {}", what, eventId, e);
        }
    }
}
