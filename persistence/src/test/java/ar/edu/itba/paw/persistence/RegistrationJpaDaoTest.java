package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Event;
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
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class RegistrationJpaDaoTest {

    private static final String EMAIL = "invitado@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private RegistrationDao registrationDao;

    private static final long DAY = 86_400_000L;

    private JdbcTemplate jdbcTemplate;
    private long eventId;
    private long userId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        eventId = insertEvent();
        userId = insertUser(EMAIL, NAME);
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

    private long insertEventAt(final long millisFromNow) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                "Evento", new Timestamp(System.currentTimeMillis() + millisFromNow), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long registerUserAtEvent(final long eventId) {
        return insertRegistration(eventId, EMAIL + eventId, userId, NAME, RegistrationStatus.CONFIRMED);
    }

    // registration.user_id es una FK de verdad contra users.id: cada email
    // "de utileria" de estos tests necesita su propia fila en users.
    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertRegistrationAt(final long eventId, final String email, final long userId, final String name,
                                       final RegistrationStatus status, final Timestamp registeredAt) {
        jdbcTemplate.update(
                "INSERT INTO registration (event_id, email, user_id, name, status, registered_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                eventId, email, userId, name, status.name(), registeredAt);
        return jdbcTemplate.queryForObject("SELECT id FROM registration ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertRegistration(final long eventId, final String email, final long userId, final String name,
                                     final RegistrationStatus status) {
        return insertRegistrationAt(eventId, email, userId, name, status, new Timestamp(System.currentTimeMillis()));
    }

    private Registration someRegistration(final long eventId, final long userId) {
        final Registration registration = new Registration(em.getReference(Event.class, eventId), em.getReference(User.class, userId), EMAIL, NAME, RegistrationStatus.CONFIRMED);
        return registration;
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheRegistrationRowAndReturnsItWithItsId() {
        // Arrange
        final Registration toCreate = someRegistration(eventId, userId);

        // Act
        final Registration created = registrationDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + created.getId() + " AND event_id = " + eventId
                        + " AND email = '" + EMAIL + "' AND name = '" + NAME + "' AND status = 'CONFIRMED'"));
    }

    // Los avisos por mail (@Async, otro hilo y sin sesion) leen el id del usuario de la inscripcion:
    // tiene que poder leerse aunque la relacion sea solo una referencia.
    @Test
    public void testCreateWithAUserBuiltFromTheSessionStoresItsIdAndItIsReadableOutsideTheSession() {
        // Arrange
        final User fromSession = new User(EMAIL, NAME);
        fromSession.setId(userId);
        final Registration toCreate = new Registration(em.getReference(Event.class, eventId), fromSession, EMAIL, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final Registration created = registrationDao.create(toCreate);
        // Se cierra la sesion de Hibernate: el objeto devuelto queda suelto, como en el hilo del mail.
        em.clear();

        // Assert
        assertEquals(Long.valueOf(userId), created.getUserId());
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + created.getId() + " AND user_id = " + userId));
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEventAndEmailThrowsDataAccessException() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        registrationDao.create(someRegistration(eventId, userId));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdReturnsTheStoredRegistration() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(NAME, found.get().getName());
        assertEquals(RegistrationStatus.CONFIRMED, found.get().getStatus());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final Optional<Registration> found = registrationDao.findByEventAndUserId(eventId, userId);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- countConfirmed() ----

    @Test
    public void testCountConfirmedReturnsZeroWhenNobodyIsRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countConfirmed(eventId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountConfirmedCountsOnlyConfirmedStatus() {
        // Arrange
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final long waitlistedUserId = insertUser("espera@mail.com", NAME);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "espera@mail.com", waitlistedUserId, NAME, RegistrationStatus.WAITLISTED);

        // Act
        final long count = registrationDao.countConfirmed(eventId);

        // Assert
        assertEquals(1, count);
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsEmptyWhenItDoesNotExist() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final Optional<Registration> found = registrationDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindByIdReturnsTheStoredRegistration() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final Optional<Registration> found = registrationDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(EMAIL, found.get().getEmail());
    }

    @Test
    public void testFindByIdReturnsARegistrationWhoseEventAndUserIdsCanBeReadOutsideTheSession() {
        // Arrange: la inscripcion guarda el evento y el usuario como relaciones LAZY (representantes
        // vacios que solo conocen su id). Se "cierra la sesion" (em.clear) para que esos
        // representantes ya no puedan ir a la base: leer el id tiene que seguir andando, porque es lo
        // que usan los servicios y los JSP despues de que la transaccion termino.
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final Registration found = registrationDao.findById(id).get();
        em.clear();

        // Act
        final long foundEventId = found.getEventId();
        final Long foundUserId = found.getUserId();

        // Assert
        assertEquals(eventId, foundEventId);
        assertEquals(Long.valueOf(userId), foundUserId);
    }

    // ---- findUpcomingByUserId() / findPastByUserId() ("Mis inscripciones") ----

    @Test
    public void testFindUpcomingByUserIdReturnsOnlyThatUsersUpcomingRegistrations() {
        // Arrange: senuelos = la inscripcion de otra persona al mismo evento, y una propia a un evento pasado
        final long upcomingEvent = insertEventAt(2 * DAY);
        registerUserAtEvent(upcomingEvent);
        registerUserAtEvent(insertEventAt(-2 * DAY));
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(upcomingEvent, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findUpcomingByUserId(userId, new Date(), 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(upcomingEvent, found.get(0).getEventId());
        assertEquals(EMAIL + upcomingEvent, found.get(0).getEmail());
    }

    @Test
    public void testFindUpcomingByUserIdReturnsEmptyWhenNeverRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> found = registrationDao.findUpcomingByUserId(userId, new Date(), 1, 10);

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindUpcomingByUserIdOrdersNearestFirst() {
        // Arrange
        final long far = insertEventAt(3 * DAY);
        final long near = insertEventAt(1 * DAY);
        registerUserAtEvent(far);
        registerUserAtEvent(near);

        // Act
        final List<Registration> found = registrationDao.findUpcomingByUserId(userId, new Date(), 1, 10);

        // Assert
        assertEquals(2, found.size());
        assertEquals(near, found.get(0).getEventId());
        assertEquals(far, found.get(1).getEventId());
    }

    @Test
    public void testFindUpcomingByUserIdReturnsTheSecondPage() {
        // Arrange: 3 inscripciones de a 2 por pagina -> la pagina 2 es solo la ultima del orden
        final long first = insertEventAt(1 * DAY);
        final long second = insertEventAt(2 * DAY);
        final long third = insertEventAt(3 * DAY);
        registerUserAtEvent(third);
        registerUserAtEvent(first);
        registerUserAtEvent(second);

        // Act
        final List<Registration> page = registrationDao.findUpcomingByUserId(userId, new Date(), 2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(third, page.get(0).getEventId());
    }

    @Test
    public void testFindUpcomingByUserIdReturnsNothingForAPageBeyondTheLast() {
        // Arrange
        registerUserAtEvent(insertEventAt(DAY));

        // Act
        final List<Registration> page = registrationDao.findUpcomingByUserId(userId, new Date(), 5, 2);

        // Assert
        assertTrue(page.isEmpty());
    }

    @Test
    public void testFindPastByUserIdReturnsOnlyThatUsersPastRegistrations() {
        // Arrange: senuelos = una inscripcion propia a un evento que viene, y la de otra persona al mismo evento pasado
        final long pastEvent = insertEventAt(-2 * DAY);
        registerUserAtEvent(pastEvent);
        registerUserAtEvent(insertEventAt(2 * DAY));
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(pastEvent, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findPastByUserId(userId, new Date(), 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(pastEvent, found.get(0).getEventId());
        assertEquals(EMAIL + pastEvent, found.get(0).getEmail());
    }

    @Test
    public void testFindPastByUserIdOrdersMostRecentFirst() {
        // Arrange
        final long old = insertEventAt(-5 * DAY);
        final long recent = insertEventAt(-1 * DAY);
        registerUserAtEvent(old);
        registerUserAtEvent(recent);

        // Act
        final List<Registration> found = registrationDao.findPastByUserId(userId, new Date(), 1, 10);

        // Assert
        assertEquals(2, found.size());
        assertEquals(recent, found.get(0).getEventId());
        assertEquals(old, found.get(1).getEventId());
    }

    @Test
    public void testFindPastByUserIdReturnsTheSecondPage() {
        // Arrange: 3 inscripciones de a 2 por pagina -> la pagina 2 es solo la mas vieja
        final long mostRecent = insertEventAt(-1 * DAY);
        final long middle = insertEventAt(-2 * DAY);
        final long oldest = insertEventAt(-3 * DAY);
        registerUserAtEvent(oldest);
        registerUserAtEvent(mostRecent);
        registerUserAtEvent(middle);

        // Act
        final List<Registration> page = registrationDao.findPastByUserId(userId, new Date(), 2, 2);

        // Assert
        assertEquals(1, page.size());
        assertEquals(oldest, page.get(0).getEventId());
    }

    // ---- countUpcomingByUserId() / countPastByUserId() ----

    @Test
    public void testCountUpcomingByUserIdCountsOnlyThatUsersUpcomingRegistrations() {
        // Arrange: senuelos = una propia a un evento pasado y la de otra persona a un evento que viene
        final long upcomingEvent = insertEventAt(2 * DAY);
        registerUserAtEvent(upcomingEvent);
        registerUserAtEvent(insertEventAt(-2 * DAY));
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(upcomingEvent, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final long count = registrationDao.countUpcomingByUserId(userId, new Date());

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountPastByUserIdCountsOnlyThatUsersPastRegistrations() {
        // Arrange: senuelos = una propia a un evento que viene y la de otra persona a un evento pasado
        final long pastEvent = insertEventAt(-2 * DAY);
        registerUserAtEvent(pastEvent);
        registerUserAtEvent(insertEventAt(2 * DAY));
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(pastEvent, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final long count = registrationDao.countPastByUserId(userId, new Date());

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountUpcomingAndPastTogetherAreAllTheUsersRegistrations() {
        // Arrange
        registerUserAtEvent(insertEventAt(1 * DAY));
        registerUserAtEvent(insertEventAt(2 * DAY));
        registerUserAtEvent(insertEventAt(-1 * DAY));
        final Date now = new Date();

        // Act
        final long total = registrationDao.countUpcomingByUserId(userId, now)
                + registrationDao.countPastByUserId(userId, now);

        // Assert
        assertEquals(3, total);
    }

    // ---- countByUserId() (badge de "Mis inscripciones") ----

    @Test
    public void testCountByUserIdCountsOnlyThatUsersRegistrations() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final long count = registrationDao.countByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountByUserIdReturnsZeroWhenNeverRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    // ---- findByEventId() ("Ver inscriptos" del organizador) ----

    @Test
    public void testFindByEventIdReturnsAllRegistrationsForThatEvent() {
        // Arrange
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertEquals(2, found.size());
    }

    @Test
    public void testFindByEventIdDoesNotReturnRegistrationsOfOtherEvents() {
        // Arrange
        final long otherEventId = insertEvent();
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(otherEventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testFindByEventIdReturnsEmptyWhenNobodyIsRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- findByEventId() paginado + countByEventId() ----

    @Test
    public void testFindByEventIdPagesTheRegistrationsOldestFirstWithoutRepeating() {
        // Arrange: 3 inscripciones con fechas distintas, de a 2 por pagina
        final long now = System.currentTimeMillis();
        final long u1 = insertUser("uno@mail.com", "Uno");
        final long u2 = insertUser("dos@mail.com", "Dos");
        final long u3 = insertUser("tres@mail.com", "Tres");
        insertRegistrationAt(eventId, "tres@mail.com", u3, "Tres", RegistrationStatus.CONFIRMED, new Timestamp(now - 1000));
        insertRegistrationAt(eventId, "uno@mail.com", u1, "Uno", RegistrationStatus.CONFIRMED, new Timestamp(now - 3000));
        insertRegistrationAt(eventId, "dos@mail.com", u2, "Dos", RegistrationStatus.CONFIRMED, new Timestamp(now - 2000));

        // Act
        final List<Registration> pageOne = registrationDao.findByEventId(eventId, 1, 2);
        final List<Registration> pageTwo = registrationDao.findByEventId(eventId, 2, 2);

        // Assert: la mas antigua primero y la ultima pagina trae solo la que sobra
        assertEquals(2, pageOne.size());
        assertEquals("uno@mail.com", pageOne.get(0).getEmail());
        assertEquals("dos@mail.com", pageOne.get(1).getEmail());
        assertEquals(1, pageTwo.size());
        assertEquals("tres@mail.com", pageTwo.get(0).getEmail());
    }

    @Test
    public void testFindByEventIdPagedDoesNotReturnRegistrationsOfOtherEvents() {
        // Arrange
        final long otherEventId = insertEvent();
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(otherEventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> found = registrationDao.findByEventId(eventId, 1, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(EMAIL, found.get(0).getEmail());
    }

    @Test
    public void testCountByEventIdCountsOnlyThatEventsRegistrations() {
        // Arrange
        final long otherEventId = insertEvent();
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "otro@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);
        final long thirdUserId = insertUser("tercero@mail.com", "Tercero");
        insertRegistration(otherEventId, "tercero@mail.com", thirdUserId, "Tercero", RegistrationStatus.CONFIRMED);

        // Act
        final long count = registrationDao.countByEventId(eventId);

        // Assert
        assertEquals(2L, count);
    }

    @Test
    public void testCountByEventIdReturnsZeroWhenNobodyIsRegistered() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countByEventId(eventId);

        // Assert
        assertEquals(0L, count);
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheRegistrationAndReturnsTrue() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final boolean result = registrationDao.delete(id);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration", "id = " + id));
    }

    // ---- countPending() ----

    @Test
    public void testCountPendingReturnsZeroWhenNobodyIsWaitingForReview() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final long count = registrationDao.countPending(eventId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountPendingCountsOnlyPendingStatus() {
        // Arrange
        final long pendingUserId = insertUser("pendiente@mail.com", NAME);
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final long rejectedUserId = insertUser("rechazado@mail.com", NAME);
        insertRegistration(eventId, "pendiente@mail.com", pendingUserId, NAME, RegistrationStatus.PENDING);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "rechazado@mail.com", rejectedUserId, NAME, RegistrationStatus.REJECTED);

        // Act
        final long count = registrationDao.countPending(eventId);

        // Assert
        assertEquals(1, count);
    }

    // ---- countByEventsAndStatus() ----

    @Test
    public void testCountByEventsAndStatusCountsPerEventOnlyThatStatus() {
        // Arrange: dos eventos pedidos, mas un evento senuelo que no se pide; en cada uno, otros estados de senuelo
        final long secondEvent = insertEventAt(DAY);
        final long decoyEvent = insertEventAt(2 * DAY);
        final long ana = insertUser("ana@mail.com", "Ana");
        final long beto = insertUser("beto@mail.com", "Beto");
        final long carla = insertUser("carla@mail.com", "Carla");
        insertRegistration(eventId, "ana@mail.com", ana, "Ana", RegistrationStatus.PENDING);
        insertRegistration(eventId, "beto@mail.com", beto, "Beto", RegistrationStatus.PENDING);
        insertRegistration(eventId, "carla@mail.com", carla, "Carla", RegistrationStatus.CONFIRMED);
        insertRegistration(secondEvent, "ana@mail.com", ana, "Ana", RegistrationStatus.PENDING);
        insertRegistration(secondEvent, "beto@mail.com", beto, "Beto", RegistrationStatus.REJECTED);
        insertRegistration(decoyEvent, "ana@mail.com", ana, "Ana", RegistrationStatus.PENDING);

        // Act
        final Map<Long, Long> counts = registrationDao.countByEventsAndStatus(
                Arrays.asList(eventId, secondEvent), RegistrationStatus.PENDING);

        // Assert
        assertEquals(2, counts.size());
        assertEquals(Long.valueOf(2), counts.get(eventId));
        assertEquals(Long.valueOf(1), counts.get(secondEvent));
    }

    @Test
    public void testCountByEventsAndStatusOmitsAnEventWithoutThatStatus() {
        // Arrange: el evento solo tiene una inscripcion confirmada
        final long ana = insertUser("ana@mail.com", "Ana");
        insertRegistration(eventId, "ana@mail.com", ana, "Ana", RegistrationStatus.CONFIRMED);

        // Act
        final Map<Long, Long> counts = registrationDao.countByEventsAndStatus(
                Collections.singletonList(eventId), RegistrationStatus.PENDING);

        // Assert
        assertTrue(counts.isEmpty());
    }

    @Test
    public void testCountByEventsAndStatusReturnsAnEmptyMapForNoEvents() {
        // Arrange: hay una inscripcion pendiente, pero no se pide ningun evento
        final long ana = insertUser("ana@mail.com", "Ana");
        insertRegistration(eventId, "ana@mail.com", ana, "Ana", RegistrationStatus.PENDING);

        // Act
        final Map<Long, Long> counts = registrationDao.countByEventsAndStatus(
                Collections.<Long>emptyList(), RegistrationStatus.PENDING);

        // Assert
        assertTrue(counts.isEmpty());
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventReturnsEmptyWhenNobodyIsWaitingForReview() {
        // Arrange: no se inserta ninguna inscripcion.

        // Act
        final List<Registration> pending = registrationDao.findPendingByEvent(eventId);

        // Assert
        assertTrue(pending.isEmpty());
    }

    @Test
    public void testFindPendingByEventReturnsOnlyPendingOrderedByOldestFirst() {
        // Arrange
        final long firstUserId = insertUser("primero@mail.com", NAME);
        final long secondUserId = insertUser("segundo@mail.com", NAME);
        final long confirmedUserId = insertUser("confirmado@mail.com", NAME);
        final Timestamp antes = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp despues = new Timestamp(System.currentTimeMillis() - 1000);
        insertRegistrationAt(eventId, "primero@mail.com", firstUserId, NAME, RegistrationStatus.PENDING, antes);
        insertRegistrationAt(eventId, "segundo@mail.com", secondUserId, NAME, RegistrationStatus.PENDING, despues);
        insertRegistration(eventId, "confirmado@mail.com", confirmedUserId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> pending = registrationDao.findPendingByEvent(eventId);

        // Assert
        assertEquals(2, pending.size());
        assertEquals("primero@mail.com", pending.get(0).getEmail());
        assertEquals("segundo@mail.com", pending.get(1).getEmail());
    }

    // ---- updateStatus() ----

    @Test
    public void testUpdateStatusChangesTheStatusOfThatRegistrationAndReturnsTrue() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);

        // Act
        final boolean result = registrationDao.updateStatus(id, RegistrationStatus.CONFIRMED);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + id + " AND status = 'CONFIRMED'"));
    }

    @Test
    public void testUpdateStatusLeavesTheOtherRegistrationsAlone() {
        // Arrange
        final long id = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);
        final long otherUserId = insertUser("otra@mail.com", "Otra");
        final long otherId = insertRegistration(eventId, "otra@mail.com", otherUserId, "Otra",
                RegistrationStatus.PENDING);

        // Act
        registrationDao.updateStatus(id, RegistrationStatus.REJECTED);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + otherId + " AND status = 'PENDING'"));
    }

    // ---- findNextWaitlisted() ----

    private long insertWaitlistedAt(final long eventId, final String email, final long millisAgo) {
        final long waiterId = insertUser(email, "Espera");
        return insertRegistrationAt(eventId, email, waiterId, "Espera", RegistrationStatus.WAITLISTED,
                new Timestamp(System.currentTimeMillis() - millisAgo));
    }

    @Test
    public void testFindNextWaitlistedOrdersByWhoArrivedFirst() {
        // Arrange
        final long last = insertWaitlistedAt(eventId, "tercero@mail.com", 1000);
        final long first = insertWaitlistedAt(eventId, "primero@mail.com", 3000);
        final long second = insertWaitlistedAt(eventId, "segundo@mail.com", 2000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 10);

        // Assert
        assertEquals(first, found.get(0).getId().longValue());
        assertEquals(second, found.get(1).getId().longValue());
        assertEquals(last, found.get(2).getId().longValue());
    }

    @Test
    public void testFindNextWaitlistedReturnsAtMostTheLimit() {
        // Arrange
        final long first = insertWaitlistedAt(eventId, "primero@mail.com", 3000);
        insertWaitlistedAt(eventId, "segundo@mail.com", 2000);
        insertWaitlistedAt(eventId, "tercero@mail.com", 1000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 1);

        // Assert
        assertEquals(1, found.size());
        assertEquals(first, found.get(0).getId().longValue());
    }

    @Test
    public void testFindNextWaitlistedIgnoresOtherStatusesAndOtherEvents() {
        // Arrange
        final long waiting = insertWaitlistedAt(eventId, "espera@mail.com", 1000);
        insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);
        final long otherEventId = insertEvent();
        insertWaitlistedAt(otherEventId, "otro-evento@mail.com", 1000);

        // Act
        final List<Registration> found = registrationDao.findNextWaitlisted(eventId, 10);

        // Assert
        assertEquals(1, found.size());
        assertEquals(waiting, found.get(0).getId().longValue());
    }

    @Test
    public void testDeleteOfARegistrationThatDoesNotExistDeletesNothingAndReturnsFalse() {
        // Arrange: otra inscripcion, que no es la del Act. Tiene que quedar.
        final long other = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final boolean result = registrationDao.delete(999_999L);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration", "id = " + other));
    }

    @Test
    public void testUpdateStatusOfARegistrationThatDoesNotExistChangesNothingAndReturnsFalse() {
        // Arrange: otra inscripcion, que no es la del Act. Tiene que seguir PENDING.
        final long other = insertRegistration(eventId, EMAIL, userId, NAME, RegistrationStatus.PENDING);

        // Act
        final boolean result = registrationDao.updateStatus(999_999L, RegistrationStatus.CONFIRMED);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "registration",
                "id = " + other + " AND status = 'PENDING'"));
    }

    // ---- findAttendedEventIds() ----

    @Test
    public void testFindAttendedEventIdsReturnsConfirmedPastEventsNewestFirst() {
        // Arrange
        final long tenDaysAgo = insertEventAt(-10 * DAY);
        final long twentyDaysAgo = insertEventAt(-20 * DAY);
        final long fiveDaysAgo = insertEventAt(-5 * DAY);
        registerUserAtEvent(tenDaysAgo);
        registerUserAtEvent(twentyDaysAgo);
        registerUserAtEvent(fiveDaysAgo);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 10);

        // Assert
        assertEquals(Arrays.asList(fiveDaysAgo, tenDaysAgo, twentyDaysAgo), ids);
    }

    @Test
    public void testFindAttendedEventIdsLeavesOutEventsThatHaveNotHappenedYet() {
        // Arrange
        final long past = insertEventAt(-DAY);
        final long future = insertEventAt(DAY);
        registerUserAtEvent(past);
        registerUserAtEvent(future);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 10);

        // Assert
        assertEquals(Collections.singletonList(past), ids);
    }

    @Test
    public void testFindAttendedEventIdsOnlyCountsConfirmedRegistrations() {
        // Arrange
        final long confirmed = insertEventAt(-DAY);
        final long waitlisted = insertEventAt(-2 * DAY);
        final long pending = insertEventAt(-3 * DAY);
        final long rejected = insertEventAt(-4 * DAY);
        insertRegistration(confirmed, EMAIL + "1", userId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(waitlisted, EMAIL + "2", userId, NAME, RegistrationStatus.WAITLISTED);
        insertRegistration(pending, EMAIL + "3", userId, NAME, RegistrationStatus.PENDING);
        insertRegistration(rejected, EMAIL + "4", userId, NAME, RegistrationStatus.REJECTED);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 10);

        // Assert
        assertEquals(Collections.singletonList(confirmed), ids);
    }

    @Test
    public void testFindAttendedEventIdsLeavesOutCancelledEvents() {
        // Arrange: un evento cancelado (o dado de baja por un admin, mismo cancelled_at) no ocurrio
        final long held = insertEventAt(-DAY);
        final long cancelled = insertEventAt(-2 * DAY);
        registerUserAtEvent(held);
        registerUserAtEvent(cancelled);
        jdbcTemplate.update("UPDATE event SET cancelled_at = ? WHERE id = ?",
                new Timestamp(System.currentTimeMillis() - 3 * DAY), cancelled);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 10);

        // Assert
        assertEquals(Collections.singletonList(held), ids);
    }

    @Test
    public void testFindAttendedEventIdsOnlyReturnsThatPersonsEvents() {
        // Arrange
        final long mine = insertEventAt(-DAY);
        final long theirs = insertEventAt(-2 * DAY);
        final long otherUserId = insertUser("otra@mail.com", "Otra");
        registerUserAtEvent(mine);
        insertRegistration(theirs, "otra@mail.com", otherUserId, "Otra", RegistrationStatus.CONFIRMED);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 10);

        // Assert
        assertEquals(Collections.singletonList(mine), ids);
    }

    @Test
    public void testFindAttendedEventIdsStopsAtTheLimitKeepingTheMostRecent() {
        // Arrange
        final long newest = insertEventAt(-DAY);
        final long middle = insertEventAt(-2 * DAY);
        final long oldest = insertEventAt(-3 * DAY);
        registerUserAtEvent(newest);
        registerUserAtEvent(middle);
        registerUserAtEvent(oldest);

        // Act
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, new Date(), 2);

        // Assert
        assertEquals(Arrays.asList(newest, middle), ids);
    }

    @Test
    public void testFindAttendedEventIdsIsEmptyWithoutRegistrations() {
        assertTrue(registrationDao.findAttendedEventIds(userId, new Date(), 10).isEmpty());
    }

    // ---- findByUserIdAndEventIds() ----

    @Test
    public void testFindByUserIdAndEventIdsReturnsTheOwnRegistrationInEachEvent() {
        // Arrange
        final long a = insertEventAt(DAY);
        final long b = insertEventAt(2 * DAY);
        final long c = insertEventAt(3 * DAY);
        insertRegistration(a, EMAIL + "a", userId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(b, EMAIL + "b", userId, NAME, RegistrationStatus.WAITLISTED);

        // Act
        final List<Registration> result = registrationDao.findByUserIdAndEventIds(userId, Arrays.asList(a, b, c));

        // Assert: en c no esta inscripto
        assertEquals(2, result.size());
        assertEquals(RegistrationStatus.CONFIRMED, statusOfEvent(result, a));
        assertEquals(RegistrationStatus.WAITLISTED, statusOfEvent(result, b));
    }

    @Test
    public void testFindByUserIdAndEventIdsIgnoresOtherUsersRegistrations() {
        // Arrange
        final long a = insertEventAt(DAY);
        final long otherUserId = insertUser("otra@mail.com", "Otra");
        insertRegistration(a, "otra@mail.com", otherUserId, "Otra", RegistrationStatus.CONFIRMED);

        // Act + Assert
        assertTrue(registrationDao.findByUserIdAndEventIds(userId, Collections.singletonList(a)).isEmpty());
    }

    @Test
    public void testFindByUserIdAndEventIdsIgnoresEventsNotAsked() {
        // Arrange
        final long asked = insertEventAt(DAY);
        final long notAsked = insertEventAt(2 * DAY);
        insertRegistration(asked, EMAIL + "a", userId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(notAsked, EMAIL + "b", userId, NAME, RegistrationStatus.CONFIRMED);

        // Act
        final List<Registration> result = registrationDao.findByUserIdAndEventIds(userId, Collections.singletonList(asked));

        // Assert
        assertEquals(1, result.size());
        assertEquals(asked, result.get(0).getEventId());
    }

    @Test
    public void testFindByUserIdAndEventIdsWithNoIdsIsEmpty() {
        assertTrue(registrationDao.findByUserIdAndEventIds(userId, Collections.<Long>emptyList()).isEmpty());
    }

    private static RegistrationStatus statusOfEvent(final List<Registration> registrations, final long eventId) {
        return registrations.stream().filter(r -> r.getEventId() == eventId).findFirst().get().getStatus();
    }

    // ---- findAccessById() / findAccessByEventAndUserId() ----

    @Test
    public void testFindAccessByIdReturnsTheUserEventAndStatusOfThatRegistration() {
        // Arrange: la buscada, mas una senuelo de otro usuario, otro evento y otro estado
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        final long otherEventId = insertEventAt(1000);
        final long registrationId = insertRegistration(eventId, "uno@mail.com", userId, NAME, RegistrationStatus.PENDING);
        insertRegistration(otherEventId, "dos@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final Optional<RegistrationAccess> result = registrationDao.findAccessById(registrationId);

        // Assert
        assertEquals(Long.valueOf(userId), result.get().getUserId());
        assertEquals(eventId, result.get().getEventId());
        assertEquals(RegistrationStatus.PENDING, result.get().getStatus());
    }

    @Test
    public void testFindAccessByIdIsEmptyWhenTheRegistrationDoesNotExist() {
        // Act
        final Optional<RegistrationAccess> result = registrationDao.findAccessById(987654L);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindAccessByEventAndUserIdReturnsOnlyThatUsersRegistrationInThatEvent() {
        // Arrange: la buscada, mas senuelos del mismo usuario en otro evento y de otro usuario en este
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        final long otherEventId = insertEventAt(1000);
        insertRegistration(eventId, "uno@mail.com", userId, NAME, RegistrationStatus.WAITLISTED);
        insertRegistration(otherEventId, "dos@mail.com", userId, NAME, RegistrationStatus.CONFIRMED);
        insertRegistration(eventId, "tres@mail.com", otherUserId, "Otro", RegistrationStatus.REJECTED);

        // Act
        final Optional<RegistrationAccess> result = registrationDao.findAccessByEventAndUserId(eventId, userId);

        // Assert
        assertEquals(Long.valueOf(userId), result.get().getUserId());
        assertEquals(eventId, result.get().getEventId());
        assertEquals(RegistrationStatus.WAITLISTED, result.get().getStatus());
    }

    @Test
    public void testFindAccessByEventAndUserIdIsEmptyWhenThereIsNoRegistration() {
        // Arrange: solo hay una de otro usuario
        final long otherUserId = insertUser("otro@mail.com", "Otro");
        insertRegistration(eventId, "tres@mail.com", otherUserId, "Otro", RegistrationStatus.CONFIRMED);

        // Act
        final Optional<RegistrationAccess> result = registrationDao.findAccessByEventAndUserId(eventId, userId);

        // Assert
        assertFalse(result.isPresent());
    }
}
