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
import javax.persistence.UniqueConstraint;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

@Entity
@Table(name = "review", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "user_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Relacion con el evento (muchos N:1). LAZY: Hibernate trae el evento de la base recien
    // cuando alguien lo usa; mientras tanto deja un "representante" que ya conoce el id.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    // Quien dejo la reseña.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "rating", nullable = false)
    private int rating;
    @Column(name = "comment", length = 1000)
    private String comment;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    public Review() {
    }

    public Review(final Event event, final User user, final int rating, final String comment) {
        this.event = event;
        this.user = user;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    // Atajos (no disparan consultas: el id ya esta en la fila de la reseña).
    public long getEventId() { return event.getId(); }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    public long getUserId() { return user.getId(); }
    public int getRating() { return rating; }
    public void setRating(final int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(final String comment) { this.comment = comment; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
}
