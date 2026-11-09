// Implementa el contrato EventInvitationDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class EventInvitationJpaDao implements EventInvitationDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public EventInvitation create(final EventInvitation invitation) {
        // Con IDENTITY el INSERT corre aca mismo: si viola UNIQUE(event_id, email) la
        // excepcion sale de este metodo (traducida a DataIntegrityViolationException).
        em.persist(invitation);
        return invitation;
    }

    @Override
    public Optional<EventInvitation> findByEventAndEmail(final long eventId, final String email) {
        return em.createQuery(
                        "FROM EventInvitation i WHERE i.event.id = :eventId AND i.email = :email", EventInvitation.class)
                .setParameter("eventId", eventId)
                .setParameter("email", email)
                .getResultList().stream().findFirst();
    }

    @Override
    public boolean existsByEventAndUserId(final long eventId, final long userId) {
        return em.createQuery(
                        "SELECT COUNT(i) FROM EventInvitation i WHERE i.event.id = :eventId AND i.user.id = :userId",
                        Long.class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getSingleResult() > 0;
    }

    @Override
    public Optional<EventInvitation> findByEventAndUserId(final long eventId, final long userId) {
        return em.createQuery(
                        "FROM EventInvitation i WHERE i.event.id = :eventId AND i.user.id = :userId", EventInvitation.class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst();
    }

    @Override
    public long countOutstanding(final long eventId) {
        // Pendientes = invitaciones cuyo email todavia no tiene una inscripcion en ese evento.
        // JPQL con subconsulta correlacionada sobre otra entidad (Registration).
        return em.createQuery(
                        "SELECT COUNT(i) FROM EventInvitation i WHERE i.event.id = :eventId AND NOT EXISTS ("
                                + "SELECT r.id FROM Registration r WHERE r.event.id = i.event.id AND r.email = i.email)",
                        Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }

    @Override
    public List<EventInvitation> findByEvent(final long eventId, final int page, final int pageSize) {
        // Dos pasos (1+1): primero los ids de la pagina (el id desempata), despues las invitaciones.
        final List<Long> ids = em.createQuery(
                        "SELECT i.id FROM EventInvitation i WHERE i.event.id = :eventId "
                                + "ORDER BY i.createdAt DESC, i.id DESC", Long.class)
                .setParameter("eventId", eventId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return PagedIds.inOrder(ids,
                em.createQuery("FROM EventInvitation i WHERE i.id IN :ids", EventInvitation.class)
                        .setParameter("ids", ids)
                        .getResultList(),
                EventInvitation::getId);
    }

    @Override
    public long countByEvent(final long eventId) {
        return em.createQuery("SELECT COUNT(i) FROM EventInvitation i WHERE i.event.id = :eventId", Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }

    @Override
    public List<Event> findInvitedEventsByUserId(final long userId, final int page, final int pageSize) {
        // Dos pasos (1+1): primero los ids de los eventos de la pagina, despues los eventos.
        final List<Long> ids = em.createQuery(
                        "SELECT e.id FROM EventInvitation i JOIN i.event e WHERE i.user.id = :userId "
                                + "ORDER BY i.createdAt DESC, i.id DESC", Long.class)
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return PagedIds.inOrder(ids,
                em.createQuery("FROM Event e WHERE e.id IN :ids", Event.class)
                        .setParameter("ids", ids)
                        .getResultList(),
                Event::getId);
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(i) FROM EventInvitation i WHERE i.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public int linkUserId(final String email, final long userId) {
        // UPDATE masivo (bulk JPQL): es un solo UPDATE ... WHERE user_id IS NULL, atomico, y
        // devuelve cuantas filas cambio. Ojo: no actualiza las entidades que ya esten cargadas
        // en el EntityManager.
        return em.createQuery(
                        "UPDATE EventInvitation i SET i.user = :user WHERE i.email = :email AND i.user IS NULL")
                .setParameter("user", em.getReference(User.class, userId))
                .setParameter("email", email)
                .executeUpdate();
    }
}
