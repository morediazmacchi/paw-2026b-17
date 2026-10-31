// Implementa el contrato ReviewDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class ReviewJpaDao implements ReviewDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Review create(final Review review) {
        // Con IDENTITY el INSERT corre aca mismo: si viola UNIQUE(event_id, user_id) la
        // excepcion sale de este metodo (traducida a DataIntegrityViolationException).
        // Ver RegistrationJpaDao.create(): de quien reseña solo hace falta el id.
        review.setUser(em.getReference(User.class, review.getUser().getId()));
        em.persist(review);
        return review;
    }

    @Override
    public Optional<Review> findByEventAndUser(final long eventId, final long userId) {
        return em.createQuery("FROM Review r WHERE r.event.id = :eventId AND r.user.id = :userId", Review.class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst();
    }

    @Override
    public List<Review> findByEventsAndUser(final List<Long> eventIds, final long userId) {
        if (eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery("FROM Review r WHERE r.event.id IN :eventIds AND r.user.id = :userId", Review.class)
                .setParameter("eventIds", eventIds)
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public List<Review> findByCreatorId(final long creatorId) {
        // Las reseñas de todos los eventos del creador: se navega Review -> Event -> creador.
        return em.createQuery(
                        "FROM Review r WHERE r.event.creator.id = :creatorId ORDER BY r.createdAt DESC", Review.class)
                .setParameter("creatorId", creatorId)
                .getResultList();
    }

    @Override
    public double findAverageRatingByCreatorId(final long creatorId) {
        // Sin reseñas, AVG da NULL: COALESCE lo convierte en 0. El CAST es porque rating es
        // INTEGER y HSQLDB (la base de los tests) hace AVG entero: 5 y 4 darian 4 en vez de 4.5.
        // Postgres no lo necesita, pero no le molesta.
        final Double average = em.createQuery(
                        "SELECT COALESCE(AVG(CAST(r.rating AS double)), 0.0) FROM Review r "
                                + "WHERE r.event.creator.id = :creatorId", Double.class)
                .setParameter("creatorId", creatorId)
                .getSingleResult();
        return average == null ? 0 : average;
    }
}
