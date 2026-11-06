// Implementa el contrato EventReportDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import java.util.Arrays;
import java.util.Date;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class EventReportJpaDao implements EventReportDao {

    // Un reporte "abierto" todavia no llego a una resolucion final (DISMISSED/ACTIONED).
    private static final List<ReportStatus> OPEN_STATUSES =
            Arrays.asList(ReportStatus.PENDING, ReportStatus.CHANGES_REQUESTED);

    @PersistenceContext
    private EntityManager em;

    @Override
    public EventReport create(final EventReport report) {
        // Ver RegistrationJpaDao.create(): de quien reporta solo hace falta el id.
        report.setReporter(em.getReference(User.class, report.getReporter().getId()));
        em.persist(report);
        return report;
    }

    @Override
    public Optional<EventReport> findById(final long id) {
        return Optional.ofNullable(em.find(EventReport.class, id));
    }

    @Override
    public Optional<ReportTarget> findTargetById(final long reportId) {
        // Proyeccion: solo el id del evento y el de su creador, no el reporte ni el evento. El
        // creator.id no hace JOIN con users: es la propia columna creator_id del evento.
        final List<Object[]> rows = em.createQuery(
                        "SELECT r.event.id, r.event.creator.id FROM EventReport r WHERE r.id = :id", Object[].class)
                .setParameter("id", reportId)
                .getResultList();
        return rows.stream()
                .map(row -> new ReportTarget((Long) row[0], (Long) row[1]))
                .findFirst();
    }

    @Override
    public List<EventReport> findPending(final int page, final int pageSize) {
        // id como desempate: sin el, dos reportes con la misma fecha podrian
        // cambiar de lugar entre una pagina y la siguiente.
        // Dos pasos (1+1): ids de la pagina, despues las entidades de esos ids.
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM EventReport r WHERE r.status = :status "
                                + "ORDER BY r.createdAt ASC, r.id ASC", Long.class)
                .setParameter("status", ReportStatus.PENDING)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadInOrder(ids);
    }

    @Override
    public long countPending() {
        return countByStatus(ReportStatus.PENDING);
    }

    @Override
    public List<EventReport> findChangesRequested(final int page, final int pageSize) {
        final List<Long> ids = em.createQuery(
                        "SELECT r.id FROM EventReport r WHERE r.status = :status "
                                + "ORDER BY r.changesRequestedAt ASC, r.id ASC", Long.class)
                .setParameter("status", ReportStatus.CHANGES_REQUESTED)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadInOrder(ids);
    }

    // Paso 2 del 1+1: carga los reportes de "ids" y los devuelve en el mismo orden que los ids.
    private List<EventReport> loadInOrder(final List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return PagedIds.inOrder(ids,
                em.createQuery("FROM EventReport r WHERE r.id IN :ids", EventReport.class)
                        .setParameter("ids", ids)
                        .getResultList(),
                EventReport::getId);
    }

    @Override
    public long countChangesRequested() {
        return countByStatus(ReportStatus.CHANGES_REQUESTED);
    }

    private long countByStatus(final ReportStatus status) {
        return em.createQuery("SELECT COUNT(r) FROM EventReport r WHERE r.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    @Override
    public boolean existsPendingByEventAndReporter(final long eventId, final long reporterUserId) {
        final long count = em.createQuery(
                        "SELECT COUNT(r) FROM EventReport r WHERE r.event.id = :eventId "
                                + "AND r.reporter.id = :reporterUserId AND r.status IN :statuses", Long.class)
                .setParameter("eventId", eventId)
                .setParameter("reporterUserId", reporterUserId)
                .setParameter("statuses", OPEN_STATUSES)
                .getSingleResult();
        return count > 0;
    }

    // Los tres UPDATE de abajo son bulk JPQL a proposito (no em.find + setter): la condicion de
    // estado en el WHERE tiene que evaluarse atomicamente en la base para que dos admins que
    // actuan a la vez sobre el mismo reporte no lo resuelvan dos veces. No actualizan las
    // entidades que ya esten cargadas en el EntityManager.

    @Override
    public boolean requestChanges(final long reportId, final String message, final long adminUserId) {
        return em.createQuery(
                        "UPDATE EventReport r SET r.status = :newStatus, r.changesRequestedMessage = :message, "
                                + "r.changesRequestedBy = :admin, r.changesRequestedAt = :now "
                                + "WHERE r.id = :reportId AND r.status = :pending")
                .setParameter("newStatus", ReportStatus.CHANGES_REQUESTED)
                .setParameter("message", message)
                .setParameter("admin", em.getReference(User.class, adminUserId))
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("reportId", reportId)
                .setParameter("pending", ReportStatus.PENDING)
                .executeUpdate() == 1;
    }

    @Override
    public boolean resolve(final long reportId, final ReportStatus status, final long resolvedByUserId) {
        return em.createQuery(
                        "UPDATE EventReport r SET r.status = :newStatus, r.resolvedBy = :resolvedBy, "
                                + "r.resolvedAt = :now WHERE r.id = :reportId AND r.status IN :open")
                .setParameter("newStatus", status)
                .setParameter("resolvedBy", em.getReference(User.class, resolvedByUserId))
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("reportId", reportId)
                .setParameter("open", OPEN_STATUSES)
                .executeUpdate() == 1;
    }

    @Override
    public int resolveAllPendingForEvent(final long eventId, final ReportStatus status, final long resolvedByUserId) {
        return em.createQuery(
                        "UPDATE EventReport r SET r.status = :newStatus, r.resolvedBy = :resolvedBy, "
                                + "r.resolvedAt = :now WHERE r.event.id = :eventId AND r.status IN :open")
                .setParameter("newStatus", status)
                .setParameter("resolvedBy", em.getReference(User.class, resolvedByUserId))
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("eventId", eventId)
                .setParameter("open", OPEN_STATUSES)
                .executeUpdate();
    }
}
