// Implementa el contrato RegistrationDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RegistrationJpaDao implements RegistrationDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Registration create(final long eventId, final long userId, final String email, final String name,
                               final RegistrationStatus status) {
        // Con IDENTITY el INSERT corre aca mismo: si viola UNIQUE(event_id, email) la
        // excepcion sale de este metodo (traducida a DataIntegrityViolationException).
        // Para la relacion alcanza con el id: se apunta a una referencia, sin leer el evento ni la cuenta
        // (si ya estan cargados en esta transaccion, es la misma instancia).
        final Registration registration = new Registration(
                em.getReference(Event.class, eventId), em.getReference(User.class, userId), email, name, status);
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
    public Optional<Registration> findById(final long id) {
        return Optional.ofNullable(em.find(Registration.class, id));
    }

    @Override
    public Optional<RegistrationAccess> findAccessById(final long id) {
        // Proyeccion: de quien es, de que evento y el estado (3 columnas), no la inscripcion. user.id y
        // event.id son las propias columnas de la tabla: no hay JOIN.
        return em.createQuery(
                        "SELECT r.user.id, r.event.id, r.status FROM Registration r WHERE r.id = :id", Object[].class)
                .setParameter("id", id)
                .getResultList().stream().map(RegistrationJpaDao::toAccess).findFirst();
    }

    @Override
    public Optional<RegistrationAccess> findAccessByEventAndUserId(final long eventId, final long userId) {
        return em.createQuery(
                        "SELECT r.user.id, r.event.id, r.status FROM Registration r "
                                + "WHERE r.event.id = :eventId AND r.user.id = :userId", Object[].class)
                .setParameter("eventId", eventId)
                .setParameter("userId", userId)
                .getResultList().stream().map(RegistrationJpaDao::toAccess).findFirst();
    }

    private static RegistrationAccess toAccess(final Object[] row) {
        return new RegistrationAccess((Long) row[0], (Long) row[1], (RegistrationStatus) row[2]);
    }

    @Override
    public List<Registration> findUpcomingByUserId(final long userId, final Date now, final int page,
                                                   final int pageSize) {
        // Dos pasos (1+1): ids de la pagina, despues las inscripciones de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM Registration r JOIN r.event e WHERE r.user.id = :userId "
                                + "AND e.eventDate >= :now ORDER BY e.eventDate ASC, r.id ASC", Long.class)
                .setParameter("userId", userId)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, "e.eventDate ASC, r.id ASC");
    }

    @Override
    public long countUpcomingByUserId(final long userId, final Date now) {
        return em.createQuery(
                        "SELECT COUNT(r) FROM Registration r JOIN r.event e WHERE r.user.id = :userId "
                                + "AND e.eventDate >= :now", Long.class)
                .setParameter("userId", userId)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .getSingleResult();
    }

    @Override
    public List<Registration> findPastByUserId(final long userId, final Date now, final int page,
                                               final int pageSize) {
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM Registration r JOIN r.event e WHERE r.user.id = :userId "
                                + "AND e.eventDate < :now ORDER BY e.eventDate DESC, r.id ASC", Long.class)
                .setParameter("userId", userId)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, "e.eventDate DESC, r.id ASC");
    }

    // Paso 2 del 1+1: carga las inscripciones de "ids" con el MISMO ORDER BY que la consulta de ids (el IN no
    // ordena nada). "orderBy" es la cola de la consulta de ids (lo que sigue a ORDER BY).
    private List<Registration> loadPage(final List<Long> ids, final String orderBy) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // Cada inscripcion trae su evento (la pantalla lo muestra): sin FETCH quedaria una consulta por fila.
        return em.createQuery("SELECT r FROM Registration r JOIN FETCH r.event e WHERE r.id IN :ids ORDER BY " + orderBy,
                        Registration.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public long countPastByUserId(final long userId, final Date now) {
        return em.createQuery(
                        "SELECT COUNT(r) FROM Registration r JOIN r.event e WHERE r.user.id = :userId "
                                + "AND e.eventDate < :now", Long.class)
                .setParameter("userId", userId)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .getSingleResult();
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(r) FROM Registration r WHERE r.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public List<Registration> findConfirmedByEventIdAfter(final long eventId, final long afterId, final int limit) {
        // Paginacion por "despues de este id" (no OFFSET): cada tanda arranca donde termino la anterior, asi recorrer
        // todas cuesta lo mismo en la primera tanda que en la ultima.
        return em.createQuery(
                        "FROM Registration r WHERE r.event.id = :eventId AND r.status IN :statuses "
                                + "AND r.id > :afterId ORDER BY r.id ASC", Registration.class)
                .setParameter("eventId", eventId)
                .setParameter("statuses", RegistrationStatus.withConfirmedPlace())
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public List<Registration> findByEventId(final long eventId, final int page, final int pageSize) {
        // Dos pasos (1+1): ids de la pagina, despues las inscripciones de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM Registration r WHERE r.event.id = :eventId "
                                + "ORDER BY r.registeredAt ASC, r.id ASC", Long.class)
                .setParameter("eventId", eventId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, "r.registeredAt ASC, r.id ASC");
    }

    @Override
    public long countByEventId(final long eventId) {
        return em.createQuery("SELECT COUNT(r) FROM Registration r WHERE r.event.id = :eventId", Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }

    @Override
    public List<Long> findNextWaitlistedIds(final long eventId, final int limit) {
        // El orden sale de registeredAt: el que llego primero a la lista. Solo el id: no se carga ninguna inscripcion.
        return em.createQuery(
                        "SELECT r.id FROM Registration r WHERE r.event.id = :eventId AND r.status = :status "
                                + "ORDER BY r.registeredAt, r.id", Long.class)
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
    public int updateStatusOfAll(final Collection<Long> registrationIds, final RegistrationStatus status) {
        // Bulk JPQL a proposito (no em.find + setter por cada una): una sentencia por tanda de ids, sin cargarlas.
        int updated = 0;
        for (final List<Long> chunk : Chunks.of(registrationIds)) {
            updated += em.createQuery("UPDATE Registration r SET r.status = :status WHERE r.id IN :ids")
                    .setParameter("status", status)
                    .setParameter("ids", chunk)
                    .executeUpdate();
        }
        return updated;
    }

    @Override
    public List<Registration> findByIds(final Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery("FROM Registration r WHERE r.id IN :ids ORDER BY r.id ASC", Registration.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public List<Registration> findByUserIdAndEventIds(final long userId, final Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery(
                        "FROM Registration r WHERE r.user.id = :userId AND r.event.id IN :eventIds",
                        Registration.class)
                .setParameter("userId", userId)
                .setParameter("eventIds", eventIds)
                .getResultList();
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
    public List<Long> findPendingIdsByEvent(final long eventId) {
        // Solo el id: no se carga ninguna inscripcion (ver RegistrationDao).
        return em.createQuery(
                        "SELECT r.id FROM Registration r WHERE r.event.id = :eventId AND r.status = :status "
                                + "ORDER BY r.registeredAt, r.id", Long.class)
                .setParameter("eventId", eventId)
                .setParameter("status", RegistrationStatus.PENDING)
                .getResultList();
    }

    @Override
    public List<Registration> findPendingByEvent(final long eventId, final int page, final int pageSize) {
        // Dos pasos (1+1), como findByEventId() paginado: ids de la pagina, despues las inscripciones de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM Registration r WHERE r.event.id = :eventId AND r.status = :status "
                                + "ORDER BY r.registeredAt ASC, r.id ASC", Long.class)
                .setParameter("eventId", eventId)
                .setParameter("status", RegistrationStatus.PENDING)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, "r.registeredAt ASC, r.id ASC");
    }

    @Override
    public long countPending(final long eventId) {
        return countByEventAndStatus(eventId, RegistrationStatus.PENDING);
    }

    @Override
    public Map<Long, Long> countByEventsAndStatus(final Collection<Long> eventIds, final RegistrationStatus status) {
        if (eventIds.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<Long, Long> counts = new HashMap<>();
        final List<Object[]> rows = em.createQuery(
                        "SELECT r.event.id, COUNT(r) FROM Registration r "
                                + "WHERE r.event.id IN :eventIds AND r.status = :status GROUP BY r.event.id",
                        Object[].class)
                .setParameter("eventIds", eventIds)
                .setParameter("status", status)
                .getResultList();
        for (final Object[] row : rows) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
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
