package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventChatJpaDaoTest {

    @Autowired
    private DataSource ds;

    @Autowired
    private EventChatDao chatDao;

    private JdbcTemplate jdbcTemplate;
    private long ana;
    private long beto;
    private long eventId;
    private long anaRegistrationId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        ana = insertUser("ana@mail.com", "Ana");
        beto = insertUser("beto@mail.com", null);
        eventId = insertEvent("Recital");
        anaRegistrationId = insertRegistration(eventId, ana, "ana@mail.com", "CONFIRMED");
    }

    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertEvent(final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    private long insertRegistration(final long event, final long user, final String email, final String status) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, status, user_id) VALUES (?, ?, ?, ?)",
                event, email, status, user);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM registration WHERE event_id = ? AND email = ?", Long.class, event, email);
    }

    private void say(final long user, final String text) {
        chatDao.addMessage(new ChatMessage(eventId, user, text));
    }

    // ---- addMember() / isMember() ----

    @Test
    public void testAddMemberMakesThePersonAMember() {
        // Act
        final boolean added = chatDao.addMember(anaRegistrationId, eventId, ana);

        // Assert
        assertTrue(added);
        assertTrue(chatDao.isMember(eventId, ana));
    }

    @Test
    public void testAddMemberTwiceReturnsFalseTheSecondTime() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);

        // Act
        final boolean addedAgain = chatDao.addMember(anaRegistrationId, eventId, ana);

        // Assert
        assertFalse(addedAgain);
    }

    @Test
    public void testIsMemberIsFalseWhenNeverJoined() {
        assertFalse(chatDao.isMember(eventId, ana));
    }

    @Test
    public void testIsMemberIsFalseWhenTheRegistrationIsNoLongerConfirmed() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        jdbcTemplate.update("UPDATE registration SET status = 'REJECTED' WHERE id = ?", anaRegistrationId);

        // Act + Assert
        assertFalse(chatDao.isMember(eventId, ana));
    }

    @Test
    public void testDeletingTheRegistrationRemovesTheMembership() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);

        // Act
        jdbcTemplate.update("DELETE FROM registration WHERE id = ?", anaRegistrationId);

        // Assert
        assertEquals(0, (int) jdbcTemplate.queryForObject("SELECT COUNT(*) FROM event_chat_member", Integer.class));
    }

    // ---- removeMember() ----

    @Test
    public void testRemoveMemberDeletesTheMembership() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);

        // Act
        final boolean removed = chatDao.removeMember(eventId, ana);

        // Assert
        assertTrue(removed);
        assertFalse(chatDao.isMember(eventId, ana));
    }

    @Test
    public void testRemoveMemberWhenNotAMemberReturnsFalse() {
        assertFalse(chatDao.removeMember(eventId, ana));
    }

    // ---- addMessage() / findLatest() / findAfter() ----

    @Test
    public void testAddMessageAssignsAnIdAndStoresTheRow() {
        // Act
        final ChatMessage saved = chatDao.addMessage(new ChatMessage(eventId, ana, "Hola!"));

        // Assert
        assertTrue(saved.getId() > 0);
        assertEquals("Hola!", jdbcTemplate.queryForObject(
                "SELECT body FROM event_chat_message WHERE id = ?", String.class, saved.getId()));
    }

    @Test
    public void testFindLatestReturnsOldestFirstAndOnlyTheLastOnes() {
        // Arrange
        say(ana, "uno");
        say(ana, "dos");
        say(ana, "tres");

        // Act
        final List<ChatMessage> latest = chatDao.findLatest(eventId, 2);

        // Assert
        assertEquals(2, latest.size());
        assertEquals("dos", latest.get(0).getBody());
        assertEquals("tres", latest.get(1).getBody());
    }

    @Test
    public void testFindLatestIgnoresOtherEvents() {
        // Arrange
        final long other = insertEvent("Otro");
        chatDao.addMessage(new ChatMessage(other, ana, "de otro evento"));
        say(ana, "de este");

        // Act
        final List<ChatMessage> latest = chatDao.findLatest(eventId, 10);

        // Assert
        assertEquals(1, latest.size());
        assertEquals("de este", latest.get(0).getBody());
    }

    @Test
    public void testFindAfterReturnsOnlyNewerMessages() {
        // Arrange
        say(ana, "uno");
        final ChatMessage second = chatDao.addMessage(new ChatMessage(eventId, ana, "dos"));
        say(ana, "tres");

        // Act
        final List<ChatMessage> newer = chatDao.findAfter(eventId, second.getId(), 10);

        // Assert
        assertEquals(1, newer.size());
        assertEquals("tres", newer.get(0).getBody());
    }

    @Test
    public void testMessageCarriesTheSenderName() {
        // Arrange
        say(ana, "hola");

        // Act
        final ChatMessage message = chatDao.findLatest(eventId, 10).get(0);

        // Assert
        assertEquals("Ana", message.getSenderName());
        assertEquals(ana, message.getUserId());
    }

    @Test
    public void testSenderNameFallsBackToTheEmailPrefixWhenThereIsNoName() {
        // Arrange
        say(beto, "hola");

        // Act
        final ChatMessage message = chatDao.findLatest(eventId, 10).get(0);

        // Assert
        assertEquals("beto", message.getSenderName());
    }

    // ---- markRead() / findGroupsByUser() ----

    @Test
    public void testFindGroupsListsTheGroupWithItsUnreadCount() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        say(beto, "uno");
        say(beto, "dos");

        // Act
        final List<ChatGroup> groups = chatDao.findGroupsByUser(ana);

        // Assert
        assertEquals(1, groups.size());
        assertEquals(eventId, groups.get(0).getEventId());
        assertEquals("Recital", groups.get(0).getEventTitle());
        assertEquals(2, groups.get(0).getUnreadCount());
        assertNull(groups.get(0).getImageId());
    }

    @Test
    public void testOwnMessagesAreNotCountedAsUnread() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        say(ana, "mio");

        // Act
        final ChatGroup group = chatDao.findGroupsByUser(ana).get(0);

        // Assert
        assertEquals(0, group.getUnreadCount());
    }

    @Test
    public void testMarkReadClearsTheUnreadCount() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        say(beto, "uno");

        // Act
        chatDao.markRead(eventId, ana);

        // Assert
        assertEquals(0, chatDao.findGroupsByUser(ana).get(0).getUnreadCount());
    }

    @Test
    public void testMessagesAfterMarkReadCountAsUnread() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        say(beto, "uno");
        chatDao.markRead(eventId, ana);
        say(beto, "dos");

        // Act
        final ChatGroup group = chatDao.findGroupsByUser(ana).get(0);

        // Assert
        assertEquals(1, group.getUnreadCount());
    }

    @Test
    public void testFindGroupsLeavesOutGroupsWhereTheRegistrationIsNotConfirmed() {
        // Arrange
        chatDao.addMember(anaRegistrationId, eventId, ana);
        jdbcTemplate.update("UPDATE registration SET status = 'REJECTED' WHERE id = ?", anaRegistrationId);

        // Act + Assert
        assertTrue(chatDao.findGroupsByUser(ana).isEmpty());
    }

    @Test
    public void testFindGroupsPutsTheMostRecentlyActiveFirst() {
        // Arrange
        final long second = insertEvent("Feria");
        final long secondRegistration = insertRegistration(second, ana, "ana@mail.com", "CONFIRMED");
        chatDao.addMember(anaRegistrationId, eventId, ana);
        chatDao.addMember(secondRegistration, second, ana);
        jdbcTemplate.update("INSERT INTO event_chat_message (event_id, user_id, body, created_at) VALUES (?, ?, ?, ?)",
                eventId, beto, "viejo", new Timestamp(System.currentTimeMillis() - 100000));
        jdbcTemplate.update("INSERT INTO event_chat_message (event_id, user_id, body, created_at) VALUES (?, ?, ?, ?)",
                second, beto, "nuevo", new Timestamp(System.currentTimeMillis()));

        // Act
        final List<ChatGroup> groups = chatDao.findGroupsByUser(ana);

        // Assert
        assertEquals("Feria", groups.get(0).getEventTitle());
        assertEquals("Recital", groups.get(1).getEventTitle());
    }
}
