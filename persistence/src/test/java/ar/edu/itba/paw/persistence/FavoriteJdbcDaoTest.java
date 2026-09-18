package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
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
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class FavoriteJdbcDaoTest {

    @Autowired
    private DataSource ds;

    @Autowired
    private FavoriteDao favoriteDao;

    @Autowired
    private UserDao userDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long eventId;
    private long otherEventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = userDao.create(new User("fan@mail.com", "Ana")).getId();
        final long categoryId = insertCategory("Musica");
        eventId = insertEvent(categoryId, "Recital");
        otherEventId = insertEvent(categoryId, "Feria");
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertEvent(final long categoryId, final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, category_id) VALUES (?, ?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", "creador@mail.com", categoryId);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    // ---- add() + isFavorite() ----

    @Test
    public void testAddMakesIsFavoriteReturnTrue() {
        favoriteDao.add(userId, eventId);

        assertTrue(favoriteDao.isFavorite(userId, eventId));
    }

    @Test
    public void testIsFavoriteReturnsFalseWhenNeverAdded() {
        assertFalse(favoriteDao.isFavorite(userId, eventId));
    }

    @Test
    public void testAddTwiceIsIdempotent() {
        // No hay ON CONFLICT (no es portable a HSQLDB): add() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        favoriteDao.add(userId, eventId);
        favoriteDao.add(userId, eventId);

        assertEquals(1, favoriteDao.findEventIdsByUser(userId).size());
    }

    // ---- remove() ----

    @Test
    public void testRemoveMakesIsFavoriteReturnFalse() {
        favoriteDao.add(userId, eventId);

        favoriteDao.remove(userId, eventId);

        assertFalse(favoriteDao.isFavorite(userId, eventId));
    }

    @Test
    public void testRemoveWhenNeverAddedDoesNothing() {
        favoriteDao.remove(userId, eventId);

        assertFalse(favoriteDao.isFavorite(userId, eventId));
    }

    // ---- findEventIdsByUser() ----

    @Test
    public void testFindEventIdsByUserReturnsOnlyThatUsersFavorites() {
        favoriteDao.add(userId, eventId);
        favoriteDao.add(userId, otherEventId);

        final List<Long> found = favoriteDao.findEventIdsByUser(userId);

        assertEquals(2, found.size());
        assertTrue(found.contains(eventId));
        assertTrue(found.contains(otherEventId));
    }

    @Test
    public void testFindEventIdsByUserReturnsEmptyWhenNoneFavorited() {
        assertTrue(favoriteDao.findEventIdsByUser(userId).isEmpty());
    }
}
