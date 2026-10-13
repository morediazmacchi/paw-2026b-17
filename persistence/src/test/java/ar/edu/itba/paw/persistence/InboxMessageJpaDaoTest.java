package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
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
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class InboxMessageJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

    private static final String EMAIL = "ana@mail.com";
    private static final String OTHER_EMAIL = "otro@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private InboxMessageDao inboxMessageDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Recital", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        eventId = jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = 'Recital'", Long.class);
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Ana");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // id global autoincremental: justo despues de un insert, es siempre la
    // ultima fila insertada en TODA la tabla, sin ambiguedad por email repetido.
    private long lastInsertedId() {
        return jdbcTemplate.queryForObject("SELECT id FROM inbox_message ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertMessage(final String email, final Long userId, final InboxMessageType type,
                                final Long eventId, final String actorName, final String body) {
        jdbcTemplate.update(
                "INSERT INTO inbox_message (recipient_email, user_id, type, event_id, actor_name, body) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                email, userId, type.name(), eventId, actorName, body);
        return lastInsertedId();
    }

    private String whereId(final long id) {
        return "id = " + id;
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsAllTheGivenFields() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, em.getReference(Event.class, eventId), "Juan", "hola");

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT recipient_email, type, event_id, actor_name, body FROM inbox_message WHERE id = ?",
                created.getId());
        assertEquals(EMAIL, row.get("recipient_email"));
        assertEquals("NEW_REGISTRATION", row.get("type"));
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertEquals("Juan", row.get("actor_name"));
        assertEquals("hola", row.get("body"));
    }

    @Test
    public void testCreateAllowsNullEventId() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.PRIVATE_INVITE, null, null, null);

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        final Long storedEventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertNull(storedEventId);
    }

    @Test
    public void testCreateSetsCreatedAtAutomatically() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, em.getReference(Event.class, eventId), "Juan", null);

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        final Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM inbox_message WHERE id = ?", Timestamp.class, created.getId());
        assertTrue(createdAt != null);
    }

    @Test
    public void testCreateLeavesReadAtNull() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, em.getReference(Event.class, eventId), "Juan", null);

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(created.getId()) + " AND read_at IS NULL"));
    }

    @Test
    public void testCreatePersistsTheLinkedUserId() {
        // Arrange
        final long recipientId = insertUser(EMAIL);
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, em.getReference(Event.class, eventId), "Juan", null);
        toCreate.setUser(em.getReference(User.class, recipientId));

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertEquals(Long.valueOf(recipientId), storedUserId);
    }

    @Test
    public void testCreateLeavesUserIdNullWhenNotLinked() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, em.getReference(Event.class, eventId), "Juan", null);

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertNull(storedUserId);
    }

    // ---- ON DELETE CASCADE con event ----

    // El borrado en cascada es una propiedad del esquema (se arma por SQL en el Arrange);
    // lo que prueba el DAO es que countByUserId() ya no cuenta esos mensajes.
    @Test
    public void testCountByUserIdDoesNotCountMessagesOfADeletedEvent() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        jdbcTemplate.update("DELETE FROM event WHERE id = ?", eventId);

        // Act
        final long count = inboxMessageDao.countByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    // ---- user_id (Grupo B): linkUserId() ----

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedMessage() {
        // Arrange
        final long id = insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long recipientId = insertUser(EMAIL);

        // Act
        inboxMessageDao.linkUserId(EMAIL, recipientId);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, id);
        assertEquals(Long.valueOf(recipientId), storedUserId);
    }

    @Test
    public void testLinkUserIdNeverOverwritesAnAlreadyLinkedMessage() {
        // Arrange
        final long firstAccountId = insertUser(EMAIL);
        final long id = insertMessage(EMAIL, firstAccountId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        inboxMessageDao.linkUserId(EMAIL, firstAccountId + 999);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, id);
        assertEquals(Long.valueOf(firstAccountId), storedUserId);
    }

    // ---- lectura por user_id (Grupo C) ----

    @Test
    public void testFindByRecipientUserIdOnlyReturnsThatUsersMessages() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long otherUserId = insertUser(OTHER_EMAIL);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        insertMessage(OTHER_EMAIL, otherUserId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        final List<InboxMessage> found = inboxMessageDao.findByRecipientUserId(userId, 1, 20);

        // Assert
        assertEquals(1, found.size());
        assertEquals(Long.valueOf(userId), found.get(0).getUserId());
    }

    // ---- countByUserId() ----

    @Test
    public void testCountByUserIdCountsOnlyThatUsersMessages() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long otherUserId = insertUser(OTHER_EMAIL);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_PENDING, eventId, null, null);
        insertMessage(OTHER_EMAIL, otherUserId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        final long count = inboxMessageDao.countByUserId(userId);

        // Assert
        assertEquals(2, count);
    }

    // ---- countUnreadByUserId() ----

    @Test
    public void testCountUnreadByUserIdCountsOnlyUnreadMessagesOfThatUser() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long readId = insertMessage(EMAIL, userId, InboxMessageType.NEW_PENDING, eventId, null, null);
        jdbcTemplate.update("UPDATE inbox_message SET read_at = ? WHERE id = ?", new Timestamp(System.currentTimeMillis()), readId);

        // Act
        final long count = inboxMessageDao.countUnreadByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    // ---- markAllReadByUserId() ----

    @Test
    public void testMarkAllReadByUserIdSetsReadAtOnlyForThatUsersUnreadMessages() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long otherUserId = insertUser(OTHER_EMAIL);
        final long mine = insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long other = insertMessage(OTHER_EMAIL, otherUserId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        inboxMessageDao.markAllReadByUserId(userId, new Date());

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(mine) + " AND read_at IS NULL"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(other) + " AND read_at IS NULL"));
    }

    @Test
    public void testMarkAllReadByUserIdReturnsHowManyMessagesWereMarked() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        insertMessage(EMAIL, userId, InboxMessageType.NEW_PENDING, eventId, null, null);

        // Act
        final int result = inboxMessageDao.markAllReadByUserId(userId, new Date());

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testMarkAllReadByUserIdReturnsZeroWhenThereIsNothingUnread() {
        // Arrange
        final long userId = insertUser(EMAIL);

        // Act
        final int result = inboxMessageDao.markAllReadByUserId(userId, new Date());

        // Assert
        assertEquals(0, result);
    }

    @Test
    public void testLinkUserIdReturnsHowManyMessagesWereLinked() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long recipientId = insertUser(EMAIL);

        // Act
        final int result = inboxMessageDao.linkUserId(EMAIL, recipientId);

        // Assert
        assertEquals(1, result);
    }

    @Test
    public void testLinkUserIdReturnsZeroWhenNoMessageWasWaiting() {
        // Arrange
        final long recipientId = insertUser(EMAIL);

        // Act
        final int result = inboxMessageDao.linkUserId(EMAIL, recipientId);

        // Assert
        assertEquals(0, result);
    }
}
