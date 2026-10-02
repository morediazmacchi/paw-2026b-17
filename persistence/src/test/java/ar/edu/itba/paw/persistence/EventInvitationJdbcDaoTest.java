package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

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
public class EventInvitationJdbcDaoTest {

    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final String INVITEE_EMAIL = "invitado@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventInvitationDao invitationDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        eventId = insertEvent();
    }

    // El evento no necesita categoria para estos tests (son de invitaciones,
    // no de categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una.
    private long insertEvent() {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, visibility) "
                        + "VALUES (?, ?, ?, ?, 'PRIVATE')",
                "Fiesta privada", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", CREATOR_EMAIL);
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Invitado");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertInvitation(final long eventId, final String email, final Long userId) {
        jdbcTemplate.update(
                "INSERT INTO event_invitation (event_id, email, invited_by, user_id) VALUES (?, ?, ?, ?)",
                eventId, email, CREATOR_EMAIL, userId);
        return jdbcTemplate.queryForObject("SELECT id FROM event_invitation ORDER BY id DESC LIMIT 1", Long.class);
    }

    private static EventInvitation someInvitation(final long eventId) {
        return new EventInvitation(eventId, INVITEE_EMAIL, CREATOR_EMAIL);
    }

    // ---- create() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        // Arrange
        final EventInvitation toCreate = someInvitation(eventId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateInsertsTheInvitationRow() {
        // Arrange
        final EventInvitation toCreate = someInvitation(eventId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, email, invited_by FROM event_invitation WHERE id = ?", created.getId());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertEquals(INVITEE_EMAIL, row.get("email"));
        assertEquals(CREATOR_EMAIL, row.get("invited_by"));
    }

    @Test
    public void testCreateSetsCreatedAtAutomatically() {
        // Arrange
        final EventInvitation toCreate = someInvitation(eventId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        final Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM event_invitation WHERE id = ?", Timestamp.class, created.getId());
        assertTrue(createdAt != null);
    }

    @Test
    public void testCreatePersistsTheOptimisticallyLinkedUserId() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final EventInvitation toCreate = someInvitation(eventId);
        toCreate.setUserId(inviteeId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM event_invitation WHERE id = ?", Long.class, created.getId());
        assertEquals(Long.valueOf(inviteeId), storedUserId);
    }

    @Test
    public void testCreateLeavesUserIdNullWhenTheInviteeHasNoAccount() {
        // Arrange
        final EventInvitation toCreate = someInvitation(eventId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM event_invitation WHERE id = ?", Long.class, created.getId());
        assertNull(storedUserId);
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEventAndEmailThrowsDataAccessException() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        invitationDao.create(new EventInvitation(eventId, INVITEE_EMAIL, CREATOR_EMAIL));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findByEventAndEmail() ----

    @Test
    public void testFindByEventAndEmailReturnsTheStoredInvitation() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
        assertEquals(INVITEE_EMAIL, found.get().getEmail());
        assertEquals(CREATOR_EMAIL, found.get().getInvitedBy());
        assertTrue(found.get().getCreatedAt() != null);
    }

    @Test
    public void testFindByEventAndEmailReturnsEmptyWhenNotInvited() {
        // Arrange: no se inserta ninguna invitacion.

        // Act
        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdReturnsTheInvitation() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        insertInvitation(eventId, INVITEE_EMAIL, inviteeId);

        // Act
        final Optional<EventInvitation> found = invitationDao.findByEventAndUserId(eventId, inviteeId);

        // Assert
        assertTrue(found.isPresent());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotLinked() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        final Optional<EventInvitation> found = invitationDao.findByEventAndUserId(eventId, 999999L);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventReturnsOnlyThatEventsInvitations() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);
        final long otherEventId = insertEvent();
        insertInvitation(otherEventId, "otro@mail.com", null);

        // Act
        final List<EventInvitation> found = invitationDao.findByEvent(eventId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(INVITEE_EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventReturnsEmptyWhenNobodyWasInvited() {
        // Arrange: no se inserta ninguna invitacion.

        // Act
        final List<EventInvitation> found = invitationDao.findByEvent(eventId);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- linkUserId() ----

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedInvitation() {
        // Arrange
        final long id = insertInvitation(eventId, INVITEE_EMAIL, null);
        final long inviteeId = insertUser(INVITEE_EMAIL);

        // Act
        invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM event_invitation WHERE id = ?", Long.class, id);
        assertEquals(Long.valueOf(inviteeId), storedUserId);
    }

    @Test
    public void testLinkUserIdNeverOverwritesAnAlreadyLinkedInvitation() {
        // Arrange
        final long firstAccountId = insertUser(INVITEE_EMAIL);
        final long id = insertInvitation(eventId, INVITEE_EMAIL, firstAccountId);

        // Act
        invitationDao.linkUserId(INVITEE_EMAIL, firstAccountId + 999);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM event_invitation WHERE id = ?", Long.class, id);
        assertEquals(Long.valueOf(firstAccountId), storedUserId);
    }

    // ---- countOutstanding() ----

    private void insertRegistration(final long eventId, final String email) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, status) VALUES (?, ?, 'CONFIRMED')",
                eventId, email);
    }

    @Test
    public void testCountOutstandingCountsOnlyTheInvitationsNobodyHasUsedYet() {
        // Arrange
        // Una invitacion "usada" es la de quien ya se anoto (tiene registration): deja de
        // reservar un lugar aparte porque ahora el lugar es su inscripcion.
        insertInvitation(eventId, "sin-usar@mail.com", null);
        insertInvitation(eventId, "usada@mail.com", null);
        insertRegistration(eventId, "usada@mail.com");

        // Act
        final long count = invitationDao.countOutstanding(eventId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountOutstandingDoesNotCountTheInvitationsOfOtherEvents() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);
        final long otherEventId = insertEvent();
        insertInvitation(otherEventId, "otro@mail.com", null);

        // Act
        final long count = invitationDao.countOutstanding(otherEventId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountOutstandingIsZeroWhenTheEventHasNoInvitations() {
        // Arrange: el evento del setUp no tiene invitaciones.

        // Act
        final long count = invitationDao.countOutstanding(eventId);

        // Assert
        assertEquals(0, count);
    }

    // ---- findByUserId() ----

    private void insertInvitationAt(final long eventId, final String email, final long userId,
                                    final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO event_invitation (event_id, email, invited_by, user_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventId, email, CREATOR_EMAIL, userId, createdAt);
    }

    @Test
    public void testFindByUserIdReturnsOnlyTheInvitationsOfThatUser() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long otherUserId = insertUser("otro@mail.com");
        insertInvitation(eventId, INVITEE_EMAIL, inviteeId);
        insertInvitation(eventId, "otro@mail.com", otherUserId);

        // Act
        final List<EventInvitation> found = invitationDao.findByUserId(inviteeId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(INVITEE_EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByUserIdOrdersNewestFirst() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long otherEventId = insertEvent();
        insertInvitationAt(eventId, INVITEE_EMAIL, inviteeId, new Timestamp(System.currentTimeMillis() - 2000));
        insertInvitationAt(otherEventId, INVITEE_EMAIL, inviteeId, new Timestamp(System.currentTimeMillis()));

        // Act
        final List<EventInvitation> found = invitationDao.findByUserId(inviteeId);

        // Assert
        assertEquals(otherEventId, found.get(0).getEventId());
        assertEquals(eventId, found.get(1).getEventId());
    }

    @Test
    public void testFindByUserIdIgnoresInvitationsNotLinkedToAnAccountYet() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        final List<EventInvitation> found = invitationDao.findByUserId(inviteeId);

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testLinkUserIdReturnsHowManyInvitationsWereLinked() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);
        final long inviteeId = insertUser(INVITEE_EMAIL);

        // Act
        final int result = invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        // Assert
        assertEquals(1, result);
    }

    @Test
    public void testLinkUserIdReturnsZeroWhenNoInvitationWasWaiting() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);

        // Act
        final int result = invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        // Assert
        assertEquals(0, result);
    }
}
