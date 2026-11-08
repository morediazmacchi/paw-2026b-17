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

// Un usuario (follower) que sigue a otro (followed) para que le avisen cuando publique un evento
// nuevo (ver EventServiceImpl.approve()). Misma forma que Favorite: la clave primaria es la PAREJA
// (follower, followed), sin id propio -- @IdClass (FollowId) describe esa pareja con los ids.
@Entity
@Table(name = "follow")
@IdClass(FollowId.class)
public class Follow {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followed_id", nullable = false)
    private User followed;

    // La llena el DEFAULT now() de la base: Hibernate no la inserta ni la actualiza.
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Date createdAt;

    /* default */ Follow() {
        // Solo para Hibernate
    }

    public Follow(final User follower, final User followed) {
        this.follower = follower;
        this.followed = followed;
    }

    public User getFollower() { return follower; }
    public User getFollowed() { return followed; }
    public long getFollowerId() { return follower.getId(); }
    public long getFollowedId() { return followed.getId(); }
    public Date getCreatedAt() { return createdAt; }
}
