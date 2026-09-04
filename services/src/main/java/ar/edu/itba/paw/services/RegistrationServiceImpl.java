package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RegistrationServiceImpl implements RegistrationService {

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

        String status = Registration.STATUS_CONFIRMED;
        if (event.getMaxCapacity() != null) {
            final long confirmed = registrationDao.countConfirmed(eventId);
            if (confirmed >= event.getMaxCapacity()) {
                status = Registration.STATUS_WAITLISTED;
            }
        }

        final Registration registration = new Registration(eventId, email, name, status);
        registrationDao.create(registration);
        return status;
    }
}
