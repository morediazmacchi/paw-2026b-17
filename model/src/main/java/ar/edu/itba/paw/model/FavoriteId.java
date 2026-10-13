package ar.edu.itba.paw.model;

import java.io.Serializable;
import java.util.Objects;

// Clave compuesta de Favorite (@IdClass): los nombres de los atributos tienen que
// coincidir con los de los @Id de la entidad.
public class FavoriteId implements Serializable {

    // Los nombres (user, event) y los tipos (el tipo del id de User y de Event) tienen que
    // coincidir con los de las dos relaciones @Id de Favorite.
    private Long user;
    private Long event;

    public FavoriteId() {
        // Requerido por JPA
    }

    public FavoriteId(final Long user, final Long event) {
        this.user = user;
        this.event = event;
    }

    public Long getUser() { return user; }
    public Long getEvent() { return event; }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FavoriteId)) {
            return false;
        }
        final FavoriteId other = (FavoriteId) o;
        return Objects.equals(user, other.user) && Objects.equals(event, other.event);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, event);
    }
}
