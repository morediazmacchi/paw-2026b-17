// Los pedidos pendientes de un evento, con el evento al que pertenecen. Lo devuelve
// MyAccountService.findPendingRequests().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class PendingRequestsView {

    private final Event event;
    private final PendingRegistrations pending;

    public PendingRequestsView(final Event event, final PendingRegistrations pending) {
        this.event = event;
        this.pending = pending;
    }

    public Event getEvent() { return event; }

    // Una pagina de pedidos, cuantos lugares quedan y si ya no queda ninguno.
    public PendingRegistrations getPending() { return pending; }
}
