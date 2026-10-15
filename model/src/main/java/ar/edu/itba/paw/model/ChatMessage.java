package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

// Un mensaje del grupo de chat de un evento. El nombre de quien lo escribio no se guarda: sale
// de la cuenta (sender) al leer, asi si la persona cambia su nombre se ve el nuevo.
@Entity
@Table(name = "event_chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false)
    private long eventId;
    // Se escribe por esta columna simple (alcanza con el id, sin cargar al usuario)...
    @Column(name = "user_id", nullable = false)
    private long userId;
    // ...y se lee por esta relacion, de solo lectura sobre la misma columna: sirve para traer el
    // nombre con un JOIN FETCH. Hibernate nunca inserta ni actualiza por aca.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User sender;
    @Column(name = "body", length = 1000, nullable = false)
    private String body;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    public ChatMessage() {
        // Solo para Hibernate
    }

    public ChatMessage(final long eventId, final long userId, final String body) {
        this.eventId = eventId;
        this.userId = userId;
        this.body = body;
    }

    public Long getId() { return id; }
    public long getEventId() { return eventId; }
    public long getUserId() { return userId; }
    public String getBody() { return body; }
    public Date getCreatedAt() { return createdAt; }

    // El nombre que eligio al registrarse; si no tiene, lo que va antes del arroba.
    // Null si el mensaje se armo a mano y todavia no se leyo de la base.
    public String getSenderName() {
        if (sender == null) {
            return null;
        }
        final String name = sender.getName();
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        final String email = sender.getEmail();
        final int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
