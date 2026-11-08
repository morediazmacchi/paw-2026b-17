package ar.edu.itba.paw.model;

import java.io.Serializable;
import java.util.Objects;

// Clave compuesta de Follow (@IdClass): los nombres de los atributos tienen que
// coincidir con los de los @Id de la entidad.
public class FollowId implements Serializable {

    private Long follower;
    private Long followed;

    public FollowId() {
        // Requerido por JPA
    }

    public FollowId(final Long follower, final Long followed) {
        this.follower = follower;
        this.followed = followed;
    }

    public Long getFollower() { return follower; }
    public Long getFollowed() { return followed; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FollowId)) {
            return false;
        }
        final FollowId other = (FollowId) o;
        return Objects.equals(follower, other.follower) && Objects.equals(followed, other.followed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(follower, followed);
    }
}
