package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.WaitlistInvitation;

/**
 * Lo que necesita la pagina del link para dibujarse. Van juntos porque la tabla
 * de invitaciones no guarda el evento (se llega por la inscripcion), y atar esos
 * dos cabos no es trabajo del controller.
 */
public class InvitationDetail {

    private final WaitlistInvitation invitation;
    private final Event event;

    public InvitationDetail(final WaitlistInvitation invitation, final Event event) {
        this.invitation = invitation;
        this.event = event;
    }

    public WaitlistInvitation getInvitation() { return invitation; }
    public Event getEvent() { return event; }
}
