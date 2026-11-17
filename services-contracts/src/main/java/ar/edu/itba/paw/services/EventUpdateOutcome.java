// Resultado de EventService.update(): el evento ya guardado y si el guardado lo reenvio a
// revision (estaba en CHANGES_REQUESTED). Existe para poder testear esos hechos con un
// assertEquals sobre el valor de retorno, sin verify() sobre los mocks.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class EventUpdateOutcome {

    private final Event event;
    private final boolean resubmittedForReview;

    public EventUpdateOutcome(final Event event, final boolean resubmittedForReview) {
        this.event = event;
        this.resubmittedForReview = resubmittedForReview;
    }

    public Event getEvent() { return event; }
    // El evento estaba en CHANGES_REQUESTED y este guardado lo volvio a mandar a la cola de
    // aprobacion (PENDING). false en cualquier otro caso (evento APPROVED, PENDING, etc.).
    public boolean isResubmittedForReview() { return resubmittedForReview; }
}
