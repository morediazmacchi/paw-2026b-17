package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.ReportStatusCounts;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventReportJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

    private static final String REASON = "Contenido inapropiado";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventReportDao eventReportDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;
    private long reporterId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        eventId = insertEvent();
        reporterId = insertUser("reportero@mail.com");
    }

    private long insertEvent() {
        return insertEventWithTitle("Recital");
    }

    private long insertEventWithTitle(final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    // El orden que importa es el de los ids que devuelve search(), no el contenido entero de cada EventReport.
    private static List<Long> idsOf(final List<EventReport> reports) {
        return reports.stream().map(EventReport::getId).collect(Collectors.toList());
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Reportero");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertPendingReportAt(final long eventId, final long reporterId, final String reason, final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO event_report (event_id, reporter_user_id, reason, created_at) VALUES (?, ?, ?, ?)",
                eventId, reporterId, reason, createdAt);
        return jdbcTemplate.queryForObject("SELECT id FROM event_report ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertPendingReport(final long eventId, final long reporterId, final String reason) {
        return insertPendingReportAt(eventId, reporterId, reason, new Timestamp(System.currentTimeMillis()));
    }

    private long insertEventCreatedBy(final long creatorId) {
        final long id = insertEvent();
        jdbcTemplate.update("UPDATE event SET creator_id = ? WHERE id = ?", creatorId, id);
        return id;
    }

    private long insertResolvedReport(final long eventId, final long reporterId, final String reason,
                                       final ReportStatus status, final long resolvedBy) {
        return insertResolvedReportAt(eventId, reporterId, reason, status, resolvedBy,
                new Timestamp(System.currentTimeMillis()));
    }

    private long insertResolvedReportAt(final long eventId, final long reporterId, final String reason,
                                         final ReportStatus status, final long resolvedBy, final Timestamp resolvedAt) {
        jdbcTemplate.update(
                "INSERT INTO event_report (event_id, reporter_user_id, reason, status, resolved_by, resolved_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                eventId, reporterId, reason, status.name(), resolvedBy, resolvedAt);
        return jdbcTemplate.queryForObject("SELECT id FROM event_report ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertChangesRequestedReportAt(final long eventId, final long reporterId, final String reason,
                                                 final String message, final long adminId, final Timestamp changesRequestedAt) {
        jdbcTemplate.update(
                "INSERT INTO event_report (event_id, reporter_user_id, reason, status, "
                        + "changes_requested_message, changes_requested_by, changes_requested_at) "
                        + "VALUES (?, ?, ?, 'CHANGES_REQUESTED', ?, ?, ?)",
                eventId, reporterId, reason, message, adminId, changesRequestedAt);
        return jdbcTemplate.queryForObject("SELECT id FROM event_report ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertChangesRequestedReport(final long eventId, final long reporterId, final String reason,
                                               final String message, final long adminId) {
        return insertChangesRequestedReportAt(eventId, reporterId, reason, message, adminId, new Timestamp(System.currentTimeMillis()));
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheReportRowAndReturnsItWithItsId() {
        // Arrange
        final EventReport toCreate = new EventReport(em.getReference(Event.class, eventId), em.getReference(User.class, reporterId), REASON);

        // Act
        final EventReport created = eventReportDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, reporter_user_id, reason FROM event_report WHERE id = ?", created.getId());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertEquals(reporterId, ((Number) row.get("reporter_user_id")).longValue());
        assertEquals(REASON, row.get("reason"));
    }

    @Test
    public void testCreateWithAReporterBuiltFromTheSessionStoresItsId() {
        // Arrange
        final User fromSession = new User("reporter@mail.com", "Rita");
        fromSession.setId(reporterId);
        final EventReport toCreate = new EventReport(em.getReference(Event.class, eventId), fromSession, REASON);

        // Act
        final EventReport created = eventReportDao.create(toCreate);

        // Assert
        final Long storedReporterId = jdbcTemplate.queryForObject(
                "SELECT reporter_user_id FROM event_report WHERE id = ?", Long.class, created.getId());
        assertEquals(reporterId, storedReporterId.longValue());
    }

    @Test
    public void testCreateDefaultsStatusToPending() {
        // Arrange
        final EventReport toCreate = new EventReport(em.getReference(Event.class, eventId), em.getReference(User.class, reporterId), REASON);

        // Act
        final EventReport created = eventReportDao.create(toCreate);

        // Assert
        final String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_report WHERE id = ?", String.class, created.getId());
        assertEquals("PENDING", status);
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsTheStoredReportAsPending() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);

        // Act
        final Optional<EventReport> found = eventReportDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
        assertEquals(reporterId, found.get().getReporterUserId());
        assertEquals(REASON, found.get().getReason());
        assertEquals(ReportStatus.PENDING, found.get().getStatus());
        assertTrue(found.get().isPending());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenItDoesNotExist() {
        // Arrange: no se inserta ningun reporte.

        // Act
        final Optional<EventReport> found = eventReportDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- search() / countSearch() ----

    @Test
    public void testSearchWithAllFilterReturnsEveryReportMostRecentActivityFirst() {
        // Arrange: un pendiente (createdAt), uno con cambios pedidos (changesRequestedAt) y uno resuelto
        // (resolvedAt), cada uno con su propia fecha de actividad, de la mas vieja a la mas nueva.
        final long now = System.currentTimeMillis();
        final long pendingId = insertPendingReportAt(eventId, reporterId, "pendiente", new Timestamp(now - 3000));
        final long admin = insertUser("admin-search@mail.com");
        final long changesId = insertChangesRequestedReportAt(eventId, insertUser("r2@mail.com"), "cambios",
                "corregi", admin, new Timestamp(now - 2000));
        final long resolvedId = insertResolvedReportAt(eventId, insertUser("r3@mail.com"), "resuelto",
                ReportStatus.DISMISSED, admin, new Timestamp(now - 1000));

        // Act
        final List<EventReport> found = eventReportDao.search(null, ReportStatusFilter.ALL, 1, 10);

        // Assert
        assertEquals(3, found.size());
        assertEquals(resolvedId, found.get(0).getId().longValue());
        assertEquals(changesId, found.get(1).getId().longValue());
        assertEquals(pendingId, found.get(2).getId().longValue());
    }

    @Test
    public void testSearchWithNewFilterReturnsOnlyPendingAndUnviewedReports() {
        // Arrange
        final long newId = insertPendingReport(eventId, reporterId, "nuevo");
        final long viewedId = insertPendingReport(eventId, insertUser("r2@mail.com"), "visto");
        eventReportDao.markViewed(viewedId, insertUser("admin-new@mail.com"));
        insertChangesRequestedReport(eventId, insertUser("r3@mail.com"), "cambios", "corregi",
                insertUser("admin-new2@mail.com"));

        // Act
        final List<EventReport> found = eventReportDao.search(null, ReportStatusFilter.NEW, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(newId, found.get(0).getId().longValue());
    }

    @Test
    public void testSearchWithInReviewFilterReturnsOnlyPendingAndViewedReports() {
        // Arrange
        insertPendingReport(eventId, reporterId, "nuevo");
        final long viewedId = insertPendingReport(eventId, insertUser("r2@mail.com"), "visto");
        eventReportDao.markViewed(viewedId, insertUser("admin-review@mail.com"));

        // Act
        final List<EventReport> found = eventReportDao.search(null, ReportStatusFilter.IN_REVIEW, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(viewedId, found.get(0).getId().longValue());
    }

    @Test
    public void testSearchWithChangesRequestedFilterReturnsOnlyThoseReports() {
        // Arrange
        insertPendingReport(eventId, reporterId, "pendiente");
        final long changesId = insertChangesRequestedReport(eventId, insertUser("r2@mail.com"), "cambios",
                "corregi", insertUser("admin-cr@mail.com"));

        // Act
        final List<EventReport> found = eventReportDao.search(null, ReportStatusFilter.CHANGES_REQUESTED, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(changesId, found.get(0).getId().longValue());
    }

    @Test
    public void testSearchWithResolvedFilterIncludesBothDismissedAndActioned() {
        // Arrange
        final long admin = insertUser("admin-resolved@mail.com");
        final long dismissedId = insertResolvedReport(eventId, reporterId, "descartado", ReportStatus.DISMISSED, admin);
        final long actionedId = insertResolvedReport(eventId, insertUser("r2@mail.com"), "accionado",
                ReportStatus.ACTIONED, admin);
        insertPendingReport(eventId, insertUser("r3@mail.com"), "pendiente");

        // Act
        final List<EventReport> found = eventReportDao.search(null, ReportStatusFilter.RESOLVED, 1, 10);

        // Assert
        assertEquals(2, found.size());
        assertTrue(found.stream().map(EventReport::getId).anyMatch(id -> id == dismissedId));
        assertTrue(found.stream().map(EventReport::getId).anyMatch(id -> id == actionedId));
    }

    @Test
    public void testSearchByTextMatchesTheEventTitleTheReporterEmailOrTheReason() {
        // Arrange: un reporte distinto que matchea cada campo; un evento propio sin "recital" en el titulo
        // para byReporter/byReason, para que no matcheen por accidente via el evento compartido del setUp().
        final long concertEvent = insertEventWithTitle("Recital en el parque");
        final long byTitle = insertPendingReport(concertEvent, reporterId, "motivo generico");
        final long otherEvent = insertEventWithTitle("Taller de cocina");
        final long specificReporter = insertUser("particular@mail.com");
        final long byReporter = insertPendingReport(otherEvent, specificReporter, "motivo generico");
        final long byReason = insertPendingReport(otherEvent, insertUser("r4@mail.com"), "ubicacion incorrecta");

        // Act
        final List<Long> byTitleResult = idsOf(eventReportDao.search("recital", ReportStatusFilter.ALL, 1, 10));
        final List<Long> byReporterResult = idsOf(eventReportDao.search("particular", ReportStatusFilter.ALL, 1, 10));
        final List<Long> byReasonResult = idsOf(eventReportDao.search("incorrecta", ReportStatusFilter.ALL, 1, 10));

        // Assert
        assertEquals(Collections.singletonList(byTitle), byTitleResult);
        assertEquals(Collections.singletonList(byReporter), byReporterResult);
        assertEquals(Collections.singletonList(byReason), byReasonResult);
    }

    @Test
    public void testSearchTreatsPercentAndUnderscoreInTheQueryAsLiteralCharacters() {
        // Arrange: si el % fuera un comodin, "100%" tambien encontraria "cobraron 1000 pesos de mas"
        final long withPercent = insertPendingReport(eventId, reporterId, "Cobraron 100% de mas");
        insertPendingReport(eventId, insertUser("r2@mail.com"), "cobraron 1000 pesos de mas");

        // Act
        final List<Long> found = idsOf(eventReportDao.search("100%", ReportStatusFilter.ALL, 1, 10));

        // Assert
        assertEquals(Collections.singletonList(withPercent), found);
    }

    @Test
    public void testSearchPagesTheResults() {
        // Arrange: 3 reportes de a 2 por pagina, cada uno con su propia actividad
        final long now = System.currentTimeMillis();
        final long first = insertPendingReportAt(eventId, reporterId, "primero", new Timestamp(now - 3000));
        final long second = insertPendingReportAt(eventId, insertUser("r2@mail.com"), "segundo", new Timestamp(now - 2000));
        final long third = insertPendingReportAt(eventId, insertUser("r3@mail.com"), "tercero", new Timestamp(now - 1000));

        // Act
        final List<Long> pageOne = idsOf(eventReportDao.search(null, ReportStatusFilter.ALL, 1, 2));
        final List<Long> pageTwo = idsOf(eventReportDao.search(null, ReportStatusFilter.ALL, 2, 2));

        // Assert
        assertEquals(Arrays.asList(third, second), pageOne);
        assertEquals(Collections.singletonList(first), pageTwo);
    }

    @Test
    public void testCountSearchMatchesTheSameReportsThatSearchReturns() {
        // Arrange
        insertPendingReport(eventId, reporterId, "pendiente");
        insertChangesRequestedReport(eventId, insertUser("r2@mail.com"), "cambios", "corregi",
                insertUser("admin-count-search@mail.com"));

        // Act
        final long countAll = eventReportDao.countSearch(null, ReportStatusFilter.ALL);
        final long countChangesRequested = eventReportDao.countSearch(null, ReportStatusFilter.CHANGES_REQUESTED);

        // Assert
        assertEquals(2, countAll);
        assertEquals(1, countChangesRequested);
    }

    // ---- countStatuses() ----

    @Test
    public void testCountStatusesCountsEachTabSeparately() {
        // Arrange: uno nuevo, uno en revision, uno con cambios pedidos y uno resuelto
        insertPendingReport(eventId, reporterId, "nuevo");
        final long viewedId = insertPendingReport(eventId, insertUser("r2@mail.com"), "en revision");
        eventReportDao.markViewed(viewedId, insertUser("admin-counts@mail.com"));
        insertChangesRequestedReport(eventId, insertUser("r3@mail.com"), "cambios", "corregi",
                insertUser("admin-counts2@mail.com"));
        insertResolvedReport(eventId, insertUser("r4@mail.com"), "resuelto", ReportStatus.ACTIONED,
                insertUser("admin-counts3@mail.com"));

        // Act
        final ReportStatusCounts counts = eventReportDao.countStatuses();

        // Assert
        assertEquals(4, counts.getTotal());
        assertEquals(1, counts.getNuevos());
        assertEquals(1, counts.getEnRevision());
        assertEquals(1, counts.getModificacionSolicitada());
        assertEquals(1, counts.getResueltos());
    }

    @Test
    public void testCountStatusesOfAnEmptyTableIsAllZeroes() {
        // Arrange: no se inserta ningun reporte.

        // Act
        final ReportStatusCounts counts = eventReportDao.countStatuses();

        // Assert
        assertEquals(0, counts.getTotal());
        assertEquals(0, counts.getNuevos());
    }

    // ---- markViewed() ----

    @Test
    public void testMarkViewedSetsViewedAtAndViewedByAndReturnsTrue() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin-viewed@mail.com");

        // Act
        final boolean result = eventReportDao.markViewed(id, adminId);

        // Assert
        assertTrue(result);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT viewed_by FROM event_report WHERE id = ?", id);
        assertEquals(adminId, ((Number) row.get("viewed_by")).longValue());
    }

    @Test
    public void testMarkViewedTwiceDoesNotOverwriteTheFirstViewerAndReturnsFalseTheSecondTime() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long firstAdmin = insertUser("admin-first@mail.com");
        eventReportDao.markViewed(id, firstAdmin);

        // Act
        final boolean secondResult = eventReportDao.markViewed(id, insertUser("admin-second@mail.com"));

        // Assert
        assertFalse(secondResult);
        final Long viewedBy = jdbcTemplate.queryForObject(
                "SELECT viewed_by FROM event_report WHERE id = ?", Long.class, id);
        assertEquals(firstAdmin, viewedBy.longValue());
    }

    @Test
    public void testMarkViewedOfANonexistentReportReturnsFalse() {
        // Act
        final boolean result = eventReportDao.markViewed(-1, insertUser("admin-missing@mail.com"));

        // Assert
        assertFalse(result);
    }

    // ---- existsPendingByEventAndReporter() ----

    @Test
    public void testExistsPendingByEventAndReporterIsTrueWhilePending() {
        // Arrange
        insertPendingReport(eventId, reporterId, REASON);

        // Act
        final boolean result = eventReportDao.existsPendingByEventAndReporter(eventId, reporterId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testExistsPendingByEventAndReporterIsFalseAfterResolved() {
        // Arrange
        insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, reporterId);

        // Act
        final boolean result = eventReportDao.existsPendingByEventAndReporter(eventId, reporterId);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testExistsPendingByEventAndReporterIsAlsoTrueWhileChangesAreRequested() {
        // Arrange
        final long adminId = insertUser("admin-exists@mail.com");
        insertChangesRequestedReport(eventId, reporterId, REASON, "corregi la direccion", adminId);

        // Act
        final boolean result = eventReportDao.existsPendingByEventAndReporter(eventId, reporterId);

        // Assert
        assertTrue(result);
    }

    // ---- requestChanges() ----

    @Test
    public void testRequestChangesMovesAPendingReportToChangesRequestedAndReturnsTrue() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin-request@mail.com");

        // Act
        final boolean result = eventReportDao.requestChanges(id, "corregi la direccion", adminId);

        // Assert
        assertTrue(result);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, changes_requested_message, changes_requested_by, changes_requested_at "
                        + "FROM event_report WHERE id = ?", id);
        assertEquals("CHANGES_REQUESTED", row.get("status"));
        assertEquals("corregi la direccion", row.get("changes_requested_message"));
        assertEquals(adminId, ((Number) row.get("changes_requested_by")).longValue());
        assertTrue(row.get("changes_requested_at") != null);
    }

    @Test
    public void testRequestChangesDoesNotTouchAReportThatAlreadyHasChangesRequested() {
        // Arrange
        final long admin = insertUser("admin-request2@mail.com");
        final long id = insertChangesRequestedReport(eventId, reporterId, REASON, "primer mensaje", admin);

        // Act
        // Otro admin llega tarde: no tiene que pisar el pedido ya hecho.
        eventReportDao.requestChanges(id, "segundo mensaje", admin);

        // Assert
        final String message = jdbcTemplate.queryForObject(
                "SELECT changes_requested_message FROM event_report WHERE id = ?", String.class, id);
        assertEquals("primer mensaje", message);
    }

    // ---- resolve() ----

    @Test
    public void testResolveSetsStatusAndResolvedByAndReturnsTrue() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin2@mail.com");

        // Act
        final boolean result = eventReportDao.resolve(id, ReportStatus.ACTIONED, adminId);

        // Assert
        assertTrue(result);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, resolved_by FROM event_report WHERE id = ?", id);
        assertEquals("ACTIONED", row.get("status"));
        assertEquals(adminId, ((Number) row.get("resolved_by")).longValue());
    }

    @Test
    public void testResolveDoesNotTouchAnAlreadyResolvedReportAndReturnsFalse() {
        // Arrange
        final long admin = insertUser("admin3@mail.com");
        final long id = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, admin);

        // Act
        // Otro admin llega tarde: no tiene que pisar la resolucion ya hecha.
        final boolean result = eventReportDao.resolve(id, ReportStatus.ACTIONED, admin);

        // Assert
        assertFalse(result);
        final String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_report WHERE id = ?", String.class, id);
        assertEquals("DISMISSED", status);
    }

    @Test
    public void testResolveAlsoWorksOnAReportWithChangesRequested() {
        // Arrange
        final long admin = insertUser("admin-resolve-cr@mail.com");
        final long id = insertChangesRequestedReport(eventId, reporterId, REASON, "corregi algo", admin);

        // Act
        eventReportDao.resolve(id, ReportStatus.DISMISSED, admin);

        // Assert
        final String status = jdbcTemplate.queryForObject(
                "SELECT status FROM event_report WHERE id = ?", String.class, id);
        assertEquals("DISMISSED", status);
    }

    // ---- resolveAllPendingForEvent() ----

    @Test
    public void testResolveAllPendingForEventResolvesEveryPendingOneAndReturnsHowMany() {
        // Arrange
        final long otherReporter = insertUser("otro2@mail.com");
        final long first = insertPendingReport(eventId, reporterId, "uno");
        final long second = insertPendingReport(eventId, otherReporter, "dos");
        final long admin = insertUser("admin4@mail.com");

        // Act
        final int result = eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        // Assert
        assertEquals(2, result);
        assertEquals(2, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_report", "id IN (" + first + "," + second + ") AND status = 'ACTIONED'"));
    }

    @Test
    public void testResolveAllPendingForEventAlsoResolvesOnesWithChangesRequested() {
        // Arrange
        final long otherReporter = insertUser("otro3@mail.com");
        final long admin = insertUser("admin5@mail.com");
        final long first = insertChangesRequestedReport(eventId, reporterId, "uno", "corregi algo", admin);
        final long second = insertPendingReport(eventId, otherReporter, "dos");

        // Act
        eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        // Assert
        assertEquals(2, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_report", "id IN (" + first + "," + second + ") AND status = 'ACTIONED'"));
    }

    @Test
    public void testResolveAllPendingForEventDoesNotTouchOtherEvents() {
        // Arrange
        final long otherEventId = insertEvent();
        final long ofThisEvent = insertPendingReport(eventId, reporterId, "de este evento");
        final long ofOtherEvent = insertPendingReport(otherEventId, reporterId, "de otro evento");
        final long admin = insertUser("admin6@mail.com");

        // Act
        eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_report", "id = " + ofThisEvent + " AND status = 'ACTIONED'"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_report", "id = " + ofOtherEvent + " AND status = 'PENDING'"));
    }

    @Test
    public void testRequestChangesOnAResolvedReportChangesNothingAndReturnsFalse() {
        // Arrange
        final long admin = insertUser("admin-ret2@mail.com");
        final long id = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, admin);

        // Act
        final boolean result = eventReportDao.requestChanges(id, "corregi la direccion", admin);

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_report",
                "id = " + id + " AND status = 'DISMISSED' AND changes_requested_message IS NULL"));
    }

    // ---- findTargetById() ----

    @Test
    public void testFindTargetByIdReturnsTheEventAndItsCreator() {
        // Arrange
        final long creatorId = insertUser("creador2@mail.com");
        final long reportedEvent = insertEventCreatedBy(creatorId);
        final long reportId = insertPendingReport(reportedEvent, reporterId, REASON);

        // Act
        final Optional<ReportTarget> target = eventReportDao.findTargetById(reportId);

        // Assert
        assertTrue(target.isPresent());
        assertEquals(reportedEvent, target.get().getEventId());
        assertEquals(Long.valueOf(creatorId), target.get().getCreatorId());
    }

    @Test
    public void testFindTargetByIdOfAnEventWithoutCreatorHasNoCreatorId() {
        // Arrange: insertEvent() no le pone creador (una fila vieja).
        final long reportId = insertPendingReport(eventId, reporterId, REASON);

        // Act
        final Optional<ReportTarget> target = eventReportDao.findTargetById(reportId);

        // Assert
        assertTrue(target.isPresent());
        assertNull(target.get().getCreatorId());
    }

    @Test
    public void testFindTargetByIdAlsoFindsAReportThatWasAlreadyResolved() {
        // Arrange: el estado del reporte no importa, solo a que evento apunta.
        final long adminId = insertUser("admin2@mail.com");
        final long reportId = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, adminId);

        // Act
        final Optional<ReportTarget> target = eventReportDao.findTargetById(reportId);

        // Assert
        assertTrue(target.isPresent());
        assertEquals(eventId, target.get().getEventId());
    }

    @Test
    public void testFindTargetByIdReturnsEmptyWhenTheReportDoesNotExist() {
        // Act
        final Optional<ReportTarget> target = eventReportDao.findTargetById(-1);

        // Assert
        assertFalse(target.isPresent());
    }
}
