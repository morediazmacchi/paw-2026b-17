package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

/**
 * Un aviso de la bandeja de mensajes (/perfil/bandeja). Es un complemento del
 * mail, no lo reemplaza: todo lo que manda un mail tambien deja uno de estos.
 *
 * Va por email y no por user_id: a quien invitaron a un evento privado puede no
 * tener cuenta todavia, y cuando se la crea con ese email ve lo que le llego.
 */
@Entity
@Table(name = "inbox_message")
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "recipient_email", length = 150, nullable = false)
    private String recipientEmail;
    // Optimista, igual que EventInvitation.userId: se completa si el
    // destinatario ya tenia cuenta al mandarse el aviso (ver
    // InboxServiceImpl.add()), o mas adelante solo, cuando se registra (ver
    // UserServiceImpl.register()). Null para siempre es un caso normal.
    // Cuenta del destinatario si ya tiene una (null si el mail todavia no se registro).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 40, nullable = false)
    private InboxMessageType type;
    // Casi todos hablan de un evento; null si alguno no.
    // Evento al que se refiere el aviso (null en los avisos que no hablan de uno).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
    // Quien hizo algo ("Ana se anoto"). Null si no aplica.
    @Column(name = "actor_name", length = 150)
    private String actorName;
    // Texto libre (lo que escribio el organizador, una pregunta). Null si no aplica.
    @Column(name = "body", length = 2000)
    private String body;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    // Null = sin leer.
    @Column(name = "read_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date readAt;

    public InboxMessage() {
    }

    public InboxMessage(final String recipientEmail, final InboxMessageType type, final Event event,
                        final String actorName, final String body) {
        this.recipientEmail = recipientEmail;
        this.type = type;
        this.event = event;
        this.actorName = actorName;
        this.body = body;
    }

    public boolean isUnread() { return readAt == null; }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public String getRecipientEmail() { return recipientEmail; }
    public void setRecipientEmail(final String recipientEmail) { this.recipientEmail = recipientEmail; }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    public Long getUserId() { return user == null ? null : user.getId(); }
    public InboxMessageType getType() { return type; }
    public void setType(final InboxMessageType type) { this.type = type; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    public Long getEventId() { return event == null ? null : event.getId(); }
    public String getActorName() { return actorName; }
    public void setActorName(final String actorName) { this.actorName = actorName; }
    public String getBody() { return body; }
    public void setBody(final String body) { this.body = body; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public Date getReadAt() { return readAt; }
    public void setReadAt(final Date readAt) { this.readAt = readAt; }
}
