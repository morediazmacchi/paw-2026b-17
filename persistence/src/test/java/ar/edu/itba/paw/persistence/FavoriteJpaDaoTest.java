package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Unidad 5 - Unit Testing: favoriteDao (el objeto bajo test) solo se toca en
// el Act. Arrange/Assert son siempre SQL directo, nunca otro metodo de
// favoriteDao (por ejemplo, el Assert de add() NO usa isFavorite()).
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class FavoriteJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private DataSource ds;

    @Autowired
    private FavoriteDao favoriteDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long eventId;
    private long otherEventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = insertUser("fan@mail.com");
        eventId = insertEvent("Recital");
        otherEventId = insertEvent("Feria");
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Ana");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    // El evento no necesita categoria para estos tests (son de favoritos, no
    // de categorias): sin la columna category_id, una fila de event ya no
    // depende de que exista una.
    private long insertEvent(final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com");
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    private void insertFavorite(final long userId, final long eventId) {
        jdbcTemplate.update("INSERT INTO favorite (user_id, event_id) VALUES (?, ?)", userId, eventId);
    }

    private String favoriteWhere(final long userId, final long eventId) {
        return "user_id = " + userId + " AND event_id = " + eventId;
    }

    // ---- add() ----

    @Test
    public void testAddInsertsTheFavoriteRowAndReturnsTrue() {
        // Arrange: userId/eventId ya vienen del setUp, sin favorito todavia.

        // Act
        final boolean result = favoriteDao.add(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    @Test
    public void testAddWhenItIsAlreadyAFavoriteKeepsOneRowAndReturnsFalse() {
        // Arrange: el favorito ya esta (insertado por SQL, no con add()).
        insertFavorite(userId, eventId);

        // Act
        // No hay ON CONFLICT (no es portable a HSQLDB): add() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final boolean result = favoriteDao.add(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    // ---- remove() ----

    @Test
    public void testRemoveDeletesTheFavoriteRowAndReturnsTrue() {
        // Arrange
        insertFavorite(userId, eventId);

        // Act
        final boolean result = favoriteDao.remove(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    @Test
    public void testRemoveWhenItWasNeverAFavoriteDeletesNothingAndReturnsFalse() {
        // Arrange: un favorito de la misma persona, pero de otro evento. Tiene que quedar.
        final long otherEventId = insertEvent("Otro");
        insertFavorite(userId, otherEventId);

        // Act
        final boolean result = favoriteDao.remove(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, otherEventId)));
    }

    // ---- isFavorite() ----

    @Test
    public void testIsFavoriteReturnsTrueWhenFavorited() {
        // Arrange
        insertFavorite(userId, eventId);

        // Act
        final boolean result = favoriteDao.isFavorite(userId, eventId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFavoriteReturnsFalseWhenNeverAdded() {
        // Arrange: no hay ningun favorito guardado.

        // Act
        final boolean result = favoriteDao.isFavorite(userId, eventId);

        // Assert
        assertFalse(result);
    }

    // ---- findFavoriteEventIds() (los corazones de una pagina) ----

    @Test
    public void testFindFavoriteEventIdsReturnsOnlyTheGivenEventsThatAreFavorites() {
        // Arrange: favorito de dos eventos, se pregunta por uno de ellos y por uno que no es favorito
        final long notFavorite = insertEvent("Libre");
        insertFavorite(userId, eventId);
        insertFavorite(userId, otherEventId);

        // Act
        final Set<Long> found = favoriteDao.findFavoriteEventIds(userId, Arrays.asList(eventId, notFavorite));

        // Assert: otherEventId es favorito pero no se pregunto por el
        assertEquals(Collections.singleton(eventId), found);
    }

    @Test
    public void testFindFavoriteEventIdsExcludesOtherUsersFavorites() {
        // Arrange: senuelo = el mismo evento es favorito de OTRA persona
        final long otherUserId = insertUser("otro@mail.com");
        insertFavorite(otherUserId, eventId);

        // Act
        final Set<Long> found = favoriteDao.findFavoriteEventIds(userId, Arrays.asList(eventId, otherEventId));

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindFavoriteEventIdsWithNoEventsReturnsEmpty() {
        // Arrange: tiene favoritos, pero no se pregunta por ninguno
        insertFavorite(userId, eventId);

        // Act
        final Set<Long> found = favoriteDao.findFavoriteEventIds(userId, Collections.emptyList());

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- countByUserId() ----

    @Test
    public void testCountByUserIdCountsOnlyThatUsersFavorites() {
        // Arrange: senuelo = el favorito de otra persona
        final long otherUserId = insertUser("otra@mail.com");
        insertFavorite(userId, eventId);
        insertFavorite(userId, otherEventId);
        insertFavorite(otherUserId, eventId);

        // Act
        final long count = favoriteDao.countByUserId(userId);

        // Assert
        assertEquals(2, count);
    }

    // ---- countViewableByUserId() ----

    private long insertEventWith(final String title, final String visibility, final String approvalStatus,
                                 final Long creatorId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, visibility, approval_status, creator_id) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com",
                visibility, approvalStatus, creatorId);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    @Test
    public void testCountViewableCountsAPublicApprovedEvent() {
        // Arrange
        insertFavorite(userId, insertEventWith("Publico", "PUBLIC", "APPROVED", null));

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountViewableIgnoresAPublicEventThatIsNotApproved() {
        // Arrange: pendiente y rechazado, los dos publicos
        insertFavorite(userId, insertEventWith("Pendiente", "PUBLIC", "PENDING", null));
        insertFavorite(userId, insertEventWith("Rechazado", "PUBLIC", "REJECTED", null));

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountViewableIgnoresAPrivateEventWithoutRegistrationNorInvitation() {
        // Arrange: senuelos = una invitacion y una inscripcion de OTRA persona al mismo evento
        final long otherUserId = insertUser("otra@mail.com");
        final long privateEvent = insertEventWith("Privado", "PRIVATE", "APPROVED", null);
        insertFavorite(userId, privateEvent);
        jdbcTemplate.update("INSERT INTO event_invitation (event_id, email, invited_by, user_id) VALUES (?, ?, ?, ?)",
                privateEvent, "otra@mail.com", "creador@mail.com", otherUserId);
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, name, status) VALUES (?, ?, ?, ?, ?)",
                privateEvent, "otra@mail.com", otherUserId, "Otra", "CONFIRMED");

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testCountViewableCountsAPrivateEventWithAnInvitationForThatUser() {
        // Arrange
        final long privateEvent = insertEventWith("Privado", "PRIVATE", "APPROVED", null);
        insertFavorite(userId, privateEvent);
        jdbcTemplate.update("INSERT INTO event_invitation (event_id, email, invited_by, user_id) VALUES (?, ?, ?, ?)",
                privateEvent, "fan@mail.com", "creador@mail.com", userId);

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountViewableCountsAPrivateEventWithARejectedRegistration() {
        // Arrange: una inscripcion en CUALQUIER estado da acceso, tambien la rechazada
        final long privateEvent = insertEventWith("Privado", "PRIVATE", "APPROVED", null);
        insertFavorite(userId, privateEvent);
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, name, status) VALUES (?, ?, ?, ?, ?)",
                privateEvent, "fan@mail.com", userId, "Ana", "REJECTED");

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountViewableCountsTheUsersOwnEventEvenIfPrivateAndPending() {
        // Arrange
        insertFavorite(userId, insertEventWith("Propio", "PRIVATE", "PENDING", userId));

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testCountViewableIgnoresTheFavoritesOfOtherUsers() {
        // Arrange
        final long otherUserId = insertUser("otra@mail.com");
        insertFavorite(otherUserId, insertEventWith("Publico", "PUBLIC", "APPROVED", null));

        // Act
        final long count = favoriteDao.countViewableByUserId(userId);

        // Assert
        assertEquals(0, count);
    }

    // ---- findViewableEventsByUser() / findEventsByUser(userId, page, pageSize) (paginado en SQL) ----

    // `minutesAgo` fija el orden de forma explicita: sin esto los INSERT de un mismo test comparten el now().
    private void insertFavoriteMinutesAgo(final long userId, final long eventId, final int minutesAgo) {
        jdbcTemplate.update("INSERT INTO favorite (user_id, event_id, created_at) VALUES (?, ?, ?)",
                userId, eventId, new Timestamp(System.currentTimeMillis() - minutesAgo * 60_000L));
    }

    private static List<Long> idsOf(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    @Test
    public void testFindViewableEventIdsReturnsTheRequestedSliceNewestFirst() {
        // Arrange: 3 publicos aprobados; el favorito mas reciente es el "c"
        final long a = insertEventWith("A", "PUBLIC", "APPROVED", null);
        final long b = insertEventWith("B", "PUBLIC", "APPROVED", null);
        final long c = insertEventWith("C", "PUBLIC", "APPROVED", null);
        insertFavoriteMinutesAgo(userId, a, 30);
        insertFavoriteMinutesAgo(userId, b, 20);
        insertFavoriteMinutesAgo(userId, c, 10);

        // Act
        final List<Long> secondPage = idsOf(favoriteDao.findViewableEventsByUser(userId, 2, 2));

        // Assert: pagina 1 = [c, b]; la 2 trae solo lo que queda
        assertEquals(Collections.singletonList(a), secondPage);
    }

    @Test
    public void testFindViewableEventIdsFirstPageIsOrderedNewestFirst() {
        // Arrange
        final long a = insertEventWith("A", "PUBLIC", "APPROVED", null);
        final long b = insertEventWith("B", "PUBLIC", "APPROVED", null);
        final long c = insertEventWith("C", "PUBLIC", "APPROVED", null);
        insertFavoriteMinutesAgo(userId, a, 30);
        insertFavoriteMinutesAgo(userId, b, 20);
        insertFavoriteMinutesAgo(userId, c, 10);

        // Act
        final List<Long> firstPage = idsOf(favoriteDao.findViewableEventsByUser(userId, 1, 2));

        // Assert
        assertEquals(Arrays.asList(c, b), firstPage);
    }

    @Test
    public void testFindViewableEventIdsAppliesTheVisibilityFilterBeforeTheLimit() {
        // Arrange: el favorito MAS reciente es de un privado sin acceso. Si el LIMIT se aplicara antes del
        // filtro, la pagina de tamaño 1 saldria vacia; tiene que salir el mas reciente de los que SI se ven.
        final long visible = insertEventWith("Visible", "PUBLIC", "APPROVED", null);
        final long hidden = insertEventWith("Oculto", "PRIVATE", "APPROVED", null);
        insertFavoriteMinutesAgo(userId, visible, 20);
        insertFavoriteMinutesAgo(userId, hidden, 10);

        // Act
        final List<Long> found = idsOf(favoriteDao.findViewableEventsByUser(userId, 1, 1));

        // Assert
        assertEquals(Collections.singletonList(visible), found);
    }

    @Test
    public void testFindViewableEventIdsIncludesOwnEventsAndInvitedPrivateOnesAndSkipsPendingOnes() {
        // Arrange
        final long own = insertEventWith("Propio", "PRIVATE", "PENDING", userId);
        final long invited = insertEventWith("Invitado", "PRIVATE", "APPROVED", null);
        final long pending = insertEventWith("Pendiente", "PUBLIC", "PENDING", null);
        insertFavoriteMinutesAgo(userId, own, 30);
        insertFavoriteMinutesAgo(userId, invited, 20);
        insertFavoriteMinutesAgo(userId, pending, 10);
        jdbcTemplate.update("INSERT INTO event_invitation (event_id, email, invited_by, user_id) VALUES (?, ?, ?, ?)",
                invited, "fan@mail.com", "creador@mail.com", userId);

        // Act
        final List<Long> found = idsOf(favoriteDao.findViewableEventsByUser(userId, 1, 10));

        // Assert
        assertEquals(Arrays.asList(invited, own), found);
    }

    @Test
    public void testFindViewableEventIdsExcludesTheFavoritesOfOtherUsers() {
        // Arrange: senuelo = un favorito visible de OTRA persona
        final long otherUserId = insertUser("otra@mail.com");
        final long mine = insertEventWith("Mio", "PUBLIC", "APPROVED", null);
        final long theirs = insertEventWith("Suyo", "PUBLIC", "APPROVED", null);
        insertFavoriteMinutesAgo(userId, mine, 20);
        insertFavoriteMinutesAgo(otherUserId, theirs, 10);

        // Act
        final List<Long> found = idsOf(favoriteDao.findViewableEventsByUser(userId, 1, 10));

        // Assert
        assertEquals(Collections.singletonList(mine), found);
    }

    @Test
    public void testFindViewableEventIdsBeyondTheLastPageIsEmpty() {
        // Arrange
        insertFavoriteMinutesAgo(userId, insertEventWith("A", "PUBLIC", "APPROVED", null), 10);

        // Act
        final List<Long> found = idsOf(favoriteDao.findViewableEventsByUser(userId, 2, 10));

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    public void testFindEventIdsPageReturnsTheRequestedSliceOfAnyEventNewestFirst() {
        // Arrange: lo que ve un admin = todos, tambien un privado sin acceso y uno pendiente
        final long a = insertEventWith("A", "PRIVATE", "APPROVED", null);
        final long b = insertEventWith("B", "PUBLIC", "PENDING", null);
        final long c = insertEventWith("C", "PUBLIC", "APPROVED", null);
        insertFavoriteMinutesAgo(userId, a, 30);
        insertFavoriteMinutesAgo(userId, b, 20);
        insertFavoriteMinutesAgo(userId, c, 10);

        // Act
        final List<Long> secondPage = idsOf(favoriteDao.findEventsByUser(userId, 2, 2));

        // Assert: pagina 1 = [c, b]
        assertEquals(Collections.singletonList(a), secondPage);
    }

    @Test
    public void testFindEventIdsPageExcludesTheFavoritesOfOtherUsers() {
        // Arrange
        final long otherUserId = insertUser("otra@mail.com");
        insertFavoriteMinutesAgo(userId, eventId, 20);
        insertFavoriteMinutesAgo(otherUserId, otherEventId, 10);

        // Act
        final List<Long> found = idsOf(favoriteDao.findEventsByUser(userId, 1, 10));

        // Assert
        assertEquals(Collections.singletonList(eventId), found);
    }
}
