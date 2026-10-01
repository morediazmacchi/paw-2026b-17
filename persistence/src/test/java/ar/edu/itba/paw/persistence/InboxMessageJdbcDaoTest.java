package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
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
public class InboxMessageJdbcDaoTest {

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

    private long insertMessageAt(final String email, final InboxMessageType type, final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO inbox_message (recipient_email, type, event_id, created_at) VALUES (?, ?, ?, ?)",
                email, type.name(), eventId, createdAt);
        return lastInsertedId();
    }

    private String whereId(final long id) {
        return "id = " + id;
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsAllTheGivenFields() {
        // Arrange
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", "hola");

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
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

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
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

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
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);
        toCreate.setUserId(recipientId);

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
        final InboxMessage toCreate = new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

        // Act
        final InboxMessage created = inboxMessageDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertNull(storedUserId);
    }

    // ---- findByRecipient() ----

    @Test
    public void testFindByRecipientReturnsTheStoredMessageUnread() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", "hola");

        // Act
        final List<InboxMessage> found = inboxMessageDao.findByRecipient(EMAIL, 1, 20);

        // Assert
        assertEquals(1, found.size());
        final InboxMessage m = found.get(0);
        assertEquals(InboxMessageType.NEW_REGISTRATION, m.getType());
        assertEquals(Long.valueOf(eventId), m.getEventId());
        assertEquals("Juan", m.getActorName());
        assertEquals("hola", m.getBody());
        assertTrue(m.getCreatedAt() != null);
        assertTrue(m.isUnread());
    }

    @Test
    public void testFindByRecipientReturnsNullEventIdWhenStoredNull() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.PRIVATE_INVITE, null, null, null);

        // Act
        final List<InboxMessage> found = inboxMessageDao.findByRecipient(EMAIL, 1, 20);

        // Assert
        assertNull(found.get(0).getEventId());
    }

    @Test
    public void testFindByRecipientOnlyReturnsThatRecipientsMessages() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        insertMessage(OTHER_EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        final List<InboxMessage> found = inboxMessageDao.findByRecipient(EMAIL, 1, 20);

        // Assert
        assertEquals(1, found.size());
    }

    @Test
    public void testFindByRecipientOrdersNewestFirstAndPaginates() {
        // Arrange
        final Timestamp t1 = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp t2 = new Timestamp(System.currentTimeMillis() - 1000);
        final Timestamp t3 = new Timestamp(System.currentTimeMillis());
        final long primero = insertMessageAt(EMAIL, InboxMessageType.REGISTERED_CONFIRMED, t1);
        final long segundo = insertMessageAt(EMAIL, InboxMessageType.EVENT_UPDATED, t2);
        final long tercero = insertMessageAt(EMAIL, InboxMessageType.EVENT_CANCELLED, t3);

        // Act
        final List<InboxMessage> pagina1 = inboxMessageDao.findByRecipient(EMAIL, 1, 2);
        final List<InboxMessage> pagina2 = inboxMessageDao.findByRecipient(EMAIL, 2, 2);

        // Assert
        assertEquals(tercero, pagina1.get(0).getId());
        assertEquals(segundo, pagina1.get(1).getId());
        assertEquals(1, pagina2.size());
        assertEquals(primero, pagina2.get(0).getId());
    }

    // ---- countByRecipient() ----

    @Test
    public void testCountByRecipientCountsOnlyThatRecipientsMessages() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        insertMessage(EMAIL, null, InboxMessageType.NEW_PENDING, eventId, null, null);
        insertMessage(OTHER_EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        final long count = inboxMessageDao.countByRecipient(EMAIL);

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testCountByRecipientReturnsZeroWhenThereAreNoMessages() {
        // Arrange: no se inserta ningun mensaje.

        // Act
        final long count = inboxMessageDao.countByRecipient(EMAIL);

        // Assert
        assertEquals(0, count);
    }

    // ---- countUnread() ----

    @Test
    public void testCountUnreadCountsOnlyUnreadMessagesOfThatRecipient() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long readId = insertMessage(EMAIL, null, InboxMessageType.NEW_PENDING, eventId, null, null);
        jdbcTemplate.update("UPDATE inbox_message SET read_at = ? WHERE id = ?", new Timestamp(System.currentTimeMillis()), readId);
        insertMessage(OTHER_EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        final long count = inboxMessageDao.countUnread(EMAIL);

        // Assert
        assertEquals(1, count);
    }

    // ---- markAllRead() ----

    @Test
    public void testMarkAllReadSetsReadAtOnlyForThatRecipientsUnreadMessages() {
        // Arrange
        final long first = insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long second = insertMessage(EMAIL, null, InboxMessageType.NEW_PENDING, eventId, null, null);
        final long other = insertMessage(OTHER_EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        inboxMessageDao.markAllRead(EMAIL, new Date());

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(first) + " AND read_at IS NULL"));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(second) + " AND read_at IS NULL"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(other) + " AND read_at IS NULL"));
    }

    @Test
    public void testMarkAllReadDoesNotOverwriteAnAlreadyReadMessage() {
        // Arrange
        final long id = insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final Timestamp primeraVez = new Timestamp(System.currentTimeMillis() - 86_400_000L);
        jdbcTemplate.update("UPDATE inbox_message SET read_at = ? WHERE id = ?", primeraVez, id);

        // Act
        inboxMessageDao.markAllRead(EMAIL, new Date());

        // Assert
        final Timestamp readAt = jdbcTemplate.queryForObject(
                "SELECT read_at FROM inbox_message WHERE id = ?", Timestamp.class, id);
        assertEquals(primeraVez.getTime(), readAt.getTime());
    }

    // ---- ON DELETE CASCADE con event ----

    @Test
    public void testMessagesGoAwayWithTheirEvent() {
        // Arrange
        insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        jdbcTemplate.update("DELETE FROM event WHERE id = ?", eventId);

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "inbox_message", "recipient_email = '" + EMAIL + "'"));
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
}
