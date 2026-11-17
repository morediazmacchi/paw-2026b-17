// Implementa el contrato ReviewDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.RatingSummary;
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
    public Review create(final long eventId, final long userId, final int rating, final String comment) {
        // Con IDENTITY el INSERT corre aca mismo: si viola UNIQUE(event_id, user_id) la
        // excepcion sale de este metodo (traducida a DataIntegrityViolationException).
        // Ver RegistrationJpaDao.create(): del evento y de quien reseña solo hace falta el id.
        final Review review = new Review(
                em.getReference(Event.class, eventId), em.getReference(User.class, userId), rating, comment);
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
    public List<Review> findByCreatorId(final long creatorId, final int page, final int pageSize) {
        // Las reseñas de todos los eventos del creador: se navega Review -> Event -> creador.
        // r.id desempata dos reseñas del mismo instante: asi una pagina y la siguiente no se pisan.
        // Dos pasos (1+1): primero los ids de la pagina, despues las reseñas de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM Review r WHERE r.event.creator.id = :creatorId "
                                + "ORDER BY r.createdAt DESC, r.id DESC", Long.class)
                .setParameter("creatorId", creatorId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // El ORDER BY es el MISMO de la consulta de ids (el IN no ordena nada). Cada reseña trae su evento: la
        // pantalla muestra de que evento es cada una, y asi no hace falta ir a buscarlos aparte.
        return em.createQuery("SELECT r FROM Review r JOIN FETCH r.event WHERE r.id IN :ids "
                + "ORDER BY r.createdAt DESC, r.id DESC", Review.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public RatingSummary findRatingSummaryByCreatorId(final long creatorId) {
        // COUNT y AVG juntos: una sola consulta (un agregado sin GROUP BY siempre devuelve exactamente una fila).
        // Sin reseñas, AVG da NULL: COALESCE lo convierte en 0. El CAST es porque rating es
        // INTEGER y HSQLDB (la base de los tests) hace AVG entero: 5 y 4 darian 4 en vez de 4.5.
        // Postgres no lo necesita, pero no le molesta.
        final Object[] row = em.createQuery(
                        "SELECT COUNT(r), COALESCE(AVG(CAST(r.rating AS double)), 0.0) FROM Review r "
                                + "WHERE r.event.creator.id = :creatorId", Object[].class)
                .setParameter("creatorId", creatorId)
                .getSingleResult();
        return new RatingSummary(((Number) row[0]).longValue(), ((Number) row[1]).doubleValue());
    }
}
