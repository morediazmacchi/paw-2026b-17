// Lo que muestran las pantallas "Ver inscriptos" (la del organizador y la del admin): el evento y una pagina de sus
// inscripciones. Lo devuelve RegistrationService.findRegistrants().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;

public class EventRegistrants {

    private final Event event;
    private final PageResult<Registration> registrations;

    public EventRegistrants(final Event event, final PageResult<Registration> registrations) {
        this.event = event;
        this.registrations = registrations;
    }

    public Event getEvent() { return event; }

    public PageResult<Registration> getRegistrations() { return registrations; }
}
