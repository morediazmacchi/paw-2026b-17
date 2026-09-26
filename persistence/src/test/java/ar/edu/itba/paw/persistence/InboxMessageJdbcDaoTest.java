package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

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

    @Autowired
    private UserDao userDao;

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

    private InboxMessage message(final String email, final InboxMessageType type) {
        return new InboxMessage(email, type, eventId, "Juan", null);
    }

    @Test
    public void testCreateAndFindReturnTheSameMessage() {
        inboxMessageDao.create(new InboxMessage(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", "hola"));

        final List<InboxMessage> found = inboxMessageDao.findByRecipient(EMAIL, 1, 20);

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
    public void testEventIdCanBeNull() {
        inboxMessageDao.create(new InboxMessage(EMAIL, InboxMessageType.PRIVATE_INVITE, null, null, null));

        assertNull(inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getEventId());
    }

    @Test
    public void testFindOnlyReturnsThatRecipientsMessages() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));
        inboxMessageDao.create(message(OTHER_EMAIL, InboxMessageType.NEW_REGISTRATION));

        assertEquals(1, inboxMessageDao.findByRecipient(EMAIL, 1, 20).size());
        assertEquals(1, inboxMessageDao.countByRecipient(EMAIL));
    }

    @Test
    public void testFindReturnsNewestFirstAndPaginates() {
        final long primero = inboxMessageDao.create(message(EMAIL, InboxMessageType.REGISTERED_CONFIRMED)).getId();
        final long segundo = inboxMessageDao.create(message(EMAIL, InboxMessageType.EVENT_UPDATED)).getId();
        final long tercero = inboxMessageDao.create(message(EMAIL, InboxMessageType.EVENT_CANCELLED)).getId();

        final List<InboxMessage> pagina1 = inboxMessageDao.findByRecipient(EMAIL, 1, 2);
        final List<InboxMessage> pagina2 = inboxMessageDao.findByRecipient(EMAIL, 2, 2);

        // Mismo created_at (misma transaccion): desempata el id, el mas nuevo primero.
        assertEquals(tercero, pagina1.get(0).getId());
        assertEquals(segundo, pagina1.get(1).getId());
        assertEquals(1, pagina2.size());
        assertEquals(primero, pagina2.get(0).getId());
    }

    @Test
    public void testMarkAllReadOnlyAffectsThatRecipient() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_PENDING));
        inboxMessageDao.create(message(OTHER_EMAIL, InboxMessageType.NEW_REGISTRATION));
        assertEquals(2, inboxMessageDao.countUnread(EMAIL));

        inboxMessageDao.markAllRead(EMAIL, new Date());

        assertEquals(0, inboxMessageDao.countUnread(EMAIL));
        assertEquals(1, inboxMessageDao.countUnread(OTHER_EMAIL));
        assertFalse(inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).isUnread());
    }

    @Test
    public void testMarkAllReadKeepsTheDateOfMessagesAlreadyRead() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));
        final Date primeraVez = new Date(System.currentTimeMillis() - 86_400_000L);
        inboxMessageDao.markAllRead(EMAIL, primeraVez);

        inboxMessageDao.markAllRead(EMAIL, new Date());

        assertEquals(primeraVez.getTime(), inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getReadAt().getTime());
    }

    @Test
    public void testMessagesGoAwayWithTheirEvent() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));

        jdbcTemplate.update("DELETE FROM event WHERE id = ?", eventId);

        assertEquals(0, inboxMessageDao.countByRecipient(EMAIL));
    }

    // ---- user_id (Grupo B) ----

    @Test
    public void testCreateAndFindKeepTheOptimisticallyLinkedUserId() {
        final long recipientId = userDao.create(new User(EMAIL, "Ana")).getId();
        final InboxMessage msg = message(EMAIL, InboxMessageType.NEW_REGISTRATION);
        msg.setUserId(recipientId);

        inboxMessageDao.create(msg);

        assertEquals(Long.valueOf(recipientId), inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getUserId());
    }

    @Test
    public void testCreateLeavesUserIdNullWhenTheRecipientHasNoAccount() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));

        assertNull(inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getUserId());
    }

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedMessage() {
        inboxMessageDao.create(message(EMAIL, InboxMessageType.NEW_REGISTRATION));
        final long recipientId = userDao.create(new User(EMAIL, "Ana")).getId();

        inboxMessageDao.linkUserId(EMAIL, recipientId);

        assertEquals(Long.valueOf(recipientId), inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getUserId());
    }

    @Test
    public void testLinkUserIdNeverOverwritesAnAlreadyLinkedMessage() {
        final long firstAccountId = userDao.create(new User(EMAIL, "Ana")).getId();
        final InboxMessage msg = message(EMAIL, InboxMessageType.NEW_REGISTRATION);
        msg.setUserId(firstAccountId);
        inboxMessageDao.create(msg);

        inboxMessageDao.linkUserId(EMAIL, firstAccountId + 999);

        assertEquals(Long.valueOf(firstAccountId), inboxMessageDao.findByRecipient(EMAIL, 1, 20).get(0).getUserId());
    }

    // ---- lectura por user_id (Grupo C) ----

    @Test
    public void testFindByRecipientUserIdOnlyReturnsThatUsersMessages() {
        final long userId = userDao.create(new User(EMAIL, "Ana")).getId();
        final long otherUserId = userDao.create(new User(OTHER_EMAIL, "Otro")).getId();
        final InboxMessage mine = message(EMAIL, InboxMessageType.NEW_REGISTRATION);
        mine.setUserId(userId);
        final InboxMessage other = message(OTHER_EMAIL, InboxMessageType.NEW_REGISTRATION);
        other.setUserId(otherUserId);
        inboxMessageDao.create(mine);
        inboxMessageDao.create(other);

        final List<InboxMessage> found = inboxMessageDao.findByRecipientUserId(userId, 1, 20);

        assertEquals(1, found.size());
        assertEquals(1, inboxMessageDao.countByUserId(userId));
    }

    @Test
    public void testCountUnreadByUserIdAndMarkAllReadByUserId() {
        final long userId = userDao.create(new User(EMAIL, "Ana")).getId();
        final InboxMessage msg = message(EMAIL, InboxMessageType.NEW_REGISTRATION);
        msg.setUserId(userId);
        inboxMessageDao.create(msg);

        assertEquals(1, inboxMessageDao.countUnreadByUserId(userId));

        inboxMessageDao.markAllReadByUserId(userId, new Date());

        assertEquals(0, inboxMessageDao.countUnreadByUserId(userId));
        assertFalse(inboxMessageDao.findByRecipientUserId(userId, 1, 20).get(0).isUnread());
    }
}
