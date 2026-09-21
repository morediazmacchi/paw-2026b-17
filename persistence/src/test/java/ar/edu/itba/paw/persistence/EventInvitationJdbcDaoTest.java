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
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventInvitationJdbcDaoTest {

    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final String INVITEE_EMAIL = "invitado@mail.com";
    private static final String TOKEN = "un-token-bien-largo-y-al-azar";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventInvitationDao invitationDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        final long categoryId = insertCategory("Musica");
        eventId = insertEvent(categoryId);
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertEvent(final long categoryId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, category_id, visibility) "
                        + "VALUES (?, ?, ?, ?, ?, 'PRIVATE')",
                "Fiesta privada", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", CREATOR_EMAIL, categoryId);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE category_id = ?", Long.class, categoryId);
    }

    private static EventInvitation someInvitation(final long eventId) {
        return new EventInvitation(eventId, INVITEE_EMAIL, TOKEN, CREATOR_EMAIL);
    }

    // ---- create() + findByToken() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final EventInvitation created = invitationDao.create(someInvitation(eventId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByTokenReturnTheSameInvitation() {
        invitationDao.create(someInvitation(eventId));

        final Optional<EventInvitation> found = invitationDao.findByToken(TOKEN);

        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
        assertEquals(INVITEE_EMAIL, found.get().getEmail());
        assertEquals(CREATOR_EMAIL, found.get().getInvitedBy());
        assertTrue(found.get().getCreatedAt() != null);
    }

    @Test
    public void testFindByTokenReturnsEmptyWhenTheTokenDoesNotExist() {
        assertFalse(invitationDao.findByToken("inexistente").isPresent());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateTokenThrowsDataAccessException() {
        invitationDao.create(someInvitation(eventId));
        invitationDao.create(new EventInvitation(eventId, "otro@mail.com", TOKEN, CREATOR_EMAIL));
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEventAndEmailThrowsDataAccessException() {
        invitationDao.create(someInvitation(eventId));
        invitationDao.create(new EventInvitation(eventId, INVITEE_EMAIL, "otro-token", CREATOR_EMAIL));
    }

    // ---- findByEventAndEmail() ----

    @Test
    public void testFindByEventAndEmailReturnsTheInvitation() {
        invitationDao.create(someInvitation(eventId));

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);

        assertTrue(found.isPresent());
        assertEquals(TOKEN, found.get().getToken());
    }

    @Test
    public void testFindByEventAndEmailReturnsEmptyWhenNotInvited() {
        assertFalse(invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL).isPresent());
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventReturnsOnlyThatEventsInvitations() {
        invitationDao.create(someInvitation(eventId));
        final long otherEventId = insertEvent(insertCategory("Deportes"));
        invitationDao.create(new EventInvitation(otherEventId, "otro@mail.com", "otro-token", CREATOR_EMAIL));

        final List<EventInvitation> found = invitationDao.findByEvent(eventId);

        assertEquals(1, found.size());
        assertEquals(INVITEE_EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventReturnsEmptyWhenNobodyWasInvited() {
        assertTrue(invitationDao.findByEvent(eventId).isEmpty());
    }
}
