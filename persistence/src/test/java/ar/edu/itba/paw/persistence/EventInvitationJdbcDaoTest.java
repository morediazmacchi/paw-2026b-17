package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;
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

    @Autowired
    private UserDao userDao;

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

    private static EventInvitation someInvitation(final long eventId) {
        return new EventInvitation(eventId, INVITEE_EMAIL, TOKEN, CREATOR_EMAIL);
    }

    // ---- create() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final EventInvitation created = invitationDao.create(someInvitation(eventId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindReturnTheSameInvitation() {
        invitationDao.create(someInvitation(eventId));

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);

        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
        assertEquals(INVITEE_EMAIL, found.get().getEmail());
        assertEquals(CREATOR_EMAIL, found.get().getInvitedBy());
        assertTrue(found.get().getCreatedAt() != null);
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

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdReturnsTheInvitation() {
        final long inviteeId = userDao.create(new User(INVITEE_EMAIL, "Invitado")).getId();
        final EventInvitation invitation = someInvitation(eventId);
        invitation.setUserId(inviteeId);
        invitationDao.create(invitation);

        final Optional<EventInvitation> found = invitationDao.findByEventAndUserId(eventId, inviteeId);

        assertTrue(found.isPresent());
        assertEquals(TOKEN, found.get().getToken());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotLinked() {
        invitationDao.create(someInvitation(eventId));

        assertFalse(invitationDao.findByEventAndUserId(eventId, 999999L).isPresent());
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventReturnsOnlyThatEventsInvitations() {
        invitationDao.create(someInvitation(eventId));
        final long otherEventId = insertEvent();
        invitationDao.create(new EventInvitation(otherEventId, "otro@mail.com", "otro-token", CREATOR_EMAIL));

        final List<EventInvitation> found = invitationDao.findByEvent(eventId);

        assertEquals(1, found.size());
        assertEquals(INVITEE_EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventReturnsEmptyWhenNobodyWasInvited() {
        assertTrue(invitationDao.findByEvent(eventId).isEmpty());
    }

    // ---- user_id (Grupo B) ----

    @Test
    public void testCreateAndFindKeepTheOptimisticallyLinkedUserId() {
        final long inviteeId = userDao.create(new User(INVITEE_EMAIL, "Invitado")).getId();
        final EventInvitation invitation = someInvitation(eventId);
        invitation.setUserId(inviteeId);

        invitationDao.create(invitation);

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);
        assertEquals(Long.valueOf(inviteeId), found.get().getUserId());
    }

    @Test
    public void testCreateLeavesUserIdNullWhenTheInviteeHasNoAccount() {
        invitationDao.create(someInvitation(eventId));

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);
        assertEquals(null, found.get().getUserId());
    }

    @Test
    public void testLinkUserIdFillsInThePreviouslyUnresolvedInvitation() {
        invitationDao.create(someInvitation(eventId));
        final long inviteeId = userDao.create(new User(INVITEE_EMAIL, "Invitado")).getId();

        invitationDao.linkUserId(INVITEE_EMAIL, inviteeId);

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);
        assertEquals(Long.valueOf(inviteeId), found.get().getUserId());
    }

    @Test
    public void testLinkUserIdNeverOverwritesAnAlreadyLinkedInvitation() {
        final long firstAccountId = userDao.create(new User(INVITEE_EMAIL, "Invitado")).getId();
        final EventInvitation invitation = someInvitation(eventId);
        invitation.setUserId(firstAccountId);
        invitationDao.create(invitation);

        invitationDao.linkUserId(INVITEE_EMAIL, firstAccountId + 999);

        final Optional<EventInvitation> found = invitationDao.findByEventAndEmail(eventId, INVITEE_EMAIL);
        assertEquals(Long.valueOf(firstAccountId), found.get().getUserId());
    }
}
