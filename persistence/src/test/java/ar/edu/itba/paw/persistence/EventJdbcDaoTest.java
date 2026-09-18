package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventJdbcDaoTest {

    private static final String CATEGORY_NAME = "Musica";
    private static final String OTHER_CATEGORY_NAME = "Deportes";
    private static final String TITLE = "Recital en el bajo";
    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventDao eventDao;

    private JdbcTemplate jdbcTemplate;
    private long categoryId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        categoryId = insertCategory(CATEGORY_NAME);
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private static Event someEvent(final long categoryId) {
        return new Event(TITLE, "Una noche a pura guitarra", new Date(), "Av. Corrientes 1234",
                Barrio.PALERMO, null, 1500.0, 50, CREATOR_EMAIL, categoryId);
    }

    // ---- create() + findById() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Event created = eventDao.create(someEvent(categoryId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByIdReturnTheSameEvent() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Optional<Event> found = eventDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(TITLE, found.get().getTitle());
        assertEquals(Barrio.PALERMO, found.get().getNeighborhood());
        assertEquals(1500.0, found.get().getPrice(), 0.001);
        assertEquals(CATEGORY_NAME, found.get().getCategoryName());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        final Optional<Event> found = eventDao.findById(-1);

        assertFalse(found.isPresent());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithNonexistentCategoryThrowsDataAccessException() {
        eventDao.create(someEvent(categoryId + 1000));
    }

    // ---- findByIdForUpdate(): el SELECT ... FOR UPDATE que usa el lock de cupo ----

    @Test
    public void testFindByIdForUpdateReturnsThePresentEvent() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Optional<Event> found = eventDao.findByIdForUpdate(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    public void testFindByIdForUpdateReturnsEmptyWhenTheEventDoesNotExist() {
        final Optional<Event> found = eventDao.findByIdForUpdate(-1);

        assertFalse(found.isPresent());
    }

    // ---- findAll() / findByCategory() ----

    @Test
    public void testFindAllReturnsEventsOrderedByDate() {
        final Date earlierDate = new Date(1_000_000_000_000L); // 2001-09-09
        final Date laterDate = new Date(2_000_000_000_000L);   // 2033-05-18
        final Event later = new Event(TITLE, null, laterDate, "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, categoryId);
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion B",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, categoryId);
        // A proposito insertados en el orden "incorrecto": el que es mas tarde primero.
        eventDao.create(later);
        eventDao.create(earlier);

        final List<Event> all = eventDao.findAll();

        assertEquals(2, all.size());
        assertEquals("Direccion B", all.get(0).getAddress());
        assertEquals("Direccion A", all.get(1).getAddress());
    }

    @Test
    public void testFindByCategoryReturnsOnlyEventsOfThatCategory() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        eventDao.create(someEvent(categoryId));
        eventDao.create(someEvent(otherCategoryId));

        final List<Event> result = eventDao.findByCategory(categoryId);

        assertEquals(1, result.size());
        assertEquals(categoryId, result.get(0).getCategoryId());
    }

    // ---- findByCreatorEmail(): "Mis eventos" ----

    @Test
    public void testFindByCreatorEmailReturnsOnlyEventsOfThatCreator() {
        final String otherEmail = "otro@mail.com";
        final Event own = new Event(TITLE, null, new Date(), "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, categoryId);
        final Event other = new Event(TITLE, null, new Date(), "Direccion B",
                Barrio.PALERMO, null, 0, null, otherEmail, categoryId);
        eventDao.create(own);
        eventDao.create(other);

        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testFindByCreatorEmailOrdersMostRecentEventDateFirst() {
        final Date earlierDate = new Date(1_000_000_000_000L); // 2001-09-09
        final Date laterDate = new Date(2_000_000_000_000L);   // 2033-05-18
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, categoryId);
        final Event later = new Event(TITLE, null, laterDate, "Direccion B",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, categoryId);
        // A proposito insertados en orden "incorrecto".
        eventDao.create(earlier);
        eventDao.create(later);

        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(2, result.size());
        assertEquals("Direccion B", result.get(0).getAddress());
        assertEquals("Direccion A", result.get(1).getAddress());
    }

    @Test
    public void testFindByCreatorEmailReturnsEmptyWhenNoEvents() {
        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertTrue(result.isEmpty());
    }
}
