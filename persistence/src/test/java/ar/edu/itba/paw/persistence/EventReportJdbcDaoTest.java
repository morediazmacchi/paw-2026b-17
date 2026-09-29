package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventReportJdbcDaoTest {

    private static final String REASON = "Contenido inapropiado";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventDao eventDao;

    @Autowired
    private EventReportDao eventReportDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;
    private long reporterId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.update("INSERT INTO category (name) VALUES ('Musica')");
        final long categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = 'Musica'", Long.class);
        final Event event = eventDao.create(new Event("Recital", "desc", new Date(), "Corrientes 1234",
                Barrio.PALERMO, null, new BigDecimal("1500"), 50, "creador@mail.com",
                Collections.singletonList(categoryId)));
        eventId = event.getId();
        reporterId = insertUser("reportero@mail.com");
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Reportero");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    public void testCreateAssignsAGeneratedId() {
        final EventReport created = eventReportDao.create(new EventReport(eventId, reporterId, REASON));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateDefaultsStatusToPending() {
        final EventReport created = eventReportDao.create(new EventReport(eventId, reporterId, REASON));

        final EventReport found = eventReportDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals(ReportStatus.PENDING, found.getStatus());
        assertTrue(found.isPending());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenItDoesNotExist() {
        assertFalse(eventReportDao.findById(-1).isPresent());
    }

    @Test
    public void testFindPendingReturnsOnlyPendingOldestFirst() {
        final EventReport first = eventReportDao.create(new EventReport(eventId, reporterId, "primero"));
        final long otherReporter = insertUser("otro@mail.com");
        final EventReport second = eventReportDao.create(new EventReport(eventId, otherReporter, "segundo"));
        eventReportDao.resolve(first.getId(), ReportStatus.DISMISSED, reporterId);

        final List<EventReport> pending = eventReportDao.findPending();

        assertEquals(1, pending.size());
        assertEquals(second.getId(), pending.get(0).getId());
    }

    @Test
    public void testExistsPendingByEventAndReporterIsTrueOnlyWhilePending() {
        final EventReport created = eventReportDao.create(new EventReport(eventId, reporterId, REASON));

        assertTrue(eventReportDao.existsPendingByEventAndReporter(eventId, reporterId));

        eventReportDao.resolve(created.getId(), ReportStatus.DISMISSED, reporterId);

        assertFalse(eventReportDao.existsPendingByEventAndReporter(eventId, reporterId));
    }

    @Test
    public void testResolveSetsStatusAndResolvedBy() {
        final EventReport created = eventReportDao.create(new EventReport(eventId, reporterId, REASON));
        final long adminId = insertUser("admin2@mail.com");

        eventReportDao.resolve(created.getId(), ReportStatus.ACTIONED, adminId);

        final EventReport found = eventReportDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals(ReportStatus.ACTIONED, found.getStatus());
        assertEquals(Long.valueOf(adminId), found.getResolvedByUserId());
        assertFalse(found.isPending());
    }

    @Test
    public void testResolveDoesNotTouchAnAlreadyResolvedReport() {
        final EventReport created = eventReportDao.create(new EventReport(eventId, reporterId, REASON));
        final long admin = insertUser("admin3@mail.com");
        eventReportDao.resolve(created.getId(), ReportStatus.DISMISSED, admin);

        // Otro admin llega tarde: no pisa la resolucion ya hecha.
        eventReportDao.resolve(created.getId(), ReportStatus.ACTIONED, admin);

        assertEquals(ReportStatus.DISMISSED, eventReportDao.findById(created.getId()).get().getStatus());
    }

    @Test
    public void testResolveAllPendingForEventResolvesEveryOneOfThem() {
        final long otherReporter = insertUser("otro2@mail.com");
        eventReportDao.create(new EventReport(eventId, reporterId, "uno"));
        eventReportDao.create(new EventReport(eventId, otherReporter, "dos"));
        final long admin = insertUser("admin4@mail.com");

        eventReportDao.resolveAllPendingForEvent(eventId, ReportStatus.ACTIONED, admin);

        assertTrue(eventReportDao.findPending().isEmpty());
    }
}
