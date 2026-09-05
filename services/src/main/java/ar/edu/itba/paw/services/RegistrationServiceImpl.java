// Implementa el contrato RegistrationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegistrationServiceImpl implements RegistrationService {

    /** El evento llego a su cupo: no se puede anotar nadie mas. */
    public static final String STATUS_FULL = "full";

    private final RegistrationDao registrationDao;
    private final EventDao eventDao;

    @Autowired
    public RegistrationServiceImpl(final RegistrationDao registrationDao, final EventDao eventDao) {
        this.registrationDao = registrationDao;
        this.eventDao = eventDao;
    }

    @Override
    public String register(final long eventId, final String email, final String name) {
        final Registration existing = registrationDao.findByEventAndEmail(eventId, email).orElse(null);
        if (existing != null) {
            return existing.getStatus();
        }

        final Event event = eventDao.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));

        // Chequeo defensivo: la pantalla ya esconde el formulario cuando el evento esta
        // lleno, pero alguien puede tener la pagina abierta desde antes de que se llenara.
        if (hasReachedCapacity(event)) {
            return STATUS_FULL;
        }

        final Registration registration =
                new Registration(eventId, email, name, Registration.STATUS_CONFIRMED);
        registrationDao.create(registration);
        return Registration.STATUS_CONFIRMED;
    }

    @Override
    public boolean isFull(final long eventId) {
        return eventDao.findById(eventId)
                .map(this::hasReachedCapacity)
                .orElse(false);
    }

    /**
     * Un evento sin cupo maximo nunca se llena. Con cupo, se llena cuando la cantidad
     * de inscriptos confirmados alcanza ese numero.
     */
    private boolean hasReachedCapacity(final Event event) {
        if (event.getMaxCapacity() == null) {
            return false;
        }
        return registrationDao.countConfirmed(event.getId()) >= event.getMaxCapacity();
    }
}
