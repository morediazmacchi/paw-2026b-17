package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.persistence.ImageDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ImageServiceImplTest {

    private static final long IMAGE_ID = 1;
    private static final byte[] SOME_DATA = "hola mundo".getBytes(StandardCharsets.UTF_8);
    private static final String SOME_DATA_SHA256 =
            "0b894166d3336435c800bea36ff21b29eaa801a52f584c006c49289a0dcf6e2f";

    @Mock
    private ImageDao mockImageDao;

    @InjectMocks
    private ImageServiceImpl imageService;

    // ---- create(): validaciones ----

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNullDataThrowsIllegalArgumentException() {
        // Act
        imageService.create(null, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithEmptyDataThrowsIllegalArgumentException() {
        // Act
        imageService.create(new byte[0], "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithDataTooLargeThrowsIllegalArgumentException() {
        // Arrange
        // MAX_SIZE_BYTES en ImageServiceImpl es 5 * 1024 * 1024. Un byte de mas alcanza.
        final byte[] tooBig = new byte[5 * 1024 * 1024 + 1];

        // Act
        imageService.create(tooBig, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNullContentTypeThrowsIllegalArgumentException() {
        // Act
        imageService.create(SOME_DATA, null, "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNonImageContentTypeThrowsIllegalArgumentException() {
        // Act
        imageService.create(SOME_DATA, "application/pdf", "foto.pdf");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCreateReturnsTheSavedImageWithMetadataAndChecksum() {
        // Arrange
        when(mockImageDao.create(any(Image.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        final Image result = imageService.create(SOME_DATA, "image/png", "foto.png");

        // Assert
        assertEquals("image/png", result.getContentType());
        assertEquals("foto.png", result.getFileName());
        assertEquals(Integer.valueOf(SOME_DATA.length), result.getFileSize());
        assertEquals(SOME_DATA_SHA256, result.getChecksum());
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsThePresentImage() {
        // Arrange
        final Image image = new Image(SOME_DATA, "image/png", "foto.png", SOME_DATA.length, SOME_DATA_SHA256);
        image.setId(IMAGE_ID);
        when(mockImageDao.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        // Act
        final Optional<Image> result = imageService.findById(IMAGE_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(IMAGE_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        // Arrange
        when(mockImageDao.findById(IMAGE_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Image> result = imageService.findById(IMAGE_ID);

        // Assert
        assertFalse(result.isPresent());
    }
}
