// Implementa el contrato EventReportDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatusCounts;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EventReportJpaDao implements EventReportDao {

    // Un reporte "abierto" todavia no llego a una resolucion final (DISMISSED/ACTIONED).
    private static final List<ReportStatus> OPEN_STATUSES =
            Arrays.asList(ReportStatus.PENDING, ReportStatus.CHANGES_REQUESTED);
    private static final List<ReportStatus> RESOLVED_STATUSES =
            Arrays.asList(ReportStatus.DISMISSED, ReportStatus.ACTIONED);
    // Mas reciente actividad primero; el id desempata (ver EventReport.getLastActivityAt()).
    private static final String SEARCH_ORDER_BY =
            " ORDER BY COALESCE(r.resolvedAt, r.changesRequestedAt, r.createdAt) DESC, r.id DESC";

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
    public List<EventReport> search(final String query, final ReportStatusFilter filter, final int page,
                                     final int pageSize) {
        final Map<String, Object> params = new HashMap<>();
        final String where = searchWhere(query, filter, params);
        // Dos pasos (1+1): ids de la pagina, despues las entidades de esos ids.
        final TypedQuery<Long> idsQuery =
                em.createQuery("SELECT r.id FROM EventReport r" + where + SEARCH_ORDER_BY, Long.class);
        params.forEach(idsQuery::setParameter);
        idsQuery.setFirstResult((page - 1) * pageSize);
        idsQuery.setMaxResults(pageSize);
        return loadInOrder(idsQuery.getResultList());
    }

    @Override
    public long countSearch(final String query, final ReportStatusFilter filter) {
        final Map<String, Object> params = new HashMap<>();
        final String where = searchWhere(query, filter, params);
        final TypedQuery<Long> countQuery =
                em.createQuery("SELECT COUNT(r) FROM EventReport r" + where, Long.class);
        params.forEach(countQuery::setParameter);
        return countQuery.getSingleResult();
    }

    // El WHERE de search()/countSearch(): el mismo filtro de texto y de pestaña en los dos, para que el total y
    // la pagina nunca se desincronicen. "!" como caracter de escape del LIKE (ver likeContains()), igual que
    // el buscador de titulo de "Todos los eventos" (EventJpaDao.ADMIN_TITLE_FILTER).
    private static String searchWhere(final String query, final ReportStatusFilter filter,
                                       final Map<String, Object> params) {
        final List<String> conditions = new ArrayList<>();
        switch (filter) {
            case NEW:
                params.put("pending", ReportStatus.PENDING);
                conditions.add("r.status = :pending AND r.viewedAt IS NULL");
                break;
            case IN_REVIEW:
                params.put("pending", ReportStatus.PENDING);
                conditions.add("r.status = :pending AND r.viewedAt IS NOT NULL");
                break;
            case CHANGES_REQUESTED:
                params.put("changesRequested", ReportStatus.CHANGES_REQUESTED);
                conditions.add("r.status = :changesRequested");
                break;
            case RESOLVED:
                params.put("resolvedStatuses", RESOLVED_STATUSES);
                conditions.add("r.status IN :resolvedStatuses");
                break;
            default:
                // ALL: sin condicion de estado.
        }
        if (query != null && !query.trim().isEmpty()) {
            params.put("q", likeContains(query.trim()));
            conditions.add("(LOWER(r.event.title) LIKE :q ESCAPE '!' OR LOWER(r.reporter.email) LIKE :q ESCAPE '!' "
                    + "OR LOWER(r.reason) LIKE :q ESCAPE '!')");
        }
        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    // Mismo criterio de escape que EventJpaDao.likeContains(): "!" como caracter de escape, asi un "%"/"_"/"!"
    // que haya escrito la persona busca ese caracter literal, no es un comodin.
    private static String likeContains(final String text) {
        return "%" + text.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @Override
    public ReportStatusCounts countStatuses() {
        final Object[] row = (Object[]) em.createQuery(
                        "SELECT COUNT(r), "
                                + "SUM(CASE WHEN r.status = :pending AND r.viewedAt IS NULL THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :pending AND r.viewedAt IS NOT NULL THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status = :changesRequested THEN 1 ELSE 0 END), "
                                + "SUM(CASE WHEN r.status IN :resolvedStatuses THEN 1 ELSE 0 END) "
                                + "FROM EventReport r")
                .setParameter("pending", ReportStatus.PENDING)
                .setParameter("changesRequested", ReportStatus.CHANGES_REQUESTED)
                .setParameter("resolvedStatuses", RESOLVED_STATUSES)
                .getSingleResult();
        return new ReportStatusCounts(((Number) row[0]).longValue(), countOf(row[1]), countOf(row[2]),
                countOf(row[3]), countOf(row[4]));
    }

    // SUM sobre un CASE que nunca matcheo da NULL (ej. tabla vacia), no 0.
    private static long countOf(final Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    @Override
    public boolean markViewed(final long reportId, final long adminUserId) {
        return em.createQuery(
                        "UPDATE EventReport r SET r.viewedAt = :now, r.viewedBy = :admin "
                                + "WHERE r.id = :reportId AND r.viewedAt IS NULL")
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("admin", em.getReference(User.class, adminUserId))
                .setParameter("reportId", reportId)
                .executeUpdate() == 1;
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
