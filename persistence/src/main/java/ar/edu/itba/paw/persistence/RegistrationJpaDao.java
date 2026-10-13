// Implementa el contrato RegistrationDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class RegistrationJpaDao implements RegistrationDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Registration create(final Registration registration) {
        // Con IDENTITY el INSERT corre aca mismo: si viola UNIQUE(event_id, email) la
        // excepcion sale de este metodo (traducida a DataIntegrityViolationException).
        em.persist(registration);
        return registration;
    }

    @Override
    public Optional<Registration> findByEventAndUserId(final long eventId, final long userId) {
        return em.createQuery(
                        "FROM Registration r WHERE r.event.id = :eventId AND r.user.id = :userId", Registration.class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList().stream().findFirst();
    }

    @Override
    public long countConfirmed(final long eventId) {
        return countByEventAndStatus(eventId, RegistrationStatus.CONFIRMED);
    }

    @Override
    public Optional<Registration> findById(final long id) {
        return Optional.ofNullable(em.find(Registration.class, id));
    }

    @Override
    public List<Registration> findByUserId(final long userId, final int page, final int pageSize) {
        // Mismo criterio de orden que EventJpaDao.findByCreatorId(): primero los
        // eventos que todavia no pasaron (el mas proximo primero), despues los
        // pasados (el mas reciente primero), y la pagina se corta en la base.
        return em.createQuery(
                        "SELECT r FROM Registration r JOIN r.event e WHERE r.user.id = :userId ORDER BY "
                                + "CASE WHEN e.eventDate >= :now THEN 0 ELSE 1 END ASC, "
                                + "CASE WHEN e.eventDate >= :now THEN e.eventDate END ASC, "
                                + "CASE WHEN e.eventDate < :now THEN e.eventDate END DESC, "
                                + "r.id ASC", Registration.class)
                .setParameter("userId", userId)
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(r) FROM Registration r WHERE r.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public List<Registration> findByEventId(final long eventId) {
        return em.createQuery(
                        "FROM Registration r WHERE r.event.id = :eventId ORDER BY r.registeredAt ASC", Registration.class)
                .setParameter("eventId", eventId)
                .getResultList();
    }

    @Override
    public List<Registration> findNextWaitlisted(final long eventId, final int limit) {
        // El orden sale de registeredAt: el que llego primero a la lista.
        return em.createQuery(
                        "FROM Registration r WHERE r.event.id = :eventId AND r.status = :status "
                                + "ORDER BY r.registeredAt, r.id", Registration.class)
                .setParameter("eventId", eventId)
                .setParameter("status", RegistrationStatus.WAITLISTED)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public boolean updateStatus(final long id, final RegistrationStatus status) {
        final Registration registration = em.find(Registration.class, id);
        if (registration == null) {
            return false;
        }
        // Dirty checking: el UPDATE se manda solo al hacer flush/commit.
        registration.setStatus(status);
        return true;
    }

    @Override
    public boolean delete(final long id) {
        final Registration registration = em.find(Registration.class, id);
        if (registration == null) {
            return false;
        }
        em.remove(registration);
        return true;
    }

    @Override
    public long countPending(final long eventId) {
        return countByEventAndStatus(eventId, RegistrationStatus.PENDING);
    }

    @Override
    public List<Registration> findPendingByEvent(final long eventId) {
        return em.createQuery(
                        "FROM Registration r WHERE r.event.id = :eventId AND r.status = :status "
                                + "ORDER BY r.registeredAt", Registration.class)
                .setParameter("eventId", eventId)
                .setParameter("status", RegistrationStatus.PENDING)
                .getResultList();
    }

    private long countByEventAndStatus(final long eventId, final RegistrationStatus status) {
        return em.createQuery(
                        "SELECT COUNT(r) FROM Registration r WHERE r.event.id = :eventId AND r.status = :status",
                        Long.class)
                .setParameter("eventId", eventId)
                .setParameter("status", status)
                .getSingleResult();
    }
}
