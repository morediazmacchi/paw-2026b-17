package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
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
public class RegistrationJdbcDaoTest {

    private static final String EMAIL = "invitado@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @Autowired
    private RegistrationDao registrationDao;

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

    private static Registration someRegistration(final long eventId, final long userId) {
        final Registration registration = new Registration(eventId, EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setUserId(userId);
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

    // ---- findByUserId() ("Mis inscripciones") ----

    @Test
    public void testFindByUserIdReturnsOnlyThatUsersRegistrations() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByUserIdReturnsEmptyWhenNeverRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> found = registrationDao.findByUserId(userId);

        // Assert
        assertTrue(found.isEmpty());
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
        assertEquals(first, found.get(0).getId());
        assertEquals(second, found.get(1).getId());
        assertEquals(last, found.get(2).getId());
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
        assertEquals(first, found.get(0).getId());
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
        assertEquals(waiting, found.get(0).getId());
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
