package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.SavedSearch;
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
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Unidad 5 - Unit Testing: savedSearchDao (el objeto bajo test) solo se toca en el Act. Arrange y Assert son siempre
// SQL directo, nunca otro metodo de savedSearchDao.
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class SavedSearchJpaDaoTest {

    private static final String KEY = "c=;b=;min=;max=;from=;to=";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private DataSource ds;

    @Autowired
    private SavedSearchDao savedSearchDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long otherUserId;
    private long categoryId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = insertUser("ana@mail.com");
        otherUserId = insertUser("beto@mail.com");
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", "MUSICA");
        categoryId = jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, "MUSICA");
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Ana");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertSearch(final long userId, final String key, final Timestamp createdAt) {
        jdbcTemplate.update("INSERT INTO saved_search (user_id, filters_key, created_at, last_checked_at) "
                + "VALUES (?, ?, ?, ?)", userId, key, createdAt, createdAt);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM saved_search WHERE user_id = ? AND filters_key = ?", Long.class, userId, key);
    }

    private long insertSearch(final long userId, final String key) {
        return insertSearch(userId, key, new Timestamp(System.currentTimeMillis()));
    }

    // ---- create() ----

    @Test
    public void testCreateStoresTheFiltersAndReturnsTheSearchWithItsId() {
        // Arrange
        final Date from = new Date(System.currentTimeMillis());

        // Act
        final SavedSearch created = savedSearchDao.create(userId, Collections.singletonList(categoryId),
                Collections.singletonList(Barrio.PALERMO), new BigDecimal("100"), new BigDecimal("500"), from, null);
        em.flush();

        // Assert
        assertTrue(created.getId() > 0);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search",
                "id = " + created.getId() + " AND user_id = " + userId
                        + " AND min_price = 100 AND max_price = 500 AND to_date IS NULL"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search_category",
                "saved_search_id = " + created.getId() + " AND category_id = " + categoryId));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search_barrio",
                "saved_search_id = " + created.getId() + " AND barrio = 'PALERMO'"));
    }

    @Test
    public void testCreateWithoutFiltersStoresNoCategoriesNorBarrios() {
        // Act
        final SavedSearch created = savedSearchDao.create(userId, null, null, null, null, null, null);
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search",
                "id = " + created.getId() + " AND filters_key = '" + KEY + "'"));
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "saved_search_category"));
        assertEquals(0, JdbcTestUtils.countRowsInTable(jdbcTemplate, "saved_search_barrio"));
    }

    // ---- existsByKey() ----

    @Test
    public void testExistsByKeyIsTrueForASearchThatPersonAlreadySaved() {
        // Arrange
        insertSearch(userId, KEY);

        // Act
        final boolean result = savedSearchDao.existsByKey(userId, KEY);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testExistsByKeyIsFalseWhenOnlyAnotherPersonSavedIt() {
        // Arrange
        insertSearch(otherUserId, KEY);

        // Act
        final boolean result = savedSearchDao.existsByKey(userId, KEY);

        // Assert
        assertFalse(result);
    }

    // ---- countByUserId() ----

    @Test
    public void testCountByUserIdCountsOnlyThatPersonsSearches() {
        // Arrange
        insertSearch(userId, "uno");
        insertSearch(userId, "dos");
        insertSearch(otherUserId, "tres");

        // Act
        final long result = savedSearchDao.countByUserId(userId);

        // Assert
        assertEquals(2, result);
    }

    // ---- findByUserId() ----

    @Test
    public void testFindByUserIdPagesTheNewestFirstWithoutRepeatingAndWithItsFilters() {
        // Arrange: 3 listas de la persona con fechas distintas (de a 2 por pagina) y una de otra persona
        final long now = System.currentTimeMillis();
        final long oldest = insertSearch(userId, "vieja", new Timestamp(now - 3000));
        final long middle = insertSearch(userId, "media", new Timestamp(now - 2000));
        final long newest = insertSearch(userId, "nueva", new Timestamp(now - 1000));
        insertSearch(otherUserId, "ajena", new Timestamp(now));
        jdbcTemplate.update("INSERT INTO saved_search_category (saved_search_id, category_id) VALUES (?, ?)",
                newest, categoryId);
        jdbcTemplate.update("INSERT INTO saved_search_barrio (saved_search_id, barrio) VALUES (?, ?)",
                newest, Barrio.PALERMO.name());
        jdbcTemplate.update("INSERT INTO saved_search_barrio (saved_search_id, barrio) VALUES (?, ?)",
                newest, Barrio.RECOLETA.name());

        // Act
        final List<SavedSearch> pageOne = savedSearchDao.findByUserId(userId, 1, 2);
        final List<SavedSearch> pageTwo = savedSearchDao.findByUserId(userId, 2, 2);

        // Assert: la mas nueva primero, la ultima pagina trae solo la que sobra y los filtros vienen cargados
        assertEquals(2, pageOne.size());
        assertEquals(newest, (long) pageOne.get(0).getId());
        assertEquals(middle, (long) pageOne.get(1).getId());
        assertEquals(Collections.singleton(categoryId), pageOne.get(0).getCategoryIds());
        assertEquals(new HashSet<>(Arrays.asList(Barrio.PALERMO, Barrio.RECOLETA)), pageOne.get(0).getBarrios());
        assertEquals(1, pageTwo.size());
        assertEquals(oldest, (long) pageTwo.get(0).getId());
    }

    @Test
    public void testFindByUserIdOfAPersonWithoutListsIsEmpty() {
        // Arrange: solo la otra persona tiene una lista
        insertSearch(otherUserId, "ajena");

        // Act
        final List<SavedSearch> result = savedSearchDao.findByUserId(userId, 1, 10);

        // Assert
        assertTrue(result.isEmpty());
    }

    // ---- deleteByIdAndUserId() ----

    @Test
    public void testDeleteByIdAndUserIdRemovesTheSearchTogetherWithItsFilters() {
        // Arrange
        final long id = insertSearch(userId, KEY);
        jdbcTemplate.update("INSERT INTO saved_search_category (saved_search_id, category_id) VALUES (?, ?)",
                id, categoryId);
        jdbcTemplate.update("INSERT INTO saved_search_barrio (saved_search_id, barrio) VALUES (?, ?)",
                id, Barrio.PALERMO.name());
        final long untouched = insertSearch(userId, "otra");

        // Act
        final boolean result = savedSearchDao.deleteByIdAndUserId(id, userId);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search", "id = " + id));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search_category",
                "saved_search_id = " + id));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search_barrio",
                "saved_search_id = " + id));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search", "id = " + untouched));
    }

    @Test
    public void testDeleteByIdAndUserIdDoesNotRemoveASearchOfSomeoneElse() {
        // Arrange
        final long id = insertSearch(otherUserId, KEY);

        // Act
        final boolean result = savedSearchDao.deleteByIdAndUserId(id, userId);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "saved_search", "id = " + id));
    }

    @Test
    public void testDeleteByIdAndUserIdOfASearchThatDoesNotExistReturnsFalse() {
        // Arrange
        final long id = insertSearch(userId, KEY);

        // Act
        final boolean result = savedSearchDao.deleteByIdAndUserId(id + 1000, userId);

        // Assert
        assertFalse(result);
    }

    // ---- findIdsAfter() ----

    @Test
    public void testFindIdsAfterReturnsTheNextBatchInOrder() {
        // Arrange
        final long first = insertSearch(userId, "uno");
        final long second = insertSearch(userId, "dos");
        final long third = insertSearch(otherUserId, "tres");
        insertSearch(otherUserId, "cuatro");

        // Act
        final List<Long> result = savedSearchDao.findIdsAfter(first, 2);

        // Assert
        assertEquals(Arrays.asList(second, third), result);
    }

    // ---- findByIdWithFilters() ----

    @Test
    public void testFindByIdWithFiltersReturnsTheSearchWithItsOwnerAndFilters() {
        // Arrange
        final long id = insertSearch(userId, KEY);
        jdbcTemplate.update("INSERT INTO saved_search_category (saved_search_id, category_id) VALUES (?, ?)",
                id, categoryId);
        jdbcTemplate.update("INSERT INTO saved_search_barrio (saved_search_id, barrio) VALUES (?, ?)",
                id, Barrio.PALERMO.name());
        jdbcTemplate.update("INSERT INTO saved_search_barrio (saved_search_id, barrio) VALUES (?, ?)",
                id, Barrio.RECOLETA.name());

        // Act
        final Optional<SavedSearch> result = savedSearchDao.findByIdWithFilters(id);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("ana@mail.com", result.get().getUser().getEmail());
        assertEquals(Collections.singleton(categoryId), result.get().getCategoryIds());
        assertEquals(new HashSet<>(Arrays.asList(Barrio.PALERMO, Barrio.RECOLETA)), result.get().getBarrios());
    }

    @Test
    public void testFindByIdWithFiltersOfASearchThatDoesNotExistIsEmpty() {
        // Arrange
        final long other = insertSearch(userId, KEY);

        // Act
        final Optional<SavedSearch> result = savedSearchDao.findByIdWithFilters(other + 1000);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- markChecked() ----

    @Test
    public void testMarkCheckedMovesTheLastCheckDate() {
        // Arrange
        final long id = insertSearch(userId, KEY, new Timestamp(System.currentTimeMillis() - 60_000));
        final Timestamp checkedAt = new Timestamp(System.currentTimeMillis());

        // Act
        final boolean result = savedSearchDao.markChecked(id, checkedAt);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(checkedAt.getTime(), jdbcTemplate.queryForObject(
                "SELECT last_checked_at FROM saved_search WHERE id = ?", Timestamp.class, id).getTime());
    }

    @Test
    public void testMarkCheckedOfASearchThatDoesNotExistReturnsFalse() {
        // Arrange
        final Timestamp before = new Timestamp(System.currentTimeMillis() - 60_000);
        final long other = insertSearch(userId, KEY, before);

        // Act
        final boolean result = savedSearchDao.markChecked(other + 1000, new Date());
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(before.getTime(), jdbcTemplate.queryForObject(
                "SELECT last_checked_at FROM saved_search WHERE id = ?", Timestamp.class, other).getTime());
    }
}
