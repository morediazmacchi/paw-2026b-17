package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Date;

// Un evento marcado como favorito por un usuario. La clave primaria es la PAREJA
// (usuario, evento): una fila por cada combinacion, sin id propio. Por eso las dos relaciones
// llevan @Id ("clave derivada") y FavoriteId (@IdClass) describe esa pareja con los ids.
@Entity
@Table(name = "favorite")
@IdClass(FavoriteId.class)
public class Favorite {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // La llena el DEFAULT now() de la base: Hibernate no la inserta ni la actualiza.
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Date createdAt;

    /* default */ Favorite() {
        // Solo para Hibernate
    }

    public Favorite(final User user, final Event event) {
        this.user = user;
        this.event = event;
    }

    public User getUser() { return user; }
    public Event getEvent() { return event; }
    public long getUserId() { return user.getId(); }
    public long getEventId() { return event.getId(); }
    public Date getCreatedAt() { return createdAt; }
}
