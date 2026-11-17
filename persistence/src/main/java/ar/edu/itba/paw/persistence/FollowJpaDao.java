// Implementa el contrato FollowDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Follow;
import ar.edu.itba.paw.model.FollowId;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Repository
public class FollowJpaDao implements FollowDao {

    @PersistenceContext
    private EntityManager em;

    // El organizador del evento: lectura acotada de UNA columna (event.creator_id), sin cargar el Event. Vacio si
    // el evento no existe o no tiene organizador cargado (eventos viejos).
    private Optional<Long> organizerOf(final long eventId) {
        return em.createQuery("SELECT e.creator.id FROM Event e WHERE e.id = :eventId AND e.creator IS NOT NULL",
                        Long.class)
                .setParameter("eventId", eventId)
                .getResultList().stream().findFirst();
    }

    @Override
    public boolean followOrganizerOf(final long followerId, final long eventId) {
        final Optional<Long> organizerId = organizerOf(eventId);
        if (!organizerId.isPresent()) {
            return false;
        }
        // No se puede "intentar el INSERT y atrapar la violacion": una excepcion de Hibernate deja la
        // transaccion marcada para rollback. Se mira antes (mismo criterio que FavoriteJpaDao.add()).
        if (em.find(Follow.class, new FollowId(followerId, organizerId.get())) != null) {
            return false;
        }
        em.persist(new Follow(em.getReference(User.class, followerId),
                em.getReference(User.class, organizerId.get())));
        // PK compuesta asignada a mano: persist() no inserta enseguida. flush() para que un id
        // inexistente (FK) falle desde este metodo y no al commit.
        em.flush();
        return true;
    }

    @Override
    public boolean unfollowOrganizerOf(final long followerId, final long eventId) {
        final Optional<Long> organizerId = organizerOf(eventId);
        if (!organizerId.isPresent()) {
            return false;
        }
        final Follow follow = em.find(Follow.class, new FollowId(followerId, organizerId.get()));
        if (follow == null) {
            return false;
        }
        em.remove(follow);
        return true;
    }

    @Override
    public boolean isFollowing(final long followerId, final long followedId) {
        return em.find(Follow.class, new FollowId(followerId, followedId)) != null;
    }

    @Override
    public List<FollowerContact> findFollowersAfter(final long followedId, final long afterId, final int limit) {
        // Proyeccion (SELECT NEW): solo las cuatro columnas que usa el mail, no la cuenta entera. Paginacion por
        // "despues de este id" (no OFFSET): cada tanda arranca donde termino la anterior.
        return em.createQuery(
                        "SELECT NEW ar.edu.itba.paw.model.FollowerContact(u.id, u.email, u.name, u.locale) "
                                + "FROM Follow f JOIN f.follower u WHERE f.followed.id = :followedId "
                                + "AND u.id > :afterId ORDER BY u.id", FollowerContact.class)
                .setParameter("followedId", followedId)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public long countFollowers(final long followedId) {
        return em.createQuery("SELECT COUNT(f) FROM Follow f WHERE f.followed.id = :followedId", Long.class)
                .setParameter("followedId", followedId)
                .getSingleResult();
    }
}
