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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    // El WHERE de visibilidad (EventAccessService.canView para quien no es admin), en un solo lugar: lo usan
    // el COUNT y la pagina, asi nunca se desincronizan. Espera los alias f (Favorite) y e (su evento).
    private static final String VIEWABLE_WHERE = "f.user.id = :userId AND ("
            + "e.creator.id = :userId OR (e.approvalStatus = :approved AND ("
            + "e.visibility = :publicVisibility "
            + "OR EXISTS (SELECT r.id FROM Registration r "
            + "WHERE r.event.id = e.id AND r.user.id = :userId) "
            + "OR EXISTS (SELECT i.id FROM EventInvitation i "
            + "WHERE i.event.id = e.id AND i.user.id = :userId))))";

    @Override
    public long countViewableByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(f) FROM Favorite f JOIN f.event e WHERE " + VIEWABLE_WHERE, Long.class)
                .setParameter("userId", userId)
                .setParameter("approved", EventApprovalStatus.APPROVED)
                .setParameter("publicVisibility", EventVisibility.PUBLIC)
                .getSingleResult();
    }

    @Override
    public List<Long> findViewableEventIdsByUser(final long userId, final int page, final int pageSize) {
        // Solo ids: la pagina trae a lo sumo pageSize numeros; los eventos (con sus categorias) los busca
        // despues quien llama con un unico IN (...). El desempate por id fija el orden si dos favoritos
        // comparten createdAt.
        return em.createQuery(
                        "SELECT e.id FROM Favorite f JOIN f.event e WHERE " + VIEWABLE_WHERE
                                + " ORDER BY f.createdAt DESC, e.id DESC", Long.class)
                .setParameter("userId", userId)
                .setParameter("approved", EventApprovalStatus.APPROVED)
                .setParameter("publicVisibility", EventVisibility.PUBLIC)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Override
    public Set<Long> findFavoriteEventIds(final long userId, final Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(em.createQuery(
                        "SELECT f.event.id FROM Favorite f WHERE f.user.id = :userId AND f.event.id IN :eventIds",
                        Long.class)
                .setParameter("userId", userId)
                .setParameter("eventIds", eventIds)
                .getResultList());
    }

    @Override
    public List<Long> findEventIdsByUser(final long userId, final int page, final int pageSize) {
        return em.createQuery(
                        "SELECT f.event.id FROM Favorite f WHERE f.user.id = :userId "
                                + "ORDER BY f.createdAt DESC, f.event.id DESC", Long.class)
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }
}
