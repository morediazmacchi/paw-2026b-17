package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.persistence.ImageDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: el ImageDao es un mock de Mockito (sin base de datos); los
// stubs con argumentos especificos hacen de expectativa y se asierta sobre lo que devuelve el service.
@RunWith(MockitoJUnitRunner.class)
public class ImageServiceImplTest {

    private static final long IMAGE_ID = 1;
    // Los primeros bytes (la "firma") son lo que identifica el formato de verdad; el
    // resto da igual para estos tests.
    private static final byte[] PNG_DATA = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    private static final byte[] JPEG_DATA = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    private static final byte[] GIF_DATA = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_DATA = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SVG_WITH_SCRIPT = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] NOT_AN_IMAGE = "hola mundo".getBytes(StandardCharsets.UTF_8);

    @Mock
    private ImageDao imageDao;

    private ImageServiceImpl imageService;

    @Before
    public void setUp() {
        imageService = new ImageServiceImpl(imageDao);
    }

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
        imageService.create(PNG_DATA, null, "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNonImageContentTypeThrowsIllegalArgumentException() {
        // Act
        imageService.create(PNG_DATA, "application/pdf", "foto.pdf");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCreateReturnsTheImageSavedByTheDaoWhenItReceivesTheUploadedBytes() {
        // Arrange
        final Image saved = new Image(PNG_DATA, "image/png", "foto.png", PNG_DATA.length);
        saved.setId(IMAGE_ID);
        when(imageDao.create(argThat(image -> Arrays.equals(PNG_DATA, image.getData())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(PNG_DATA, "image/png", "foto.png");

        // Assert
        assertSame(saved, result);
    }

    @Test
    public void testCreateSavesTheImageWithItsMetadata() {
        // Arrange
        final Image saved = new Image(PNG_DATA, "image/png", "foto.png", PNG_DATA.length);
        saved.setId(IMAGE_ID);
        when(imageDao.create(argThat(image -> "image/png".equals(image.getContentType())
                && "foto.png".equals(image.getFileName())
                && Integer.valueOf(PNG_DATA.length).equals(image.getFileSize())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(PNG_DATA, "image/png", "foto.png");

        // Assert
        assertEquals(IMAGE_ID, result.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithTextDeclaredAsPngThrowsIllegalArgumentException() {
        // Act: el header dice image/png, los bytes dicen otra cosa.
        imageService.create(NOT_AN_IMAGE, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithSvgThrowsIllegalArgumentException() {
        // Act: un SVG puede traer <script>; no es un formato permitido.
        imageService.create(SVG_WITH_SCRIPT, "image/svg+xml", "logo.svg");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithSvgDeclaredAsPngThrowsIllegalArgumentException() {
        // Act: mentir en el Content-Type no alcanza para colar un SVG.
        imageService.create(SVG_WITH_SCRIPT, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithDataShorterThanAnyImageSignatureThrowsIllegalArgumentException() {
        // Act: dos bytes de una firma JPEG, truncada.
        imageService.create(new byte[] {(byte) 0xFF, (byte) 0xD8}, "image/jpeg", "foto.jpg");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCreateStoresAJpegWithItsRealType() {
        // Arrange
        final Image saved = new Image(JPEG_DATA, "image/jpeg", "foto.jpg", JPEG_DATA.length);
        when(imageDao.create(argThat(image -> "image/jpeg".equals(image.getContentType())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(JPEG_DATA, "image/jpeg", "foto.jpg");

        // Assert
        assertSame(saved, result);
    }

    @Test
    public void testCreateStoresAGifWithItsRealType() {
        // Arrange
        final Image saved = new Image(GIF_DATA, "image/gif", "foto.gif", GIF_DATA.length);
        when(imageDao.create(argThat(image -> "image/gif".equals(image.getContentType())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(GIF_DATA, "image/gif", "foto.gif");

        // Assert
        assertSame(saved, result);
    }

    @Test
    public void testCreateStoresAWebpWithItsRealType() {
        // Arrange
        final Image saved = new Image(WEBP_DATA, "image/webp", "foto.webp", WEBP_DATA.length);
        when(imageDao.create(argThat(image -> "image/webp".equals(image.getContentType())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(WEBP_DATA, "image/webp", "foto.webp");

        // Assert
        assertSame(saved, result);
    }

    @Test
    public void testCreateStoresTheRealTypeEvenIfTheClientDeclaredAnotherImageType() {
        // Arrange: el cliente dice GIF pero los bytes son PNG; se guarda lo que los bytes dicen.
        final Image saved = new Image(PNG_DATA, "image/png", "foto.gif", PNG_DATA.length);
        when(imageDao.create(argThat(image -> "image/png".equals(image.getContentType())))).thenReturn(saved);

        // Act
        final Image result = imageService.create(PNG_DATA, "image/gif", "foto.gif");

        // Assert
        assertEquals("image/png", result.getContentType());
    }

    // ---- findOwnerEventId() ----

    @Test
    public void testFindOwnerEventIdReturnsTheEventTheImageBelongsTo() {
        // Arrange
        when(imageDao.findOwnerEventId(IMAGE_ID)).thenReturn(Optional.of(7L));

        // Act
        final Optional<Long> result = imageService.findOwnerEventId(IMAGE_ID);

        // Assert
        assertEquals(Optional.of(7L), result);
    }

    @Test
    public void testFindOwnerEventIdReturnsEmptyWhenTheImageBelongsToNoEvent() {
        // Arrange
        when(imageDao.findOwnerEventId(IMAGE_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Long> result = imageService.findOwnerEventId(IMAGE_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsThePresentImage() {
        // Arrange
        final Image image = new Image(PNG_DATA, "image/png", "foto.png", PNG_DATA.length);
        image.setId(IMAGE_ID);
        when(imageDao.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        // Act
        final Optional<Image> result = imageService.findById(IMAGE_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(IMAGE_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        // Arrange
        when(imageDao.findById(IMAGE_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Image> result = imageService.findById(IMAGE_ID);

        // Assert
        assertFalse(result.isPresent());
    }
}
