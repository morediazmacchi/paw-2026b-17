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
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

    @PersistenceContext
    private EntityManager em;

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

    // Arrange por SQL directo, nunca con el DAO bajo prueba (ni addMember ni addMessage): si el
    // test falla, tiene que ser por el metodo del Act y no por otro.
    private void insertMember(final long registrationId, final long event, final long user) {
        jdbcTemplate.update("INSERT INTO event_chat_member (registration_id, event_id, user_id) VALUES (?, ?, ?)",
                registrationId, event, user);
    }

    private long insertMessage(final long event, final long user, final String text) {
        jdbcTemplate.update("INSERT INTO event_chat_message (event_id, user_id, body) VALUES (?, ?, ?)",
                event, user, text);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM event_chat_message", Long.class);
    }

    private long say(final long user, final String text) {
        return insertMessage(eventId, user, text);
    }

    private String memberWhere(final long event, final long user) {
        return "event_id = " + event + " AND user_id = " + user;
    }

    // ---- addMember() / isMember() ----

    @Test
    public void testAddMemberInsertsTheMemberRowAndReturnsTrue() {
        // Arrange: ana ya tiene su inscripcion confirmada (setUp), todavia sin grupo.

        // Act
        final boolean added = chatDao.addMember(anaRegistrationId, eventId, ana);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(added);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member",
                "registration_id = " + anaRegistrationId + " AND " + memberWhere(eventId, ana)));
    }

    @Test
    public void testAddMemberWhenAlreadyAMemberKeepsOneRowAndReturnsFalse() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);

        // Act
        final boolean addedAgain = chatDao.addMember(anaRegistrationId, eventId, ana);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(addedAgain);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member", memberWhere(eventId, ana)));
    }

    @Test
    public void testIsMemberIsFalseWhenNeverJoined() {
        // Arrange: ana esta inscripta pero nunca entro al grupo.

        // Act
        final boolean member = chatDao.isMember(eventId, ana);

        // Assert
        assertFalse(member);
    }

    @Test
    public void testIsMemberIsFalseWhenTheRegistrationIsNoLongerConfirmed() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);
        jdbcTemplate.update("UPDATE registration SET status = 'REJECTED' WHERE id = ?", anaRegistrationId);

        // Act
        final boolean member = chatDao.isMember(eventId, ana);

        // Assert
        assertFalse(member);
    }

    @Test
    public void testDeletingTheRegistrationRemovesTheMembership() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);

        // Act
        jdbcTemplate.update("DELETE FROM registration WHERE id = ?", anaRegistrationId);

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member", memberWhere(eventId, ana)));
    }

    // ---- removeMember() ----

    @Test
    public void testRemoveMemberDeletesTheMemberRowAndReturnsTrue() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);

        // Act
        final boolean removed = chatDao.removeMember(eventId, ana);

        // Assert
        assertTrue(removed);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member", memberWhere(eventId, ana)));
    }

    @Test
    public void testRemoveMemberWhenNotAMemberDeletesNothingAndReturnsFalse() {
        // Arrange: beto si esta en el grupo; ana no. El de beto tiene que quedar.
        final long betoRegistrationId = insertRegistration(eventId, beto, "beto@mail.com", "CONFIRMED");
        insertMember(betoRegistrationId, eventId, beto);

        // Act
        final boolean removed = chatDao.removeMember(eventId, ana);

        // Assert
        assertFalse(removed);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member", memberWhere(eventId, beto)));
    }

    // ---- addMessage() / findLatest() / findAfter() ----

    @Test
    public void testAddMessageAssignsAnIdAndStoresTheRow() {
        // Act
        final ChatMessage saved = chatDao.addMessage(eventId, ana, "Hola!");

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
        insertMessage(other, ana, "de otro evento");
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
        final long second = say(ana, "dos");
        say(ana, "tres");

        // Act
        final List<ChatMessage> newer = chatDao.findAfter(eventId, second, 10);

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
        insertMember(anaRegistrationId, eventId, ana);
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
        insertMember(anaRegistrationId, eventId, ana);
        say(ana, "mio");

        // Act
        final ChatGroup group = chatDao.findGroupsByUser(ana).get(0);

        // Assert
        assertEquals(0, group.getUnreadCount());
    }

    @Test
    public void testMarkReadStoresTheLastMessageAsRead() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);
        final long last = say(beto, "uno");

        // Act
        chatDao.markRead(eventId, ana);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_chat_member",
                memberWhere(eventId, ana) + " AND last_read_id = " + last));
    }

    @Test
    public void testMessagesAfterMarkReadCountAsUnread() {
        // Arrange: ana ya leyo hasta "uno" (last_read_id por SQL, no con markRead); despues llega "dos".
        insertMember(anaRegistrationId, eventId, ana);
        final long first = say(beto, "uno");
        jdbcTemplate.update("UPDATE event_chat_member SET last_read_id = ? WHERE registration_id = ?", first, anaRegistrationId);
        say(beto, "dos");

        // Act
        final ChatGroup group = chatDao.findGroupsByUser(ana).get(0);

        // Assert
        assertEquals(1, group.getUnreadCount());
    }

    @Test
    public void testFindGroupsLeavesOutGroupsWhereTheRegistrationIsNotConfirmed() {
        // Arrange
        insertMember(anaRegistrationId, eventId, ana);
        jdbcTemplate.update("UPDATE registration SET status = 'REJECTED' WHERE id = ?", anaRegistrationId);

        // Act
        final List<ChatGroup> groups = chatDao.findGroupsByUser(ana);

        // Assert
        assertTrue(groups.isEmpty());
    }

    @Test
    public void testFindGroupsPutsTheMostRecentlyActiveFirst() {
        // Arrange
        final long second = insertEvent("Feria");
        final long secondRegistration = insertRegistration(second, ana, "ana@mail.com", "CONFIRMED");
        insertMember(anaRegistrationId, eventId, ana);
        insertMember(secondRegistration, second, ana);
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
