package ar.edu.itba.paw.model;

import java.util.Date;

/**
 * Un aviso de la bandeja de mensajes (/perfil/bandeja). Es un complemento del
 * mail, no lo reemplaza: todo lo que manda un mail tambien deja uno de estos.
 *
 * Va por email y no por user_id: a quien invitaron a un evento privado puede no
 * tener cuenta todavia, y cuando se la crea con ese email ve lo que le llego.
 */
public class InboxMessage {

    private long id;
    private String recipientEmail;
    // Optimista, igual que EventInvitation.userId: se completa si el
    // destinatario ya tenia cuenta al mandarse el aviso (ver
    // InboxServiceImpl.add()), o mas adelante solo, cuando se registra (ver
    // UserServiceImpl.register()). Null para siempre es un caso normal.
    private Long userId;
    private InboxMessageType type;
    // Casi todos hablan de un evento; null si alguno no.
    private Long eventId;
    // Quien hizo algo ("Ana se anoto"). Null si no aplica.
    private String actorName;
    // Texto libre (lo que escribio el organizador, una pregunta). Null si no aplica.
    private String body;
    private Date createdAt;
    // Null = sin leer.
    private Date readAt;

    public InboxMessage() {
    }

    public InboxMessage(final String recipientEmail, final InboxMessageType type, final Long eventId,
                        final String actorName, final String body) {
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.eventId = eventId;
        this.actorName = actorName;
        this.body = body;
    }

    public boolean isUnread() { return readAt == null; }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(final String recipientEmail) { this.recipientEmail = recipientEmail; }
    public Long getUserId() { return userId; }
    public void setUserId(final Long userId) { this.userId = userId; }
    public InboxMessageType getType() { return type; }
    public void setType(final InboxMessageType type) { this.type = type; }
    public Long getEventId() { return eventId; }
    public void setEventId(final Long eventId) { this.eventId = eventId; }
    public String getActorName() { return actorName; }
    public void setActorName(final String actorName) { this.actorName = actorName; }
    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public Date getReadAt() { return readAt; }
    public void setReadAt(final Date readAt) { this.readAt = readAt; }
}
