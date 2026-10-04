// Resultado de EventService.update(): el evento ya guardado, y cuantos de la lista de
// espera entraron (si subio el cupo). Existe para poder testear esos dos hechos con un
// assertEquals sobre el valor de retorno, sin verify() sobre los mocks de eventDao/registrationService.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class EventUpdateOutcome {

    private final Event event;
    private final int promotedFromWaitlist;

    public EventUpdateOutcome(final Event event, final int promotedFromWaitlist) {
        this.event = event;
        this.promotedFromWaitlist = promotedFromWaitlist;
    }

    public Event getEvent() { return event; }
    public int getPromotedFromWaitlist() { return promotedFromWaitlist; }
}
