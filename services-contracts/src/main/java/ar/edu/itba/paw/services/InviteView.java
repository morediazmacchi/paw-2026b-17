// La pantalla de invitar: el evento y una pagina de las invitaciones ya enviadas. Lo devuelve
// MyAccountService.findInviteScreen().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;

public class InviteView {

    private final Event event;
    private final PageResult<EventInvitation> invitations;

    public InviteView(final Event event, final PageResult<EventInvitation> invitations) {
        this.event = event;
        this.invitations = invitations;
    }

    public Event getEvent() { return event; }

    public PageResult<EventInvitation> getInvitations() { return invitations; }
}
