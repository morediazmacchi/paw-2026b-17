package ar.edu.itba.paw.model;

import java.util.Date;

/**
 * Invitacion personal del creador a alguien para que pueda ver (y, si quiere,
 * anotarse) a un evento privado. El token es la unica credencial: quien lo
 * tiene, entra, sin importar con que cuenta este logueado (o si esta
 * deslogueado). Ver el comentario de la tabla event_invitation en schema.sql.
 */
public class EventInvitation {

    private long id;
    private long eventId;
    private String email;
    private String token;
    private String invitedBy;
    private Date createdAt;

    public EventInvitation() {
    }

    public EventInvitation(final long eventId, final String email, final String token, final String invitedBy) {
        this.eventId = eventId;
        this.email = email;
        this.token = token;
        this.invitedBy = invitedBy;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getToken() { return token; }
    public void setToken(final String token) { this.token = token; }
    public String getInvitedBy() { return invitedBy; }
    public void setInvitedBy(final String invitedBy) { this.invitedBy = invitedBy; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
}
