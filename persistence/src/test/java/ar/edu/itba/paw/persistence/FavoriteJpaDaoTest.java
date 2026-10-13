package ar.edu.itba.paw.persistence;

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
    public void testAddInsertsTheFavoriteRow() {
        // Arrange: userId/eventId ya vienen del setUp, sin favorito todavia.

        // Act
        favoriteDao.add(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    @Test
    public void testAddWhenItIsAlreadyAFavoriteIsIdempotent() {
        // Arrange: el favorito ya esta (insertado por SQL, no con add()).
        insertFavorite(userId, eventId);

        // Act
        // No hay ON CONFLICT (no es portable a HSQLDB): add() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        favoriteDao.add(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    // ---- remove() ----

    @Test
    public void testRemoveDeletesTheFavoriteRow() {
        // Arrange
        insertFavorite(userId, eventId);

        // Act
        favoriteDao.remove(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
    }

    @Test
    public void testRemoveWhenNeverAddedDoesNothing() {
        // Arrange: no hay ningun favorito guardado.

        // Act
        favoriteDao.remove(userId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "favorite", favoriteWhere(userId, eventId)));
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

    // ---- findEventIdsByUser() ----

    @Test
    public void testFindEventIdsByUserReturnsAllFavoritesOfThatUser() {
        // Arrange
        insertFavorite(userId, eventId);
        insertFavorite(userId, otherEventId);

        // Act
        final List<Long> found = favoriteDao.findEventIdsByUser(userId);

        // Assert
        assertEquals(2, found.size());
        assertTrue(found.contains(eventId));
        assertTrue(found.contains(otherEventId));
    }

    @Test
    public void testFindEventIdsByUserExcludesOtherUsersFavorites() {
        // Arrange
        final long otherUserId = insertUser("otro@mail.com");
        insertFavorite(userId, eventId);
        insertFavorite(otherUserId, otherEventId);

        // Act
        final List<Long> found = favoriteDao.findEventIdsByUser(userId);

        // Assert
        assertEquals(1, found.size());
        assertTrue(found.contains(eventId));
    }

    @Test
    public void testFindEventIdsByUserReturnsEmptyWhenNoneFavorited() {
        // Arrange: no hay ningun favorito guardado.

        // Act
        final List<Long> found = favoriteDao.findEventIdsByUser(userId);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ---- valores de retorno (true si cambio una fila) ----

    @Test
    public void testAddReturnsTrueWhenTheFavoriteWasInserted() {
        // Arrange
        // (sin favorito todavia)

        // Act
        final boolean result = favoriteDao.add(userId, eventId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAddReturnsFalseWhenItWasAlreadyAFavorite() {
        // Arrange
        insertFavorite(userId, eventId);

        // Act
        final boolean result = favoriteDao.add(userId, eventId);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRemoveReturnsTrueWhenTheFavoriteWasDeleted() {
        // Arrange
        insertFavorite(userId, eventId);

        // Act
        final boolean result = favoriteDao.remove(userId, eventId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRemoveReturnsFalseWhenItWasNeverAFavorite() {
        // Arrange
        // (no hay ningun favorito guardado)

        // Act
        final boolean result = favoriteDao.remove(userId, eventId);

        // Assert
        assertFalse(result);
    }
}
