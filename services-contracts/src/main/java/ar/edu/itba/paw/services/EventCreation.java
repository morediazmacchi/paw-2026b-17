// Lo que devuelve EventService.create(): el evento creado y el proximo paso del flujo de publicar. Quien lo
// recibe (el controller) solo lo traduce a una pantalla; la decision de que viene despues es del service.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

public class EventCreation {

    private final Event event;
    private final boolean needsInvitations;

    public EventCreation(final Event event, final boolean needsInvitations) {
        this.event = event;
        this.needsInvitations = needsInvitations;
    }

    public Event getEvent() { return event; }

    // Un evento privado solo lo ven los invitados: lo siguiente es invitar gente (un evento publico ya se puede
    // compartir por su link).
    public boolean isNeedsInvitations() { return needsInvitations; }
}
