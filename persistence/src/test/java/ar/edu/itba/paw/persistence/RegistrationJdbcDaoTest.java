package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
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
public class RegistrationJdbcDaoTest {

    private static final String EMAIL = "invitado@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @Autowired
    private RegistrationDao registrationDao;

    @Autowired
    private UserDao userDao;

    private JdbcTemplate jdbcTemplate;
    private long eventId;
    private long userId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        eventId = insertEvent();
        userId = userDao.create(new User(EMAIL, NAME)).getId();
    }

    // El evento no necesita categoria para estos tests (son de inscripciones,
    // no de categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una.
    private long insertEvent() {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Recital", new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private Registration someRegistration(final long eventId) {
        return registrationFor(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
    }

    // registration.user_id ahora es una FK de verdad contra users.id: cada
    // email "de utileria" de estos tests necesita su propia fila en users
    // (userDao.create), no alcanza con inventar un id suelto.
    private Registration registrationFor(final long eventId, final String email, final String name,
                                          final RegistrationStatus status) {
        return registrationFor(eventId, email, userDao.create(new User(email, name)).getId(), name, status);
    }

    private Registration registrationFor(final long eventId, final String email, final long userId,
                                          final String name, final RegistrationStatus status) {
        final Registration registration = new Registration(eventId, email, name, status);
        registration.setUserId(userId);
        return registration;
    }

    // ---- create() + findByEventAndUserId() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Registration created = registrationDao.create(someRegistration(eventId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByEventAndUserIdReturnTheSameRegistration() {
        registrationDao.create(someRegistration(eventId));

        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

        assertTrue(found.isPresent());
        assertEquals(NAME, found.get().getName());
        assertEquals(RegistrationStatus.CONFIRMED, found.get().getStatus());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotRegistered() {
        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

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
        registrationDao.create(registrationFor(eventId, "confirmado@mail.com", NAME, RegistrationStatus.CONFIRMED));
        registrationDao.create(registrationFor(eventId, "espera@mail.com", NAME, RegistrationStatus.WAITLISTED));

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

    // ---- findByUserId() ("Mis inscripciones") ----

    @Test
    public void testFindByUserIdReturnsOnlyThatUsersRegistrations() {
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(registrationFor(eventId, "otro@mail.com", "Otro", RegistrationStatus.CONFIRMED));

        final List<Registration> found = registrationDao.findByUserId(userId);

        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByUserIdReturnsEmptyWhenNeverRegistered() {
        assertTrue(registrationDao.findByUserId(userId).isEmpty());
    }

    // ---- findByEventId() ("Ver inscriptos" del organizador) ----

    @Test
    public void testFindByEventIdReturnsAllRegistrationsForThatEvent() {
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(registrationFor(eventId, "otro@mail.com", "Otro", RegistrationStatus.CONFIRMED));

        final List<Registration> found = registrationDao.findByEventId(eventId);

        assertEquals(2, found.size());
    }

    @Test
    public void testFindByEventIdDoesNotReturnRegistrationsOfOtherEvents() {
        final long otherEventId = insertEvent();
        registrationDao.create(someRegistration(eventId));
        registrationDao.create(registrationFor(otherEventId, "otro@mail.com", "Otro", RegistrationStatus.CONFIRMED));

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

    // ---- countPending() ----

    @Test
    public void testCountPendingReturnsZeroWhenNobodyIsWaitingForReview() {
        assertEquals(0, registrationDao.countPending(eventId));
    }

    @Test
    public void testCountPendingCountsOnlyPendingStatus() {
        registrationDao.create(registrationFor(eventId, "pendiente@mail.com", NAME, RegistrationStatus.PENDING));
        registrationDao.create(registrationFor(eventId, "confirmado@mail.com", NAME, RegistrationStatus.CONFIRMED));
        registrationDao.create(registrationFor(eventId, "rechazado@mail.com", NAME, RegistrationStatus.REJECTED));

        assertEquals(1, registrationDao.countPending(eventId));
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventReturnsEmptyWhenNobodyIsWaitingForReview() {
        assertTrue(registrationDao.findPendingByEvent(eventId).isEmpty());
    }

    @Test
    public void testFindPendingByEventReturnsOnlyPendingOrderedByOldestFirst() throws InterruptedException {
        final Registration first = registrationDao.create(
                registrationFor(eventId, "primero@mail.com", NAME, RegistrationStatus.PENDING));
        // HSQLDB trunca registered_at a milisegundos: sin esta pausa, las dos
        // filas podrian quedar con el mismo timestamp y el orden no estaria
        // garantizado.
        Thread.sleep(5);
        final Registration second = registrationDao.create(
                registrationFor(eventId, "segundo@mail.com", NAME, RegistrationStatus.PENDING));
        registrationDao.create(registrationFor(eventId, "confirmado@mail.com", NAME, RegistrationStatus.CONFIRMED));

        final List<Registration> pending = registrationDao.findPendingByEvent(eventId);

        assertEquals(2, pending.size());
        assertEquals(first.getEmail(), pending.get(0).getEmail());
        assertEquals(second.getEmail(), pending.get(1).getEmail());
    }
}
