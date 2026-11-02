// Un evento de "Mis eventos" con los conteos de inscripciones que muestra la pantalla. Lo devuelve
// EventService.findMyEventsPage().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class EventWithRegistrationStats {

    private final Event event;
    private final long confirmedCount;
    private final Long pendingCount;

    public EventWithRegistrationStats(final Event event, final long confirmedCount, final Long pendingCount) {
        this.event = event;
        this.confirmedCount = confirmedCount;
        this.pendingCount = pendingCount;
    }

    public Event getEvent() { return event; }

    // Inscriptos confirmados (los que se enteran por mail si se cancela). 0 en un evento ya cancelado.
    public long getConfirmedCount() { return confirmedCount; }

    // Pedidos esperando que el organizador los acepte. null si el evento no pide aprobacion.
    public Long getPendingCount() { return pendingCount; }
}
