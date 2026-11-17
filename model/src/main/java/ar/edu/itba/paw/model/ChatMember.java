package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

// Alguien que quiso unirse al grupo de chat de un evento. Su clave es la de su inscripcion
// (registration): la base la borra sola (ON DELETE CASCADE) si esa inscripcion desaparece, y
// solo cuenta como miembro activo mientras la inscripcion siga CONFIRMED.
@Entity
@Table(name = "event_chat_member")
public class ChatMember {

    // La clave primaria es la de la inscripcion (@MapsId: se copia de la relacion de abajo).
    @Id
    @Column(name = "registration_id")
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "registration_id")
    private Registration registration;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
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

    public ChatMember(final Registration registration, final Event event, final User user) {
        this.registration = registration;
        this.event = event;
        this.user = user;
    }

    public Registration getRegistration() { return registration; }
    public Event getEvent() { return event; }
    public User getUser() { return user; }
    public Date getJoinedAt() { return joinedAt; }
    public long getLastReadId() { return lastReadId; }
}
