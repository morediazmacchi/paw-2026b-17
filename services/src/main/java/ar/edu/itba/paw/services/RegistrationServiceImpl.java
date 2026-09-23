// Implementa el contrato RegistrationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private final RegistrationDao registrationDao;
    private final EventDao eventDao;
    private final UserService userService;
    private final NotificationService notificationService;
    private final WaitlistService waitlistService;

    @Autowired
    public RegistrationServiceImpl(final RegistrationDao registrationDao, final EventDao eventDao,
                                    final UserService userService,
                                    final NotificationService notificationService,
                                    final WaitlistService waitlistService) {
        this.registrationDao = registrationDao;
        this.eventDao = eventDao;
        this.userService = userService;
        this.notificationService = notificationService;
        this.waitlistService = waitlistService;
    }

    @Override
    @Transactional
    public RegistrationResult register(final long eventId, final String email, final String name) {
        // Normalizado: "Test@mail.com" y "test@mail.com" son la misma persona.
        final String normalizedEmail = email.trim().toLowerCase();

        final Registration existing = registrationDao.findByEventAndEmail(eventId, normalizedEmail).orElse(null);
        if (existing != null) {
            return alreadyExistsResult(existing.getStatus());
        }

        // Bloquea la fila para que dos registros concurrentes no se pisen.
        final Event event = eventDao.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));

        // El creador no se anota a su propio evento: no tiene sentido pedirse
        // aprobacion a si mismo en uno con requiresApproval, ni "invitarse" a
        // uno privado del que ya es dueño. Va antes de tocar la base: no se
        // crea ninguna fila ni sale ningun mail.
        if (normalizedEmail.equalsIgnoreCase(event.getCreatorEmail())) {
            return RegistrationResult.OWN_EVENT;
        }

        // Que el evento este lleno ya no es un error: la persona entra a la lista
        // de espera. El orden de la lista sale de registration.registered_at, que
        // la base completa sola, asi que no hay ninguna posicion que guardar.
        final boolean full = isFull(event);
        final String status = registrationStatus(event, full);

        // Si viola el UNIQUE(event_id, email), se propaga: quien llama reintenta en otra transaccion.
        final Registration registration = new Registration(eventId, normalizedEmail, name, status);
        registrationDao.create(registration);

        try {
            userService.findOrCreate(normalizedEmail, name);
        } catch (final RuntimeException e) {
            LOGGER.warn("No se pudo crear/actualizar el user para {}", normalizedEmail, e);
        }

        // Tres avisos posibles, siempre en otro hilo: uno a la persona y otro al
        // dueno del evento. Lo que cambia es el texto, no la logica. Sin este if,
        // a quien quedo en lista de espera (o pendiente de revision) le llegaria
        // un mail que dice "confirmamos tu lugar", que es justo lo que no paso.
        if (full) {
            notificationService.notifyWaitlistJoined(event, registration);
            notificationService.notifyNewWaitlistEntry(event, registration);
        } else if (Registration.STATUS_PENDING.equals(status)) {
            notificationService.notifyRegistrationPending(event, registration);
            notificationService.notifyNewPendingRegistration(event, registration);
        } else {
            notificationService.notifyRegistrationConfirmed(event, registration);
            notificationService.notifyNewRegistration(event, registration);

            // "Se lleno" es un CAMBIO de estado, no un estado: el aviso sale solo
            // en el pedido que ocupo el ultimo lugar. Se vuelve a preguntar DESPUES
            // del insert, porque recien ahora esta contado. Y va en esta rama
            // unicamente: si arriba ya estaba lleno, no hay nada que anunciar.
            if (isFull(event)) {
                notificationService.notifyEventFull(event);
            }
        }

        return registrationResult(status);
    }

    // Se distingue el caso porque el cartel no dice lo mismo: "ya estabas
    // anotado" (tenes lugar) vs "ya estas en la lista de espera" vs "todavia
    // estan revisando tu pedido" vs "ya te habian rechazado".
    private static RegistrationResult alreadyExistsResult(final String status) {
        if (Registration.STATUS_WAITLISTED.equals(status)) {
            return RegistrationResult.ALREADY_WAITLISTED;
        }
        if (Registration.STATUS_PENDING.equals(status)) {
            return RegistrationResult.ALREADY_PENDING;
        }
        if (Registration.STATUS_REJECTED.equals(status)) {
            return RegistrationResult.REJECTED;
        }
        return RegistrationResult.ALREADY_REGISTERED;
    }

    // Sin lugar -> lista de espera (gana siempre: no tiene sentido "pending"
    // sin cupo todavia). Con lugar y el evento pide confirmacion -> pending.
    // Con lugar y no pide confirmacion -> confirmed, como siempre.
    private static String registrationStatus(final Event event, final boolean full) {
        if (full) {
            return Registration.STATUS_WAITLISTED;
        }
        return event.isRequiresApproval() ? Registration.STATUS_PENDING : Registration.STATUS_CONFIRMED;
    }

    private static RegistrationResult registrationResult(final String status) {
        if (Registration.STATUS_WAITLISTED.equals(status)) {
            return RegistrationResult.WAITLISTED;
        }
        if (Registration.STATUS_PENDING.equals(status)) {
            return RegistrationResult.PENDING;
        }
        return RegistrationResult.CONFIRMED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFull(final long eventId) {
        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));
        return isFull(event);
    }

    private boolean isFull(final Event event) {
        return event.getMaxCapacity() != null && occupied(event.getId()) >= event.getMaxCapacity();
    }

    /**
     * Un cupo puede estar ocupado (alguien confirmado), RESERVADO por una
     * invitacion de lista de espera sin contestar, o RESERVADO porque el
     * creador todavia no reviso esa inscripcion (evento con requiresApproval).
     * Los tres cuentan como lleno: si solo mirramos los confirmados, mientras
     * alguien decide (la persona invitada, o el creador revisando pendientes)
     * otra se anotaria desde la pagina y terminariamos con mas gente que
     * lugares. Es debatible si "pending" deberia reservar cupo o no (el
     * creador podria preferir revisar sin esa presion) - queda para discutir
     * con el equipo, hoy se eligio el criterio mas conservador.
     */
    private long occupied(final long eventId) {
        return registrationDao.countConfirmed(eventId)
                + waitlistService.countReserved(eventId)
                + registrationDao.countPending(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public Integer availableSpots(final long eventId) {
        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));
        if (event.getMaxCapacity() == null) {
            return null;
        }
        return (int) Math.max(0, event.getMaxCapacity() - occupied(eventId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findByEmail(final String email) {
        return registrationDao.findByEmail(email);
    }

    @Override
    @Transactional
    public boolean cancel(final long registrationId, final String email) {
        final Optional<Registration> registration = registrationDao.findById(registrationId);
        if (!registration.isPresent() || !registration.get().getEmail().equalsIgnoreCase(email.trim())) {
            return false;
        }
        final Registration cancelled = registration.get();
        final long eventId = cancelled.getEventId();
        // Se mira ANTES de borrar: despues la fila ya no existe.
        final boolean wasConfirmed = Registration.STATUS_CONFIRMED.equals(cancelled.getStatus());

        registrationDao.delete(registrationId);
        // El cupo que se acaba de soltar se le ofrece enseguida al primero de la
        // lista de espera. Va en la misma transaccion: o pasan las dos cosas o
        // no pasa ninguna. Devuelve a cuantos invito, para poder contarselo al
        // organizador sin volver a consultar.
        final int invited = waitlistService.reconcile(eventId);

        // Solo se avisa si se libero un lugar de verdad. Que alguien salga de la
        // lista de espera no le cambia nada al organizador: el cupo disponible
        // era el mismo antes y despues.
        if (wasConfirmed) {
            eventDao.findById(eventId).ifPresent(event ->
                    notificationService.notifyRegistrationCancelled(event, cancelled, invited > 0));
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findByEventAndEmail(final long eventId, final String email) {
        return registrationDao.findByEventAndEmail(eventId, email.trim().toLowerCase());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findByEventId(final long eventId) {
        return registrationDao.findByEventId(eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Registration> findPendingByEvent(final long eventId, final String creatorEmail) {
        if (!ownsEvent(eventId, creatorEmail)) {
            return Collections.emptyList();
        }
        return registrationDao.findPendingByEvent(eventId);
    }

    @Override
    @Transactional
    public boolean approve(final long registrationId, final String creatorEmail) {
        final Registration registration = pendingOwnedBy(registrationId, creatorEmail).orElse(null);
        if (registration == null) {
            return false;
        }
        registrationDao.updateStatus(registrationId, Registration.STATUS_CONFIRMED);
        registration.setStatus(Registration.STATUS_CONFIRMED);
        eventDao.findById(registration.getEventId())
                .ifPresent(event -> notificationService.notifyRegistrationApproved(event, registration));
        return true;
    }

    @Override
    @Transactional
    public boolean reject(final long registrationId, final String creatorEmail) {
        final Registration registration = pendingOwnedBy(registrationId, creatorEmail).orElse(null);
        if (registration == null) {
            return false;
        }
        registrationDao.updateStatus(registrationId, Registration.STATUS_REJECTED);
        registration.setStatus(Registration.STATUS_REJECTED);
        final long eventId = registration.getEventId();
        eventDao.findById(eventId)
                .ifPresent(event -> notificationService.notifyRegistrationRejected(event, registration));
        // El cupo que esta inscripcion tenia reservado (ver occupied()) se
        // libera: se lo ofrecemos enseguida a quien siga en la lista de espera.
        waitlistService.reconcile(eventId);
        return true;
    }

    // Busca una inscripcion pendiente y confirma, en el mismo paso, que quien
    // pide aprobarla/rechazarla es el creador del evento al que pertenece.
    // Optional.empty() cubre los tres motivos de "no" (no existe, no esta
    // pending, o es de otro creador) sin distinguirlos: a quien llama no le
    // interesa por que no se pudo, solo que no se pudo.
    private Optional<Registration> pendingOwnedBy(final long registrationId, final String creatorEmail) {
        return registrationDao.findById(registrationId)
                .filter(r -> Registration.STATUS_PENDING.equals(r.getStatus()))
                .filter(r -> ownsEvent(r.getEventId(), creatorEmail));
    }

    private boolean ownsEvent(final long eventId, final String creatorEmail) {
        return eventDao.findById(eventId)
                .map(event -> event.getCreatorEmail().equalsIgnoreCase(creatorEmail.trim()))
                .orElse(false);
    }
}
