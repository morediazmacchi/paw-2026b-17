// Implementa el contrato FavoriteDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Favorite;
import ar.edu.itba.paw.model.FavoriteId;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Repository
public class FavoriteJpaDao implements FavoriteDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public boolean add(final long userId, final long eventId) {
        // No se puede "intentar el INSERT y atrapar la violacion": una excepcion de
        // Hibernate deja la transaccion marcada para rollback. Se mira antes.
        if (em.find(Favorite.class, new FavoriteId(userId, eventId)) != null) {
            return false;
        }
        // getReference: un "representante" del usuario y del evento con solo su id, sin ir a la
        // base a buscarlos (para la FK alcanza con el id).
        em.persist(new Favorite(em.getReference(User.class, userId), em.getReference(Event.class, eventId)));
        // PK compuesta asignada a mano: persist() no inserta enseguida. flush() para que
        // un user/event inexistente (FK) falle desde este metodo y no al commit.
        em.flush();
        return true;
    }

    @Override
    public boolean remove(final long userId, final long eventId) {
        final Favorite favorite = em.find(Favorite.class, new FavoriteId(userId, eventId));
        if (favorite == null) {
            return false;
        }
        em.remove(favorite);
        return true;
    }

    @Override
    public boolean isFavorite(final long userId, final long eventId) {
        return em.find(Favorite.class, new FavoriteId(userId, eventId)) != null;
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(f) FROM Favorite f WHERE f.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public long countViewableByUserId(final long userId) {
        return em.createQuery(
                        "SELECT COUNT(f) FROM Favorite f JOIN f.event e WHERE f.user.id = :userId AND ("
                                + "e.creator.id = :userId OR (e.approvalStatus = :approved AND ("
                                + "e.visibility = :publicVisibility "
                                + "OR EXISTS (SELECT r.id FROM Registration r "
                                + "WHERE r.event.id = e.id AND r.user.id = :userId) "
                                + "OR EXISTS (SELECT i.id FROM EventInvitation i "
                                + "WHERE i.event.id = e.id AND i.user.id = :userId))))", Long.class)
                .setParameter("userId", userId)
                .setParameter("approved", EventApprovalStatus.APPROVED)
                .setParameter("publicVisibility", EventVisibility.PUBLIC)
                .getSingleResult();
    }

    @Override
    public List<Long> findEventIdsByUser(final long userId) {
        return em.createQuery(
                        "SELECT f.event.id FROM Favorite f WHERE f.user.id = :userId ORDER BY f.createdAt DESC",
                        Long.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
