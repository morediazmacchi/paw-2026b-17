package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Image;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    private ImageDao imageDao;

    private static Image someImage() {
        return new Image(DATA, CONTENT_TYPE, FILE_NAME, DATA.length, CHECKSUM);
    }

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Image created = imageDao.create(someImage());

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByIdReturnTheSameBytesAndMetadata() {
        final Image created = imageDao.create(someImage());

        final Optional<Image> found = imageDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertTrue(Arrays.equals(DATA, found.get().getData()));
        assertEquals(CONTENT_TYPE, found.get().getContentType());
        assertEquals(FILE_NAME, found.get().getFileName());
        assertEquals(Integer.valueOf(DATA.length), found.get().getFileSize());
        assertEquals(CHECKSUM, found.get().getChecksum());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        final Optional<Image> found = imageDao.findById(-1);

        assertFalse(found.isPresent());
    }

    @Test
    public void testUploadedAtIsSetAutomaticallyByTheDatabase() {
        final Image created = imageDao.create(someImage());

        final Optional<Image> found = imageDao.findById(created.getId());

        assertNotNull(found.get().getUploadedAt());
    }
}
