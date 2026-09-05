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

@Service
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private final RegistrationDao registrationDao;
    private final EventDao eventDao;
    private final UserService userService;

    @Autowired
    public RegistrationServiceImpl(final RegistrationDao registrationDao, final EventDao eventDao,
                                    final UserService userService) {
        this.registrationDao = registrationDao;
        this.eventDao = eventDao;
        this.userService = userService;
    }

    @Override
    @Transactional
    public RegistrationResult register(final long eventId, final String email, final String name) {
        // Normalizado: "Test@mail.com" y "test@mail.com" son la misma persona.
        final String normalizedEmail = email.trim().toLowerCase();

        final Registration existing = registrationDao.findByEventAndEmail(eventId, normalizedEmail).orElse(null);
        if (existing != null) {
            return RegistrationResult.ALREADY_REGISTERED;
        }

        // Bloquea la fila para que dos registros concurrentes no se pisen.
        final Event event = eventDao.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));

        // waitlist: pendiente para otro sprint
        if (isFull(event)) {
            throw new IllegalStateException("El evento ya esta lleno");
        }

        // Si viola el UNIQUE(event_id, email), se propaga: quien llama reintenta en otra transaccion.
        final Registration registration = new Registration(eventId, normalizedEmail, name, Registration.STATUS_CONFIRMED);
        registrationDao.create(registration);

        try {
            userService.findOrCreate(normalizedEmail, name);
        } catch (final RuntimeException e) {
            LOGGER.warn("No se pudo crear/actualizar el user para {}", normalizedEmail, e);
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
        return event.getMaxCapacity() != null
                && registrationDao.countConfirmed(event.getId()) >= event.getMaxCapacity();
    }
}
