// Resultado de EventService.update(): el evento ya guardado, cuantos de la lista de
// espera entraron (si subio el cupo), y si el guardado lo reenvio a revision (estaba
// en CHANGES_REQUESTED). Existe para poder testear esos hechos con un assertEquals
// sobre el valor de retorno, sin verify() sobre los mocks de eventDao/registrationService.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class EventUpdateOutcome {

    private final Event event;
    private final int promotedFromWaitlist;
    private final boolean resubmittedForReview;

    public EventUpdateOutcome(final Event event, final int promotedFromWaitlist, final boolean resubmittedForReview) {
        this.event = event;
        this.promotedFromWaitlist = promotedFromWaitlist;
        this.resubmittedForReview = resubmittedForReview;
    }

    public Event getEvent() { return event; }
    public int getPromotedFromWaitlist() { return promotedFromWaitlist; }
    // El evento estaba en CHANGES_REQUESTED y este guardado lo volvio a mandar a la cola de
    // aprobacion (PENDING). false en cualquier otro caso (evento APPROVED, PENDING, etc.).
    public boolean isResubmittedForReview() { return resubmittedForReview; }
}
