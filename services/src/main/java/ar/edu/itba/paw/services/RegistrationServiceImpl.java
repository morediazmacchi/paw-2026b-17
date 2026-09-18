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
            // Se distingue el caso porque el cartel no dice lo mismo: "ya estabas
            // anotado" (tenes lugar) vs "ya estas en la lista de espera" (no lo tenes).
            return Registration.STATUS_WAITLISTED.equals(existing.getStatus())
                    ? RegistrationResult.ALREADY_WAITLISTED
                    : RegistrationResult.ALREADY_REGISTERED;
        }

        // Bloquea la fila para que dos registros concurrentes no se pisen.
        final Event event = eventDao.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));

        // Que el evento este lleno ya no es un error: la persona entra a la lista
        // de espera. El orden de la lista sale de registration.registered_at, que
        // la base completa sola, asi que no hay ninguna posicion que guardar.
        final boolean full = isFull(event);
        final String status = full ? Registration.STATUS_WAITLISTED : Registration.STATUS_CONFIRMED;

        // Si viola el UNIQUE(event_id, email), se propaga: quien llama reintenta en otra transaccion.
        final Registration registration = new Registration(eventId, normalizedEmail, name, status);
        registrationDao.create(registration);

        try {
            userService.findOrCreate(normalizedEmail, name);
        } catch (final RuntimeException e) {
            LOGGER.warn("No se pudo crear/actualizar el user para {}", normalizedEmail, e);
        }

        // Dos avisos en los dos casos, siempre en otro hilo: uno a la persona y
        // otro al dueno del evento. Lo que cambia es el texto, no la logica. Sin
        // este if, a quien quedo en lista de espera le llegaria un mail que dice
        // "confirmamos tu lugar", que es justo lo que no paso.
        if (full) {
            notificationService.notifyWaitlistJoined(event, registration);
            notificationService.notifyNewWaitlistEntry(event, registration);
        } else {
            notificationService.notifyRegistrationConfirmed(event, registration);
            notificationService.notifyNewRegistration(event, registration);
        }

        return full ? RegistrationResult.WAITLISTED : RegistrationResult.CONFIRMED;
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
     * Un cupo puede estar ocupado (alguien confirmado) o RESERVADO (alguien con
     * una invitacion de lista de espera que todavia no contesto). Los dos cuentan
     * como lleno: si mirramos solo los confirmados, mientras la persona invitada
     * decide, otra se anotaria desde la pagina y terminariamos con mas gente que
     * lugares.
     */
    private long occupied(final long eventId) {
        return registrationDao.countConfirmed(eventId) + waitlistService.countReserved(eventId);
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
        final long eventId = registration.get().getEventId();
        registrationDao.delete(registrationId);
        // El cupo que se acaba de soltar se le ofrece enseguida al primero de la
        // lista de espera. Va en la misma transaccion: o pasan las dos cosas o
        // no pasa ninguna.
        waitlistService.reconcile(eventId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Registration> findByEventAndEmail(final long eventId, final String email) {
        return registrationDao.findByEventAndEmail(eventId, email.trim().toLowerCase());
    }
}
