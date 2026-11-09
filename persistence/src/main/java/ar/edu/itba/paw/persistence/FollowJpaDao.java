// Implementa el contrato FollowDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Follow;
import ar.edu.itba.paw.model.FollowId;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Repository
public class FollowJpaDao implements FollowDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean follow(final long followerId, final long followedId) {
        // No se puede "intentar el INSERT y atrapar la violacion": una excepcion de Hibernate deja la
        // transaccion marcada para rollback. Se mira antes (mismo criterio que FavoriteJpaDao.add()).
        if (em.find(Follow.class, new FollowId(followerId, followedId)) != null) {
            return false;
        }
        em.persist(new Follow(em.getReference(User.class, followerId), em.getReference(User.class, followedId)));
        // PK compuesta asignada a mano: persist() no inserta enseguida. flush() para que un id
        // inexistente (FK) falle desde este metodo y no al commit.
        em.flush();
        return true;
    }

    @Override
    public boolean unfollow(final long followerId, final long followedId) {
        final Follow follow = em.find(Follow.class, new FollowId(followerId, followedId));
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
    public List<User> findFollowers(final long followedId) {
        return em.createQuery("SELECT f.follower FROM Follow f WHERE f.followed.id = :followedId", User.class)
                .setParameter("followedId", followedId)
                .getResultList();
    }

    @Override
    public long countFollowers(final long followedId) {
        return em.createQuery("SELECT COUNT(f) FROM Follow f WHERE f.followed.id = :followedId", Long.class)
                .setParameter("followedId", followedId)
                .getSingleResult();
    }
}
