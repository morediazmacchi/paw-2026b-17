// Implementa el contrato EventInvitationDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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
    public List<EventInvitation> findByEvent(final long eventId) {
        return em.createQuery(
                        "FROM EventInvitation i WHERE i.event.id = :eventId ORDER BY i.createdAt DESC",
                        EventInvitation.class)
                .setParameter("eventId", eventId)
                .getResultList();
    }

    @Override
    public List<EventInvitation> findByUserId(final long userId, final int page, final int pageSize) {
        return em.createQuery(
                        "FROM EventInvitation i WHERE i.user.id = :userId ORDER BY i.createdAt DESC, i.id DESC",
                        EventInvitation.class)
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
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
