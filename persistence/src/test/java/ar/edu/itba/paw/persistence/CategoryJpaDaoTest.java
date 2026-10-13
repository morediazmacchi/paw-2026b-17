package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Category;
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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class CategoryJpaDaoTest {

    @Autowired
    private DataSource ds;

    @Autowired
    private CategoryDao categoryDao;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    @Test
    public void testFindAllReturnsTheCategoriesOrderedByName() {
        // Arrange
        // A proposito insertadas al reves del orden alfabetico esperado.
        insertCategory("Musica");
        insertCategory("Deportes");

        // Act
        final List<Category> result = categoryDao.findAll();

        // Assert
        assertEquals(2, result.size());
        assertEquals("Deportes", result.get(0).getName());
        assertEquals("Musica", result.get(1).getName());
    }

    @Test
    public void testFindAllReturnsAnEmptyListWhenThereAreNoCategories() {
        // Arrange: no se inserta ninguna categoria.

        // Act
        final List<Category> result = categoryDao.findAll();

        // Assert
        assertTrue(result.isEmpty());
    }

}
