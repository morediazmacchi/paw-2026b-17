package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Recital", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
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
        jdbcTemplate.update(
                "INSERT INTO event_report (event_id, reporter_user_id, reason, status, resolved_by, resolved_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                eventId, reporterId, reason, status.name(), resolvedBy, new Timestamp(System.currentTimeMillis()));
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
    public void testCreateAssignsAGeneratedId() {
        // Arrange
        final EventReport toCreate = new EventReport(em.getReference(Event.class, eventId), em.getReference(User.class, reporterId), REASON);

        // Act
        final EventReport created = eventReportDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateInsertsTheReportRow() {
        // Arrange
        final EventReport toCreate = new EventReport(em.getReference(Event.class, eventId), em.getReference(User.class, reporterId), REASON);

        // Act
        final EventReport created = eventReportDao.create(toCreate);

        // Assert
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

    // ---- findPending() ----

    @Test
    public void testFindPendingReturnsOnlyPendingOldestFirst() {
        // Arrange
        final Timestamp antes = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp despues = new Timestamp(System.currentTimeMillis() - 1000);
        final long first = insertPendingReportAt(eventId, reporterId, "primero", antes);
        final long otherReporter = insertUser("otro@mail.com");
        final long second = insertPendingReportAt(eventId, otherReporter, "segundo", despues);
        insertResolvedReport(eventId, reporterId, "ya resuelto", ReportStatus.DISMISSED, reporterId);

        // Act
        final List<EventReport> pending = eventReportDao.findPending(1, 10);

        // Assert
        assertEquals(2, pending.size());
        assertEquals(first, pending.get(0).getId().longValue());
        assertEquals(second, pending.get(1).getId().longValue());
    }

    @Test
    public void testFindPendingReturnsTheSecondPageOldestFirst() {
        // Arrange: 3 pendientes de a 2 por pagina -> la pagina 2 es solo el mas nuevo
        final long now = System.currentTimeMillis();
        insertPendingReportAt(eventId, reporterId, "primero", new Timestamp(now - 3000));
        insertPendingReportAt(eventId, insertUser("r2@mail.com"), "segundo", new Timestamp(now - 2000));
        final long third = insertPendingReportAt(eventId, insertUser("r3@mail.com"), "tercero", new Timestamp(now - 1000));

        // Act
        final List<EventReport> page = eventReportDao.findPending(2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(third, page.get(0).getId().longValue());
    }

    @Test
    public void testFindPendingReturnsNothingForAPageBeyondTheLast() {
        // Arrange
        insertPendingReport(eventId, reporterId, REASON);

        // Act
        final List<EventReport> page = eventReportDao.findPending(5, 2);

        // Assert
        assertTrue(page.isEmpty());
    }

    // ---- countPending() ----

    @Test
    public void testCountPendingCountsOnlyPendingReports() {
        // Arrange
        insertPendingReport(eventId, reporterId, REASON);
        insertPendingReport(eventId, insertUser("r2@mail.com"), REASON);
        insertResolvedReport(eventId, reporterId, "ya resuelto", ReportStatus.DISMISSED, reporterId);
        insertChangesRequestedReport(eventId, insertUser("r3@mail.com"), REASON, "corregi", reporterId);

        // Act
        final long count = eventReportDao.countPending();

        // Assert
        assertEquals(2, count);
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
    public void testRequestChangesMovesAPendingReportToChangesRequested() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin-request@mail.com");

        // Act
        eventReportDao.requestChanges(id, "corregi la direccion", adminId);

        // Assert
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

    // ---- findChangesRequested() ----

    @Test
    public void testFindChangesRequestedReturnsOnlyThoseOldestFirst() {
        // Arrange
        final long admin = insertUser("admin-find@mail.com");
        final Timestamp antes = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp despues = new Timestamp(System.currentTimeMillis() - 1000);
        final long otherReporter = insertUser("otro-cr@mail.com");
        final long first = insertChangesRequestedReportAt(eventId, reporterId, "primero", "mensaje 1", admin, antes);
        final long second = insertChangesRequestedReportAt(eventId, otherReporter, "segundo", "mensaje 2", admin, despues);
        insertPendingReport(eventId, reporterId, "un pendiente comun");

        // Act
        final List<EventReport> found = eventReportDao.findChangesRequested(1, 10);

        // Assert
        assertEquals(2, found.size());
        assertEquals(first, found.get(0).getId().longValue());
        assertEquals(second, found.get(1).getId().longValue());
    }

    @Test
    public void testFindChangesRequestedReturnsTheSecondPageOldestFirst() {
        // Arrange: 3 con cambios pedidos de a 2 por pagina -> la pagina 2 es solo el mas nuevo
        final long admin = insertUser("admin-page@mail.com");
        final long now = System.currentTimeMillis();
        insertChangesRequestedReportAt(eventId, reporterId, "primero", "m1", admin, new Timestamp(now - 3000));
        insertChangesRequestedReportAt(eventId, insertUser("c2@mail.com"), "segundo", "m2", admin, new Timestamp(now - 2000));
        final long third = insertChangesRequestedReportAt(eventId, insertUser("c3@mail.com"), "tercero", "m3", admin,
                new Timestamp(now - 1000));

        // Act
        final List<EventReport> page = eventReportDao.findChangesRequested(2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(third, page.get(0).getId().longValue());
    }

    @Test
    public void testFindChangesRequestedReturnsNothingForAPageBeyondTheLast() {
        // Arrange
        insertChangesRequestedReport(eventId, reporterId, REASON, "corregi", insertUser("admin-beyond@mail.com"));

        // Act
        final List<EventReport> page = eventReportDao.findChangesRequested(5, 2);

        // Assert
        assertTrue(page.isEmpty());
    }

    // ---- countChangesRequested() ----

    @Test
    public void testCountChangesRequestedCountsOnlyChangesRequestedReports() {
        // Arrange
        final long admin = insertUser("admin-count@mail.com");
        insertChangesRequestedReport(eventId, reporterId, "uno", "m1", admin);
        insertChangesRequestedReport(eventId, insertUser("c2@mail.com"), "dos", "m2", admin);
        insertPendingReport(eventId, insertUser("p1@mail.com"), "un pendiente comun");

        // Act
        final long count = eventReportDao.countChangesRequested();

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testFindChangesRequestedDoesNotIncludePlainPendingReports() {
        // Arrange
        insertPendingReport(eventId, reporterId, REASON);

        // Act
        final List<EventReport> found = eventReportDao.findChangesRequested(1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- resolve() ----

    @Test
    public void testResolveSetsStatusAndResolvedBy() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin2@mail.com");

        // Act
        eventReportDao.resolve(id, ReportStatus.ACTIONED, adminId);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, resolved_by FROM event_report WHERE id = ?", id);
        assertEquals("ACTIONED", row.get("status"));
        assertEquals(adminId, ((Number) row.get("resolved_by")).longValue());
    }

    @Test
    public void testResolveDoesNotTouchAnAlreadyResolvedReport() {
        // Arrange
        final long admin = insertUser("admin3@mail.com");
        final long id = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, admin);

        // Act
        // Otro admin llega tarde: no tiene que pisar la resolucion ya hecha.
        eventReportDao.resolve(id, ReportStatus.ACTIONED, admin);

        // Assert
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
    public void testResolveAllPendingForEventResolvesEveryPendingOne() {
        // Arrange
        final long otherReporter = insertUser("otro2@mail.com");
        final long first = insertPendingReport(eventId, reporterId, "uno");
        final long second = insertPendingReport(eventId, otherReporter, "dos");
        final long admin = insertUser("admin4@mail.com");

        // Act
        eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        // Assert
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
    public void testRequestChangesReturnsTrueForAPendingReport() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin-ret1@mail.com");

        // Act
        final boolean result = eventReportDao.requestChanges(id, "corregi la direccion", adminId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRequestChangesReturnsFalseForAReportThatIsNotPending() {
        // Arrange
        final long admin = insertUser("admin-ret2@mail.com");
        final long id = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, admin);

        // Act
        final boolean result = eventReportDao.requestChanges(id, "corregi la direccion", admin);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testResolveReturnsTrueForAnOpenReport() {
        // Arrange
        final long id = insertPendingReport(eventId, reporterId, REASON);
        final long adminId = insertUser("admin-ret3@mail.com");

        // Act
        final boolean result = eventReportDao.resolve(id, ReportStatus.ACTIONED, adminId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testResolveReturnsFalseForAnAlreadyResolvedReport() {
        // Arrange
        final long admin = insertUser("admin-ret4@mail.com");
        final long id = insertResolvedReport(eventId, reporterId, REASON, ReportStatus.DISMISSED, admin);

        // Act
        final boolean result = eventReportDao.resolve(id, ReportStatus.ACTIONED, admin);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testResolveAllPendingForEventReturnsHowManyReportsWereResolved() {
        // Arrange
        final long otherReporter = insertUser("otro-ret@mail.com");
        insertPendingReport(eventId, reporterId, "uno");
        insertPendingReport(eventId, otherReporter, "dos");
        final long admin = insertUser("admin-ret5@mail.com");

        // Act
        final int result = eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        // Assert
        assertEquals(2, result);
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
