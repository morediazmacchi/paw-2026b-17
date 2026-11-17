package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.RegistrationStatus;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
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

    private void insertRegistration(final long eventId, final String email, final Long userId,
                                    final RegistrationStatus status) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, name, status) VALUES (?, ?, ?, ?, ?)",
                eventId, email, userId, "Alguien", status.name());
    }

    private long insertEventCreatedBy(final long creatorId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, creator_id) VALUES (?, ?, ?, ?, ?)",
                "Feria", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "organizadora@mail.com", creatorId);
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private void insertFollow(final long followerId, final long followedId) {
        jdbcTemplate.update("INSERT INTO follow (follower_id, followed_id) VALUES (?, ?)", followerId, followedId);
    }

    private List<String> recipientsOf(final InboxMessageType type) {
        return jdbcTemplate.queryForList(
                "SELECT recipient_email FROM inbox_message WHERE type = ? ORDER BY recipient_email",
                String.class, type.name());
    }

    // ---- createForRegistrants() ----

    @Test
    public void testCreateForRegistrantsLeavesOneMessagePerRegistrantInTheGivenStatuses() {
        // Arrange: una inscripcion por estado; solo las dos primeras estan en los estados pedidos.
        insertRegistration(eventId, "confirmada@mail.com", null, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "espera@mail.com", null, RegistrationStatus.WAITLISTED);
        insertRegistration(eventId, "rechazada@mail.com", null, RegistrationStatus.REJECTED);

        // Act
        final int created = inboxMessageDao.createForRegistrants(eventId, InboxMessageType.EVENT_CANCELLED,
                EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED));

        // Assert
        assertEquals(2, created);
        assertEquals(Arrays.asList("confirmada@mail.com", "espera@mail.com"), recipientsOf(InboxMessageType.EVENT_CANCELLED));
    }

    @Test
    public void testCreateForRegistrantsIgnoresRegistrantsOfOtherEvents() {
        // Arrange
        final long otherEventId = insertEventCreatedBy(insertUser("organizadora@mail.com"));
        insertRegistration(eventId, "de-este@mail.com", null, RegistrationStatus.CONFIRMED);
        insertRegistration(otherEventId, "de-otro@mail.com", null, RegistrationStatus.CONFIRMED);

        // Act
        inboxMessageDao.createForRegistrants(eventId, InboxMessageType.EVENT_UPDATED,
                EnumSet.of(RegistrationStatus.CONFIRMED));

        // Assert
        assertEquals(Collections.singletonList("de-este@mail.com"), recipientsOf(InboxMessageType.EVENT_UPDATED));
    }

    @Test
    public void testCreateForRegistrantsLinksTheAccountWithThatEmailAndTheEvent() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertRegistration(eventId, EMAIL, userId, RegistrationStatus.CONFIRMED);

        // Act
        inboxMessageDao.createForRegistrants(eventId, InboxMessageType.EVENT_REMINDER,
                EnumSet.of(RegistrationStatus.CONFIRMED));

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, event_id, actor_name, read_at FROM inbox_message WHERE recipient_email = ?", EMAIL);
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertNull(row.get("actor_name"));
        assertNull(row.get("read_at"));
    }

    @Test
    public void testCreateForRegistrantsLeavesTheAccountEmptyWhenThatEmailHasNone() {
        // Arrange
        insertRegistration(eventId, OTHER_EMAIL, null, RegistrationStatus.CONFIRMED);

        // Act
        inboxMessageDao.createForRegistrants(eventId, InboxMessageType.EVENT_REMINDER,
                EnumSet.of(RegistrationStatus.CONFIRMED));

        // Assert
        final Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE recipient_email = ?", Long.class, OTHER_EMAIL);
        assertNull(userId);
    }

    @Test
    public void testCreateForRegistrantsStoresTheEmailInLowercaseAndWithoutSpaces() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertRegistration(eventId, "  Ana@Mail.COM ", null, RegistrationStatus.CONFIRMED);

        // Act
        inboxMessageDao.createForRegistrants(eventId, InboxMessageType.EVENT_REMINDER,
                EnumSet.of(RegistrationStatus.CONFIRMED));

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap("SELECT recipient_email, user_id FROM inbox_message");
        assertEquals(EMAIL, row.get("recipient_email"));
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
    }

    // ---- createForRegistrations() ----

    private long registrationIdOf(final String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM registration WHERE email = ?", Long.class, email);
    }

    @Test
    public void testCreateForRegistrationsLeavesOneMessagePerGivenRegistrationOnly() {
        // Arrange: tres inscripciones del evento en lista de espera y una de otro evento; se piden solo dos.
        final long otherEventId = insertEventCreatedBy(insertUser("organizadora@mail.com"));
        insertRegistration(eventId, "uno@mail.com", null, RegistrationStatus.WAITLISTED);
        insertRegistration(eventId, "dos@mail.com", null, RegistrationStatus.WAITLISTED);
        insertRegistration(eventId, "tres@mail.com", null, RegistrationStatus.WAITLISTED);
        insertRegistration(otherEventId, "cuatro@mail.com", null, RegistrationStatus.WAITLISTED);

        // Act
        final int created = inboxMessageDao.createForRegistrations(InboxMessageType.REGISTERED_WAITLISTED,
                Arrays.asList(registrationIdOf("uno@mail.com"), registrationIdOf("dos@mail.com")));

        // Assert
        assertEquals(2, created);
        assertEquals(Arrays.asList("dos@mail.com", "uno@mail.com"), recipientsOf(InboxMessageType.REGISTERED_WAITLISTED));
    }

    @Test
    public void testCreateForRegistrationsLinksTheAccountAndTheEventAndNormalizesTheEmail() {
        // Arrange
        final long userId = insertUser(EMAIL);
        insertRegistration(eventId, "  Ana@Mail.COM ", null, RegistrationStatus.WAITLISTED);

        // Act
        inboxMessageDao.createForRegistrations(InboxMessageType.REGISTERED_WAITLISTED,
                Collections.singletonList(registrationIdOf("  Ana@Mail.COM ")));

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT recipient_email, user_id, event_id, actor_name, read_at FROM inbox_message");
        assertEquals(EMAIL, row.get("recipient_email"));
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertNull(row.get("actor_name"));
        assertNull(row.get("read_at"));
    }

    @Test
    public void testCreateForRegistrationsLeavesTheAccountEmptyWhenThatEmailHasNone() {
        // Arrange
        insertRegistration(eventId, OTHER_EMAIL, null, RegistrationStatus.WAITLISTED);

        // Act
        inboxMessageDao.createForRegistrations(InboxMessageType.REGISTERED_WAITLISTED,
                Collections.singletonList(registrationIdOf(OTHER_EMAIL)));

        // Assert
        final Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE recipient_email = ?", Long.class, OTHER_EMAIL);
        assertNull(userId);
    }

    // ---- createForOrganizerOfRegistrations() ----

    @Test
    public void testCreateForOrganizerOfRegistrationsLeavesOneMessagePerGivenRegistrationForTheOrganizer() {
        // Arrange: dos inscripciones del evento y una tercera que no se pide.
        insertRegistration(eventId, "uno@mail.com", null, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "dos@mail.com", null, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "tres@mail.com", null, RegistrationStatus.CONFIRMED);

        // Act
        final int created = inboxMessageDao.createForOrganizerOfRegistrations(InboxMessageType.NEW_REGISTRATION,
                Arrays.asList(registrationIdOf("uno@mail.com"), registrationIdOf("dos@mail.com")));

        // Assert: dos avisos, los dos al mail del organizador del evento.
        assertEquals(2, created);
        assertEquals(Arrays.asList("creador@mail.com", "creador@mail.com"),
                recipientsOf(InboxMessageType.NEW_REGISTRATION));
    }

    @Test
    public void testCreateForOrganizerOfRegistrationsUsesTheNameOfEachPersonAsTheActorOrTheEmailWhenThereIsNone() {
        // Arrange: una con nombre y otra con el nombre en blanco.
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, name, status) VALUES (?, ?, ?, ?, ?)",
                eventId, "ana@mail.com", null, "Ana Gomez", RegistrationStatus.PENDING.name());
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, name, status) VALUES (?, ?, ?, ?, ?)",
                eventId, "sinnombre@mail.com", null, "  ", RegistrationStatus.PENDING.name());

        // Act
        inboxMessageDao.createForOrganizerOfRegistrations(InboxMessageType.NEW_PENDING,
                Arrays.asList(registrationIdOf("ana@mail.com"), registrationIdOf("sinnombre@mail.com")));

        // Assert
        final List<String> actors = jdbcTemplate.queryForList(
                "SELECT actor_name FROM inbox_message WHERE type = ? ORDER BY actor_name",
                String.class, InboxMessageType.NEW_PENDING.name());
        assertEquals(Arrays.asList("Ana Gomez", "sinnombre@mail.com"), actors);
    }

    @Test
    public void testCreateForOrganizerOfRegistrationsLinksTheOrganizerAccountAndTheEvent() {
        // Arrange
        final long organizerId = insertUser("creador@mail.com");
        insertRegistration(eventId, EMAIL, null, RegistrationStatus.CONFIRMED);

        // Act
        inboxMessageDao.createForOrganizerOfRegistrations(InboxMessageType.NEW_REGISTRATION,
                Collections.singletonList(registrationIdOf(EMAIL)));

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, event_id, read_at FROM inbox_message WHERE type = ?",
                InboxMessageType.NEW_REGISTRATION.name());
        assertEquals(organizerId, ((Number) row.get("user_id")).longValue());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertNull(row.get("read_at"));
    }

    // ---- createForFollowersOfCreator() ----

    @Test
    public void testCreateForFollowersOfCreatorLeavesOneMessagePerFollowerOfTheOrganizer() {
        // Arrange: dos siguen a la organizadora; un tercero sigue a otra persona (no tiene que recibir nada).
        final long organizerId = insertUser("organizadora@mail.com");
        final long otherOrganizerId = insertUser("otra-organizadora@mail.com");
        final long featuredEventId = insertEventCreatedBy(organizerId);
        insertFollow(insertUser("fan1@mail.com"), organizerId);
        insertFollow(insertUser("fan2@mail.com"), organizerId);
        insertFollow(insertUser("fan-de-otra@mail.com"), otherOrganizerId);

        // Act
        final int created = inboxMessageDao.createForFollowersOfCreator(featuredEventId,
                InboxMessageType.FOLLOWED_USER_NEW_EVENT);

        // Assert
        assertEquals(2, created);
        assertEquals(Arrays.asList("fan1@mail.com", "fan2@mail.com"), recipientsOf(InboxMessageType.FOLLOWED_USER_NEW_EVENT));
    }

    @Test
    public void testCreateForFollowersOfCreatorUsesTheOrganizerNameAsActorAndLinksTheFollower() {
        // Arrange
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", "organizadora@mail.com", "Lola");
        final long organizerId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "organizadora@mail.com");
        final long featuredEventId = insertEventCreatedBy(organizerId);
        final long fanId = insertUser("fan@mail.com");
        insertFollow(fanId, organizerId);

        // Act
        inboxMessageDao.createForFollowersOfCreator(featuredEventId, InboxMessageType.FOLLOWED_USER_NEW_EVENT);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, event_id, actor_name FROM inbox_message WHERE recipient_email = 'fan@mail.com'");
        assertEquals(fanId, ((Number) row.get("user_id")).longValue());
        assertEquals(featuredEventId, ((Number) row.get("event_id")).longValue());
        assertEquals("Lola", row.get("actor_name"));
    }

    @Test
    public void testCreateForFollowersOfCreatorOfAnEventWithoutOrganizerLeavesNothing() {
        // Arrange: el evento del setUp no tiene creator_id (evento viejo); hay seguimientos, pero de nadie suyo.
        insertFollow(insertUser("fan@mail.com"), insertUser("organizadora@mail.com"));

        // Act
        final int created = inboxMessageDao.createForFollowersOfCreator(eventId,
                InboxMessageType.FOLLOWED_USER_NEW_EVENT);

        // Assert
        assertEquals(0, created);
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "inbox_message"));
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsAllTheGivenFields() {
        // Arrange
        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", "hola");

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
        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.PRIVATE_INVITE, null, null, null);

        // Assert
        final Long storedEventId = jdbcTemplate.queryForObject(
                "SELECT event_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertNull(storedEventId);
    }

    @Test
    public void testCreateSetsCreatedAtAutomatically() {
        // Arrange
        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

        // Assert
        final Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM inbox_message WHERE id = ?", Timestamp.class, created.getId());
        assertTrue(createdAt != null);
    }

    @Test
    public void testCreateLeavesReadAtNull() {
        // Arrange
        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(created.getId()) + " AND read_at IS NULL"));
    }

    @Test
    public void testCreateLinksTheAccountWithThatEmail() {
        // Arrange: ya hay una cuenta con ese mail (por SQL)
        final long recipientId = insertUser(EMAIL);

        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM inbox_message WHERE id = ?", Long.class, created.getId());
        assertEquals(Long.valueOf(recipientId), storedUserId);
    }

    @Test
    public void testCreateLeavesUserIdNullWhenNotLinked() {
        // Arrange
        // Act
        final InboxMessage created = inboxMessageDao.create(EMAIL, InboxMessageType.NEW_REGISTRATION, eventId, "Juan", null);

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

    // ---- user_id (avisos a quien todavia no tenia cuenta): linkUserId() ----

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedMessageAndReturnsHowMany() {
        // Arrange
        final long id = insertMessage(EMAIL, null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long recipientId = insertUser(EMAIL);

        // Act
        final int result = inboxMessageDao.linkUserId(EMAIL, recipientId);

        // Assert
        assertEquals(1, result);
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

    // ---- markReadByIds() ----

    @Test
    public void testMarkReadByIdsSetsReadAtOnlyForTheGivenMessages() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long first = insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long second = insertMessage(EMAIL, userId, InboxMessageType.NEW_PENDING, eventId, null, null);
        final long notShown = insertMessage(EMAIL, userId, InboxMessageType.EVENT_UPDATED, eventId, null, null);

        // Act
        inboxMessageDao.markReadByIds(userId, Arrays.asList(first, second), new Date());

        // Assert
        assertEquals(2, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", "user_id = " + userId + " AND read_at IS NOT NULL"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(notShown) + " AND read_at IS NULL"));
    }

    @Test
    public void testMarkReadByIdsIgnoresMessagesOfOtherUsers() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long otherUserId = insertUser(OTHER_EMAIL);
        final long others = insertMessage(OTHER_EMAIL, otherUserId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);

        // Act
        inboxMessageDao.markReadByIds(userId, Arrays.asList(others), new Date());

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(others) + " AND read_at IS NULL"));
    }

    @Test
    public void testMarkReadByIdsMarksOnlyTheUnreadOnesAndReturnsHowMany() {
        // Arrange
        final long userId = insertUser(EMAIL);
        final long unread = insertMessage(EMAIL, userId, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long alreadyRead = insertMessage(EMAIL, userId, InboxMessageType.NEW_PENDING, eventId, null, null);
        jdbcTemplate.update("UPDATE inbox_message SET read_at = ? WHERE id = ?",
                new Timestamp(System.currentTimeMillis()), alreadyRead);

        // Act
        final int result = inboxMessageDao.markReadByIds(userId, Arrays.asList(unread, alreadyRead), new Date());

        // Assert
        assertEquals(1, result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "inbox_message", whereId(unread) + " AND read_at IS NOT NULL"));
    }

    @Test
    public void testLinkUserIdWithNoMessageForThatEmailLinksNothingAndReturnsZero() {
        // Arrange: un aviso de otro email, sin cuenta. Tiene que seguir sin enlazar.
        final long other = insertMessage("otra@mail.com", null, InboxMessageType.NEW_REGISTRATION, eventId, null, null);
        final long recipientId = insertUser(EMAIL);

        // Act
        final int result = inboxMessageDao.linkUserId(EMAIL, recipientId);

        // Assert
        assertEquals(0, result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "inbox_message",
                whereId(other) + " AND user_id IS NULL"));
    }
}
