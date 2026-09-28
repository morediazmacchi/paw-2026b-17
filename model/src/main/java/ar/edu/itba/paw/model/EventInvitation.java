package ar.edu.itba.paw.model;

import java.util.Date;

/**
 * Invitacion personal del creador a alguien para que pueda ver (y, si quiere,
 * anotarse) a un evento privado. Quien entra tiene que SER la persona
 * invitada: logueado con la cuenta enlazada a esta invitacion (ver userId
 * mas abajo y EventController.canView()). Ya no hay un token/link que valga
 * por si solo sin sesion (ver el comentario de la tabla event_invitation en
 * schema.sql).
 */
public class EventInvitation {

    private long id;
    private long eventId;
    private String email;
    private String invitedBy;
    // FK de verdad contra users.id (ver schema.sql): quien invita es siempre
    // el creador del evento, logueado. Null solo en una fila vieja sin
    // backfillear todavia (ver postgres-migrations.sql).
    private Long invitedByUserId;
    // El INVITADO (no el que invita): puede no tener cuenta todavia, asi que
    // esto es optimista -- se completa si ya existia una cuenta al invitar
    // (ver EventInvitationServiceImpl.invite()), o mas adelante solo, cuando
    // esa persona se registra (ver UserServiceImpl.register()). Un valor en
    // null para siempre es un caso normal, no una fila vieja sin migrar.
    private Long userId;
    private Date createdAt;

    public EventInvitation() {
    }

    public EventInvitation(final long eventId, final String email, final String invitedBy) {
        this.eventId = eventId;
        this.email = email;
        this.invitedBy = invitedBy;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getInvitedBy() { return invitedBy; }
    public void setInvitedBy(final String invitedBy) { this.invitedBy = invitedBy; }
    public Long getInvitedByUserId() { return invitedByUserId; }
    public void setInvitedByUserId(final Long invitedByUserId) { this.invitedByUserId = invitedByUserId; }
    public Long getUserId() { return userId; }
    public void setUserId(final Long userId) { this.userId = userId; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
}
