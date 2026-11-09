package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;
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
public class EventInvitationJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

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

    private EventInvitation someInvitation(final long eventId) {
        return new EventInvitation(em.getReference(Event.class, eventId), INVITEE_EMAIL, CREATOR_EMAIL);
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheInvitationRowAndReturnsItWithItsId() {
        // Arrange
        final EventInvitation toCreate = someInvitation(eventId);

        // Act
        final EventInvitation created = invitationDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
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
        toCreate.setUser(em.getReference(User.class, inviteeId));

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
        invitationDao.create(new EventInvitation(em.getReference(Event.class, eventId), INVITEE_EMAIL, CREATOR_EMAIL));

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
        final List<EventInvitation> found = invitationDao.findByEvent(eventId, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(INVITEE_EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventReturnsEmptyWhenNobodyWasInvited() {
        // Arrange: no se inserta ninguna invitacion.

        // Act
        final List<EventInvitation> found = invitationDao.findByEvent(eventId, 1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindByEventPagesTheInvitationsNewestFirstWithoutRepeating() {
        // Arrange: 3 invitaciones con fechas distintas, de a 2 por pagina
        final long u1 = insertUser("uno@mail.com");
        final long u2 = insertUser("dos@mail.com");
        final long u3 = insertUser("tres@mail.com");
        final long now = System.currentTimeMillis();
        insertInvitationAt(eventId, "uno@mail.com", u1, new Timestamp(now - 3000));
        insertInvitationAt(eventId, "dos@mail.com", u2, new Timestamp(now - 2000));
        insertInvitationAt(eventId, "tres@mail.com", u3, new Timestamp(now - 1000));

        // Act
        final List<EventInvitation> pageOne = invitationDao.findByEvent(eventId, 1, 2);
        final List<EventInvitation> pageTwo = invitationDao.findByEvent(eventId, 2, 2);

        // Assert: la mas nueva primero y la ultima pagina trae solo la que sobra
        assertEquals(2, pageOne.size());
        assertEquals("tres@mail.com", pageOne.get(0).getEmail());
        assertEquals("dos@mail.com", pageOne.get(1).getEmail());
        assertEquals(1, pageTwo.size());
        assertEquals("uno@mail.com", pageTwo.get(0).getEmail());
    }

    // ---- countByEvent() ----

    @Test
    public void testCountByEventCountsOnlyThatEventsInvitations() {
        // Arrange
        insertInvitation(eventId, INVITEE_EMAIL, null);
        insertInvitation(eventId, "otro@mail.com", null);
        final long otherEventId = insertEvent();
        insertInvitation(otherEventId, "tercero@mail.com", null);

        // Act
        final long count = invitationDao.countByEvent(eventId);

        // Assert
        assertEquals(2L, count);
    }

    @Test
    public void testCountByEventReturnsZeroWhenNobodyWasInvited() {
        // Arrange: no se inserta ninguna invitacion.

        // Act
        final long count = invitationDao.countByEvent(eventId);

        // Assert
        assertEquals(0L, count);
    }

    // ---- linkUserId() ----

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedInvitationAndReturnsHowMany() {
        // Arrange
        final long id = insertInvitation(eventId, INVITEE_EMAIL, null);
        final long inviteeId = insertUser(INVITEE_EMAIL);

        // Act
        final int result = invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        // Assert
        assertEquals(1, result);
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

    // ---- findInvitedEventsByUserId() ----

    private void insertInvitationAt(final long eventId, final String email, final long userId,
                                    final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO event_invitation (event_id, email, invited_by, user_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventId, email, CREATOR_EMAIL, userId, createdAt);
    }

    @Test
    public void testFindInvitedEventsByUserIdReturnsOnlyTheEventsThatUserWasInvitedTo() {
        // Arrange: el evento de otra persona invitada y un evento sin invitaciones son los senuelos
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long otherUserId = insertUser("otro@mail.com");
        final long otherEventId = insertEvent();
        insertEvent();
        insertInvitation(eventId, INVITEE_EMAIL, inviteeId);
        insertInvitation(otherEventId, "otro@mail.com", otherUserId);

        // Act
        final List<Event> found = invitationDao.findInvitedEventsByUserId(inviteeId, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(Long.valueOf(eventId), found.get(0).getId());
    }

    @Test
    public void testFindInvitedEventsByUserIdOrdersMostRecentInvitationFirst() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long otherEventId = insertEvent();
        insertInvitationAt(eventId, INVITEE_EMAIL, inviteeId, new Timestamp(System.currentTimeMillis() - 2000));
        insertInvitationAt(otherEventId, INVITEE_EMAIL, inviteeId, new Timestamp(System.currentTimeMillis()));

        // Act
        final List<Event> found = invitationDao.findInvitedEventsByUserId(inviteeId, 1, 10);

        // Assert
        assertEquals(Long.valueOf(otherEventId), found.get(0).getId());
        assertEquals(Long.valueOf(eventId), found.get(1).getId());
    }

    @Test
    public void testFindInvitedEventsByUserIdIgnoresInvitationsNotLinkedToAnAccountYet() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        final List<Event> found = invitationDao.findInvitedEventsByUserId(inviteeId, 1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindInvitedEventsByUserIdReturnsTheSecondPageMostRecentFirst() {
        // Arrange: 3 invitaciones de a 2 por pagina -> la pagina 2 es solo la mas vieja
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long oldestEvent = insertEvent();
        final long middleEvent = insertEvent();
        final long newestEvent = insertEvent();
        final long now = System.currentTimeMillis();
        insertInvitationAt(oldestEvent, INVITEE_EMAIL, inviteeId, new Timestamp(now - 3000));
        insertInvitationAt(middleEvent, INVITEE_EMAIL, inviteeId, new Timestamp(now - 2000));
        insertInvitationAt(newestEvent, INVITEE_EMAIL, inviteeId, new Timestamp(now - 1000));

        // Act
        final List<Event> page = invitationDao.findInvitedEventsByUserId(inviteeId, 2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(Long.valueOf(oldestEvent), page.get(0).getId());
    }

    @Test
    public void testFindInvitedEventsByUserIdReturnsNothingForAPageBeyondTheLast() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        insertInvitation(eventId, INVITEE_EMAIL, inviteeId);

        // Act
        final List<Event> page = invitationDao.findInvitedEventsByUserId(inviteeId, 5, 2);

        // Assert
        assertTrue(page.isEmpty());
    }

    // ---- countByUserId() ----

    @Test
    public void testCountByUserIdCountsOnlyTheInvitationsOfThatUser() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        final long otherUserId = insertUser("otro@mail.com");
        insertInvitation(eventId, INVITEE_EMAIL, inviteeId);
        insertInvitation(eventId, "otro@mail.com", otherUserId);

        // Act
        final long count = invitationDao.countByUserId(inviteeId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountByUserIdIgnoresInvitationsNotLinkedToAnAccountYet() {
        // Arrange
        final long inviteeId = insertUser(INVITEE_EMAIL);
        insertInvitation(eventId, INVITEE_EMAIL, null);

        // Act
        final long count = invitationDao.countByUserId(inviteeId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testLinkUserIdWithNoInvitationForThatEmailLinksNothingAndReturnsZero() {
        // Arrange: una invitacion de otro email, sin cuenta. Tiene que seguir sin enlazar.
        final long other = insertInvitation(eventId, "otra@mail.com", null);
        final long inviteeId = insertUser(INVITEE_EMAIL);

        // Act
        final int result = invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        // Assert
        assertEquals(0, result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_invitation",
                "id = " + other + " AND user_id IS NULL"));
    }

    // ---- existsByEventAndUserId() ----

    @Test
    public void testExistsByEventAndUserIdIsTrueForThatUsersInvitationToThatEvent() {
        // Arrange: la buscada, mas senuelos de otro usuario en este evento y del mismo usuario en otro
        final long eventId = insertEvent();
        final long otherEventId = insertEvent();
        final long userId = insertUser("invitado@mail.com");
        final long otherUserId = insertUser("otro@mail.com");
        insertInvitation(eventId, "invitado@mail.com", userId);
        insertInvitation(eventId, "otro@mail.com", otherUserId);
        insertInvitation(otherEventId, "invitado@mail.com", userId);

        // Act
        final boolean result = invitationDao.existsByEventAndUserId(eventId, userId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testExistsByEventAndUserIdIsFalseWhenOnlyOthersWereInvited() {
        // Arrange
        final long eventId = insertEvent();
        final long otherEventId = insertEvent();
        final long userId = insertUser("invitado@mail.com");
        final long otherUserId = insertUser("otro@mail.com");
        insertInvitation(eventId, "otro@mail.com", otherUserId);
        insertInvitation(otherEventId, "invitado@mail.com", userId);

        // Act
        final boolean result = invitationDao.existsByEventAndUserId(eventId, userId);

        // Assert
        assertFalse(result);
    }
}
