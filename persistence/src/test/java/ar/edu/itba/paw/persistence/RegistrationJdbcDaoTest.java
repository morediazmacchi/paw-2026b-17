package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
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
public class RegistrationJdbcDaoTest {

    private static final String EMAIL = "invitado@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @Autowired
    private RegistrationDao registrationDao;

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
                "INSERT INTO event (title, event_date, address, creator_email, category_id) VALUES (?, ?, ?, ?, ?)",
                "Recital", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com", categoryId);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE category_id = ?", Long.class, categoryId);
    }

    private static Registration someRegistration(final long eventId) {
        return new Registration(eventId, EMAIL, NAME, Registration.STATUS_CONFIRMED);
    }

    // ---- create() + findByEventAndEmail() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Registration created = registrationDao.create(someRegistration(eventId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByEventAndEmailReturnTheSameRegistration() {
        registrationDao.create(someRegistration(eventId));

        final Optional<Registration> found = registrationDao.findByEventAndEmail(eventId, EMAIL);

        assertTrue(found.isPresent());
        assertEquals(NAME, found.get().getName());
        assertEquals(Registration.STATUS_CONFIRMED, found.get().getStatus());
    }

    @Test
    public void testFindByEventAndEmailReturnsEmptyWhenNotRegistered() {
        final Optional<Registration> found = registrationDao.findByEventAndEmail(eventId, EMAIL);

        assertFalse(found.isPresent());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEventAndEmailThrowsDataAccessException() {
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(someRegistration(eventId));
    }

    // ---- countConfirmed() ----

    @Test
    public void testCountConfirmedReturnsZeroWhenNobodyIsRegistered() {
        assertEquals(0, registrationDao.countConfirmed(eventId));
    }

    @Test
    public void testCountConfirmedCountsOnlyConfirmedStatus() {
        registrationDao.create(new Registration(eventId, "confirmado@mail.com", NAME, Registration.STATUS_CONFIRMED));
        registrationDao.create(new Registration(eventId, "espera@mail.com", NAME, Registration.STATUS_WAITLISTED));

        assertEquals(1, registrationDao.countConfirmed(eventId));
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsEmptyWhenItDoesNotExist() {
        assertFalse(registrationDao.findById(-1).isPresent());
    }

    @Test
    public void testFindByIdReturnsTheRegistration() {
        final Registration created = registrationDao.create(someRegistration(eventId));

        final Optional<Registration> found = registrationDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(EMAIL, found.get().getEmail());
    }

    // ---- findByEmail() ("Mis inscripciones") ----

    @Test
    public void testFindByEmailReturnsOnlyThatEmailsRegistrations() {
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(new Registration(eventId, "otro@mail.com", "Otro", Registration.STATUS_CONFIRMED));

        final List<Registration> found = registrationDao.findByEmail(EMAIL);

        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEmailReturnsEmptyWhenNeverRegistered() {
        assertTrue(registrationDao.findByEmail(EMAIL).isEmpty());
    }

    // ---- findByEventId() ("Ver inscriptos" del organizador) ----

    @Test
    public void testFindByEventIdReturnsAllRegistrationsForThatEvent() {
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(new Registration(eventId, "otro@mail.com", "Otro", Registration.STATUS_CONFIRMED));

        final List<Registration> found = registrationDao.findByEventId(eventId);

        assertEquals(2, found.size());
    }

    @Test
    public void testFindByEventIdDoesNotReturnRegistrationsOfOtherEvents() {
        final long otherCategoryId = insertCategory("Deportes");
        final long otherEventId = insertEvent(otherCategoryId);
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(new Registration(otherEventId, "otro@mail.com", "Otro", Registration.STATUS_CONFIRMED));

        final List<Registration> found = registrationDao.findByEventId(eventId);

        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventIdReturnsEmptyWhenNobodyIsRegistered() {
        assertTrue(registrationDao.findByEventId(eventId).isEmpty());
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheRegistration() {
        final Registration created = registrationDao.create(someRegistration(eventId));

        registrationDao.delete(created.getId());

        assertFalse(registrationDao.findById(created.getId()).isPresent());
        assertEquals(0, registrationDao.countConfirmed(eventId));
    }
}
