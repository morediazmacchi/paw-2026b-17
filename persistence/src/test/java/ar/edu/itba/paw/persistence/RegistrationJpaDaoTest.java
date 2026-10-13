package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class RegistrationJpaDaoTest {

    private static final String EMAIL = "invitado@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private RegistrationDao registrationDao;

    private static final long DAY = 86_400_000L;

    private JdbcTemplate jdbcTemplate;
    private long eventId;
    private long userId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        eventId = insertEvent();
        userId = insertUser(EMAIL, NAME);
    }

    // El evento no necesita categoria para estos tests (son de inscripciones,
    // no de categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una.
    private long insertEvent() {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Recital", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertEventAt(final long millisFromNow) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Evento", new Timestamp(System.currentTimeMillis() + millisFromNow), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long registerUserAtEvent(final long eventId) {
        return insertRegistration(eventId, EMAIL + eventId, userId, NAME, RegistrationStatus.CONFIRMED);
    }

    // registration.user_id es una FK de verdad contra users.id: cada email
    // "de utileria" de estos tests necesita su propia fila en users.
    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertRegistrationAt(final long eventId, final String email, final long userId, final String name,
                                       final RegistrationStatus status, final Timestamp registeredAt) {
        jdbcTemplate.update(
                "INSERT INTO registration (event_id, email, user_id, name, status, registered_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                eventId, email, userId, name, status.name(), registeredAt);
        return jdbcTemplate.queryForObject("SELECT id FROM registration ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertRegistration(final long eventId, final String email, final long userId, final String name,
                                     final RegistrationStatus status) {
        return insertRegistrationAt(eventId, email, userId, name, status, new Timestamp(System.currentTimeMillis()));
    }

    private Registration someRegistration(final long eventId, final long userId) {
        final Registration registration = new Registration(em.getReference(Event.class, eventId), em.getReference(User.class, userId), EMAIL, NAME, RegistrationStatus.CONFIRMED);
        return registration;
    }

    // ---- create() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        // Arrange
        final Registration toCreate = someRegistration(eventId, userId);

        // Act
        final Registration created = registrationDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateInsertsTheRegistrationRow() {
        // Arrange
        final Registration toCreate = someRegistration(eventId, userId);

        // Act
        final Registration created = registrationDao.create(toCreate);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + created.getId() + " AND event_id = " + eventId
                        + " AND email = '" + EMAIL + "' AND name = '" + NAME + "' AND status = 'CONFIRMED'"));
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEventAndEmailThrowsDataAccessException() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        registrationDao.create(someRegistration(eventId, userId));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdReturnsTheStoredRegistration() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(NAME, found.get().getName());
        assertEquals(RegistrationStatus.CONFIRMED, found.get().getStatus());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- countConfirmed() ----

    @Test
    public void testCountConfirmedReturnsZeroWhenNobodyIsRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countConfirmed(eventId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountConfirmedCountsOnlyConfirmedStatus() {
        // Arrange
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final long waitlistedUserId = insertUser("espera@mail.com", NAME);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "espera@mail.com", waitlistedUserId, NAME, RegistrationStatus.WAITLISTED);

        // Act
        final long count = registrationDao.countConfirmed(eventId);

        // Assert
        assertEquals(1, count);
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsEmptyWhenItDoesNotExist() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final Optional<Registration> found = registrationDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindByIdReturnsTheStoredRegistration() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final Optional<Registration> found = registrationDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(EMAIL, found.get().getEmail());
    }

    @Test
    public void testFindByIdReturnsARegistrationWhoseEventAndUserIdsCanBeReadOutsideTheSession() {
        // Arrange: la inscripcion guarda el evento y el usuario como relaciones LAZY (representantes
        // vacios que solo conocen su id). Se "cierra la sesion" (em.clear) para que esos
        // representantes ya no puedan ir a la base: leer el id tiene que seguir andando, porque es lo
        // que usan los servicios y los JSP despues de que la transaccion termino.
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final Registration found = registrationDao.findById(id).get();
        em.clear();

        // Act
        final long foundEventId = found.getEventId();
        final Long foundUserId = found.getUserId();

        // Assert
        assertEquals(eventId, foundEventId);
        assertEquals(Long.valueOf(userId), foundUserId);
    }

    // ---- findByUserId() ("Mis inscripciones") ----

    @Test
    public void testFindByUserIdReturnsOnlyThatUsersRegistrations() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByUserIdReturnsEmptyWhenNeverRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId, 1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindByUserIdListsUpcomingEventsBeforePastOnes() {
        // Arrange: a proposito la del evento pasado se inserta primero
        final long pastEvent = insertEventAt(-2 * DAY);
        final long upcomingEvent = insertEventAt(2 * DAY);
        registerUserAtEvent(pastEvent);
        registerUserAtEvent(upcomingEvent);

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId, 1, 10);

        // Assert
        assertEquals(2, found.size());
        assertEquals(upcomingEvent, found.get(0).getEventId());
        assertEquals(pastEvent, found.get(1).getEventId());
    }

    @Test
    public void testFindByUserIdOrdersUpcomingNearestFirstAndPastMostRecentFirst() {
        // Arrange
        final long upcomingFar = insertEventAt(3 * DAY);
        final long pastOld = insertEventAt(-5 * DAY);
        final long upcomingNear = insertEventAt(1 * DAY);
        final long pastRecent = insertEventAt(-1 * DAY);
        registerUserAtEvent(upcomingFar);
        registerUserAtEvent(pastOld);
        registerUserAtEvent(upcomingNear);
        registerUserAtEvent(pastRecent);

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId, 1, 10);

        // Assert
        assertEquals(4, found.size());
        assertEquals(upcomingNear, found.get(0).getEventId());
        assertEquals(upcomingFar, found.get(1).getEventId());
        assertEquals(pastRecent, found.get(2).getEventId());
        assertEquals(pastOld, found.get(3).getEventId());
    }

    @Test
    public void testFindByUserIdReturnsTheSecondPage() {
        // Arrange: 3 inscripciones de a 2 por pagina -> la pagina 2 es solo la ultima del orden
        final long first = insertEventAt(1 * DAY);
        final long second = insertEventAt(2 * DAY);
        final long third = insertEventAt(3 * DAY);
        registerUserAtEvent(third);
        registerUserAtEvent(first);
        registerUserAtEvent(second);

        // Act
        final List<Registration> page = registrationDao.findByUserId(userId, 2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(third, page.get(0).getEventId());
    }

    @Test
    public void testFindByUserIdReturnsNothingForAPageBeyondTheLast() {
        // Arrange
        registerUserAtEvent(insertEventAt(DAY));

        // Act
        final List<Registration> page = registrationDao.findByUserId(userId, 5, 2);

        // Assert
        assertTrue(page.isEmpty());
    }

    // ---- countByUserId() (badge de "Mis inscripciones") ----

    @Test
    public void testCountByUserIdCountsOnlyThatUsersRegistrations() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final long count = registrationDao.countByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountByUserIdReturnsZeroWhenNeverRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    // ---- findByEventId() ("Ver inscriptos" del organizador) ----

    @Test
    public void testFindByEventIdReturnsAllRegistrationsForThatEvent() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertEquals(2, found.size());
    }

    @Test
    public void testFindByEventIdDoesNotReturnRegistrationsOfOtherEvents() {
        // Arrange
        final long otherEventId = insertEvent();
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(otherEventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventIdReturnsEmptyWhenNobodyIsRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheRegistration() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        registrationDao.delete(id);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration", "id = " + id));
    }

    // ---- countPending() ----

    @Test
    public void testCountPendingReturnsZeroWhenNobodyIsWaitingForReview() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countPending(eventId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountPendingCountsOnlyPendingStatus() {
        // Arrange
        final long pendingUserId = insertUser("pendiente@mail.com", NAME);
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final long rejectedUserId = insertUser("rechazado@mail.com", NAME);
        insertRegistration(eventId, "pendiente@mail.com", pendingUserId, NAME, RegistrationStatus.PENDING);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "rechazado@mail.com", rejectedUserId, NAME, RegistrationStatus.REJECTED);

        // Act
        final long count = registrationDao.countPending(eventId);

        // Assert
        assertEquals(1, count);
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventReturnsEmptyWhenNobodyIsWaitingForReview() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> pending = registrationDao.findPendingByEvent(eventId);

        // Assert
        assertTrue(pending.isEmpty());
    }

    @Test
    public void testFindPendingByEventReturnsOnlyPendingOrderedByOldestFirst() {
        // Arrange
        final long firstUserId = insertUser("primero@mail.com", NAME);
        final long secondUserId = insertUser("segundo@mail.com", NAME);
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final Timestamp antes = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp despues = new Timestamp(System.currentTimeMillis() - 1000);
        insertRegistrationAt(eventId, "primero@mail.com", firstUserId, NAME, RegistrationStatus.PENDING, antes);
        insertRegistrationAt(eventId, "segundo@mail.com", secondUserId, NAME, RegistrationStatus.PENDING, despues);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> pending = registrationDao.findPendingByEvent(eventId);

        // Assert
        assertEquals(2, pending.size());
        assertEquals("primero@mail.com", pending.get(0).getEmail());
        assertEquals("segundo@mail.com", pending.get(1).getEmail());
    }

    // ---- updateStatus() ----

    @Test
    public void testUpdateStatusChangesTheStatusOfThatRegistration() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);

        // Act
        registrationDao.updateStatus(id, RegistrationStatus.CONFIRMED);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + id + " AND status = 'CONFIRMED'"));
    }

    @Test
    public void testUpdateStatusLeavesTheOtherRegistrationsAlone() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);
        final long otherUserId = insertUser("otra@mail.com", "Otra");
        final long otherId = insertRegistration(eventId, "otra@mail.com", otherUserId, "Otra",
                RegistrationStatus.PENDING);

        // Act
        registrationDao.updateStatus(id, RegistrationStatus.REJECTED);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + otherId + " AND status = 'PENDING'"));
    }

    // ---- findNextWaitlisted() ----

    private long insertWaitlistedAt(final long eventId, final String email, final long millisAgo) {
        final long waiterId = insertUser(email, "Espera");
        return insertRegistrationAt(eventId, email, waiterId, "Espera", RegistrationStatus.WAITLISTED,
                new Timestamp(System.currentTimeMillis() - millisAgo));
    }

    @Test
    public void testFindNextWaitlistedOrdersByWhoArrivedFirst() {
        // Arrange
        final long last = insertWaitlistedAt(eventId, "tercero@mail.com", 1000);
        final long first = insertWaitlistedAt(eventId, "primero@mail.com", 3000);
        final long second = insertWaitlistedAt(eventId, "segundo@mail.com", 2000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 10);

        // Assert
        assertEquals(first, found.get(0).getId().longValue());
        assertEquals(second, found.get(1).getId().longValue());
        assertEquals(last, found.get(2).getId().longValue());
    }

    @Test
    public void testFindNextWaitlistedReturnsAtMostTheLimit() {
        // Arrange
        final long first = insertWaitlistedAt(eventId, "primero@mail.com", 3000);
        insertWaitlistedAt(eventId, "segundo@mail.com", 2000);
        insertWaitlistedAt(eventId, "tercero@mail.com", 1000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 1);

        // Assert
        assertEquals(1, found.size());
        assertEquals(first, found.get(0).getId().longValue());
    }

    @Test
    public void testFindNextWaitlistedIgnoresOtherStatusesAndOtherEvents() {
        // Arrange
        final long waiting = insertWaitlistedAt(eventId, "espera@mail.com", 1000);
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherEventId = insertEvent();
        insertWaitlistedAt(otherEventId, "otro-evento@mail.com", 1000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(waiting, found.get(0).getId().longValue());
    }

    @Test
    public void testDeleteReturnsTrueWhenTheRegistrationExisted() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final boolean result = registrationDao.delete(id);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testDeleteReturnsFalseWhenThereIsNoSuchRegistration() {
        // Arrange
        // (no hay ninguna inscripcion guardada)

        // Act
        final boolean result = registrationDao.delete(999_999L);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUpdateStatusReturnsTrueWhenTheRegistrationExisted() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);

        // Act
        final boolean result = registrationDao.updateStatus(id, RegistrationStatus.CONFIRMED);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUpdateStatusReturnsFalseWhenThereIsNoSuchRegistration() {
        // Arrange
        // (no hay ninguna inscripcion guardada)

        // Act
        final boolean result = registrationDao.updateStatus(999_999L, RegistrationStatus.CONFIRMED);

        // Assert
        assertFalse(result);
    }
}
