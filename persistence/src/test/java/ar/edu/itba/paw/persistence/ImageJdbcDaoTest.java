package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Image;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class ImageJdbcDaoTest {

    private static final byte[] DATA = "esto no es una imagen real, son bytes cualquiera"
            .getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "image/png";
    private static final String FILE_NAME = "foto.png";
    private static final String CHECKSUM = "0b894166d3336435c800bea36ff21b29eaa801a52f584c006c49289a0dcf6e2f";

    @Autowired
    private DataSource ds;

    @Autowired
    private ImageDao imageDao;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
    }

    private static Image someImage() {
        return new Image(DATA, CONTENT_TYPE, FILE_NAME, DATA.length, CHECKSUM);
    }

    private long insertImage() {
        jdbcTemplate.update(
                "INSERT INTO image (data, content_type, file_name, file_size, checksum) VALUES (?, ?, ?, ?, ?)",
                DATA, CONTENT_TYPE, FILE_NAME, DATA.length, CHECKSUM);
        return jdbcTemplate.queryForObject("SELECT id FROM image WHERE checksum = ?", Long.class, CHECKSUM);
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsTheBytesAndMetadataAndAssignsAnId() {
        // Arrange
        final Image toCreate = someImage();

        // Act
        final Image created = imageDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT content_type, file_name, file_size, checksum FROM image WHERE id = ?", created.getId());
        assertEquals(CONTENT_TYPE, row.get("content_type"));
        assertEquals(FILE_NAME, row.get("file_name"));
        assertEquals(DATA.length, row.get("file_size"));
        assertEquals(CHECKSUM, row.get("checksum"));
        final byte[] storedData = jdbcTemplate.queryForObject(
                "SELECT data FROM image WHERE id = ?", byte[].class, created.getId());
        assertTrue(Arrays.equals(DATA, storedData));
    }

    @Test
    public void testCreateSetsUploadedAtAutomatically() {
        // Arrange
        final Image toCreate = someImage();

        // Act
        final Image created = imageDao.create(toCreate);

        // Assert
        final java.sql.Timestamp uploadedAt = jdbcTemplate.queryForObject(
                "SELECT uploaded_at FROM image WHERE id = ?", java.sql.Timestamp.class, created.getId());
        assertNotNull(uploadedAt);
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsTheStoredImage() {
        // Arrange
        final long id = insertImage();

        // Act
        final Optional<Image> found = imageDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertTrue(Arrays.equals(DATA, found.get().getData()));
        assertEquals(CONTENT_TYPE, found.get().getContentType());
        assertEquals(FILE_NAME, found.get().getFileName());
        assertEquals(Integer.valueOf(DATA.length), found.get().getFileSize());
        assertEquals(CHECKSUM, found.get().getChecksum());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        // Arrange: no se inserta ninguna imagen con ese id.

        // Act
        final Optional<Image> found = imageDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }
}
