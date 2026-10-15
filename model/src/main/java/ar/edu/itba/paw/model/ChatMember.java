package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

// Alguien que quiso unirse al grupo de chat de un evento. Su id es el de su inscripcion
// (registration): la base la borra sola (ON DELETE CASCADE) si esa inscripcion desaparece, y
// solo cuenta como miembro activo mientras la inscripcion siga CONFIRMED.
@Entity
@Table(name = "event_chat_member")
public class ChatMember {

    @Id
    @Column(name = "registration_id")
    private long registrationId;
    @Column(name = "event_id", nullable = false)
    private long eventId;
    @Column(name = "user_id", nullable = false)
    private long userId;
    // DEFAULT now() de la base.
    @Column(name = "joined_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date joinedAt;
    // Id del ultimo mensaje que vio; lo que viene despues cuenta como "sin leer".
    @Column(name = "last_read_id", nullable = false)
    private long lastReadId;

    public ChatMember() {
        // Solo para Hibernate
    }

    public ChatMember(final long registrationId, final long eventId, final long userId) {
        this.registrationId = registrationId;
        this.eventId = eventId;
        this.userId = userId;
    }

    public long getRegistrationId() { return registrationId; }
    public long getEventId() { return eventId; }
    public long getUserId() { return userId; }
    public Date getJoinedAt() { return joinedAt; }
    public long getLastReadId() { return lastReadId; }
}
