package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventAccessSummary;
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
public class ImageJpaDaoTest {

    private static final byte[] DATA = "esto no es una imagen real, son bytes cualquiera"
            .getBytes(StandardCharsets.UTF_8);
    private static final String CONTENT_TYPE = "image/png";
    private static final String FILE_NAME = "foto.png";

    @Autowired
    private DataSource ds;

    @Autowired
    private ImageDao imageDao;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
    }

    private long insertImage() {
        jdbcTemplate.update(
                "INSERT INTO image (data, content_type, file_name, file_size) VALUES (?, ?, ?, ?)",
                DATA, CONTENT_TYPE, FILE_NAME, DATA.length);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM image", Long.class);
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsTheBytesAndMetadataAndAssignsAnId() {
        // Act
        final Image created = imageDao.create(DATA, CONTENT_TYPE, FILE_NAME);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT content_type, file_name, file_size FROM image WHERE id = ?", created.getId());
        assertEquals(CONTENT_TYPE, row.get("content_type"));
        assertEquals(FILE_NAME, row.get("file_name"));
        assertEquals(DATA.length, row.get("file_size"));
        final byte[] storedData = jdbcTemplate.queryForObject(
                "SELECT data FROM image WHERE id = ?", byte[].class, created.getId());
        assertTrue(Arrays.equals(DATA, storedData));
    }

    @Test
    public void testCreateSetsUploadedAtAutomatically() {
        // Act
        final Image created = imageDao.create(DATA, CONTENT_TYPE, FILE_NAME);

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
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        // Arrange: no se inserta ninguna imagen con ese id.

        // Act
        final Optional<Image> found = imageDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findOwnerEventAccess() ----

    private long insertEvent(final String visibility, final String approvalStatus) {
        jdbcTemplate.update("INSERT INTO event (title, event_date, address, creator_email, visibility, approval_status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "Un evento", new java.sql.Timestamp(System.currentTimeMillis() + 86_400_000L),
                "Av. Corrientes 1234", "creador@test.com", visibility, approvalStatus);
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    @Test
    public void testFindOwnerEventAccessReturnsTheVisibilityDataOfTheEventTheImageIsAttachedTo() {
        // Arrange
        final long imageId = insertImage();
        final long eventId = insertEvent("PRIVATE", "APPROVED");
        jdbcTemplate.update("INSERT INTO event_image (event_id, image_id) VALUES (?, ?)", eventId, imageId);

        // Act
        final Optional<EventAccessSummary> access = imageDao.findOwnerEventAccess(imageId);

        // Assert
        assertTrue(access.isPresent());
        assertEquals(Long.valueOf(eventId), access.get().getId());
        assertTrue(access.get().isPrivate());
        assertTrue(access.get().isApproved());
    }

    @Test
    public void testFindOwnerEventAccessReturnsTheCreatorIdOfTheEvent() {
        // Arrange
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", "creador@test.com", "Creador");
        final long creatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, "creador@test.com");
        final long imageId = insertImage();
        final long eventId = insertEvent("PRIVATE", "APPROVED");
        jdbcTemplate.update("UPDATE event SET creator_id = ? WHERE id = ?", creatorId, eventId);
        jdbcTemplate.update("INSERT INTO event_image (event_id, image_id) VALUES (?, ?)", eventId, imageId);

        // Act
        final Optional<EventAccessSummary> access = imageDao.findOwnerEventAccess(imageId);

        // Assert
        assertTrue(access.isPresent());
        assertEquals(Long.valueOf(creatorId), access.get().getCreatorId());
    }

    @Test
    public void testFindOwnerEventAccessOfAPublicPendingEventIsNotPrivateNorApproved() {
        // Arrange
        final long imageId = insertImage();
        final long eventId = insertEvent("PUBLIC", "PENDING");
        jdbcTemplate.update("INSERT INTO event_image (event_id, image_id) VALUES (?, ?)", eventId, imageId);

        // Act
        final Optional<EventAccessSummary> access = imageDao.findOwnerEventAccess(imageId);

        // Assert
        assertTrue(access.isPresent());
        assertFalse(access.get().isPrivate());
        assertFalse(access.get().isApproved());
    }

    @Test
    public void testFindOwnerEventAccessReturnsEmptyWhenTheImageIsNotAttachedToAnyEvent() {
        // Arrange: una imagen subida que nunca se asocio a un evento.
        final long imageId = insertImage();

        // Act
        final Optional<EventAccessSummary> access = imageDao.findOwnerEventAccess(imageId);

        // Assert
        assertFalse(access.isPresent());
    }
}
