package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.exception.InvalidImageException;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    // El service avisa POR QUE rechazo la imagen con un codigo (no con una clave de mensaje).

    @Test
    public void testCreateWithEmptyDataFailsWithReasonEmpty() {
        final InvalidImageException thrown = assertThrows(InvalidImageException.class,
                () -> imageService.create(new byte[0], "image/png", "foto.png"));

        assertEquals(InvalidImageException.Reason.EMPTY, thrown.getReason());
    }

    @Test
    public void testCreateWithTooBigDataFailsWithReasonTooLarge() {
        final byte[] huge = new byte[5 * 1024 * 1024 + 1];

        final InvalidImageException thrown = assertThrows(InvalidImageException.class,
                () -> imageService.create(huge, "image/png", "foto.png"));

        assertEquals(InvalidImageException.Reason.TOO_LARGE, thrown.getReason());
    }

    @Test
    public void testCreateWithNonImageBytesFailsWithReasonNotAnImage() {
        final InvalidImageException thrown = assertThrows(InvalidImageException.class,
                () -> imageService.create(NOT_AN_IMAGE, "image/png", "foto.png"));

        assertEquals(InvalidImageException.Reason.NOT_AN_IMAGE, thrown.getReason());
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithNullDataThrowsInvalidImageException() {
        // Act
        imageService.create(null, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithEmptyDataThrowsInvalidImageException() {
        // Act
        imageService.create(new byte[0], "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithDataTooLargeThrowsInvalidImageException() {
        // Arrange
        // MAX_SIZE_BYTES en ImageServiceImpl es 5 * 1024 * 1024. Un byte de mas alcanza.
        final byte[] tooBig = new byte[5 * 1024 * 1024 + 1];

        // Act
        imageService.create(tooBig, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithNullContentTypeThrowsInvalidImageException() {
        // Act
        imageService.create(PNG_DATA, null, "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithNonImageContentTypeThrowsInvalidImageException() {
        // Act
        imageService.create(PNG_DATA, "application/pdf", "foto.pdf");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCreateReturnsTheImageSavedByTheDaoWhenItReceivesTheUploadedBytes() {
        // Arrange
        final Image saved = new Image(PNG_DATA, "image/png", "foto.png", PNG_DATA.length);
        saved.setId(IMAGE_ID);
        when(imageDao.create(any(Image.class))).thenReturn(saved);

        // Act
        final Image result = imageService.create(PNG_DATA, "image/png", "foto.png");

        // Assert
        assertSame(saved, result);
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithTextDeclaredAsPngThrowsInvalidImageException() {
        // Act: el header dice image/png, los bytes dicen otra cosa.
        imageService.create(NOT_AN_IMAGE, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithSvgThrowsInvalidImageException() {
        // Act: un SVG puede traer <script>; no es un formato permitido.
        imageService.create(SVG_WITH_SCRIPT, "image/svg+xml", "logo.svg");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithSvgDeclaredAsPngThrowsInvalidImageException() {
        // Act: mentir en el Content-Type no alcanza para colar un SVG.
        imageService.create(SVG_WITH_SCRIPT, "image/png", "foto.png");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = InvalidImageException.class)
    public void testCreateWithDataShorterThanAnyImageSignatureThrowsInvalidImageException() {
        // Act: dos bytes de una firma JPEG, truncada.
        imageService.create(new byte[] {(byte) 0xFF, (byte) 0xD8}, "image/jpeg", "foto.jpg");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- detectType(): el formato sale de los bytes, no del Content-Type ----

    @Test
    public void testDetectTypeRecognizesJpegBySignature() {
        // Act
        final String type = ImageServiceImpl.detectType(JPEG_DATA);

        // Assert
        assertEquals("image/jpeg", type);
    }

    @Test
    public void testDetectTypeRecognizesPngBySignature() {
        // Act
        final String type = ImageServiceImpl.detectType(PNG_DATA);

        // Assert
        assertEquals("image/png", type);
    }

    @Test
    public void testDetectTypeRecognizesGif89aBySignature() {
        // Act
        final String type = ImageServiceImpl.detectType(GIF_DATA);

        // Assert
        assertEquals("image/gif", type);
    }

    @Test
    public void testDetectTypeRecognizesGif87aBySignature() {
        // Act
        final String type = ImageServiceImpl.detectType("GIF87a....".getBytes(StandardCharsets.US_ASCII));

        // Assert
        assertEquals("image/gif", type);
    }

    @Test
    public void testDetectTypeRecognizesWebpBySignature() {
        // Act
        final String type = ImageServiceImpl.detectType(WEBP_DATA);

        // Assert
        assertEquals("image/webp", type);
    }

    @Test
    public void testDetectTypeReturnsNullForSvgEvenWhenItIsWellFormed() {
        // Act: un SVG puede traer <script>, por eso no es un formato permitido.
        final String type = ImageServiceImpl.detectType(SVG_WITH_SCRIPT);

        // Assert
        assertNull(type);
    }

    @Test
    public void testDetectTypeReturnsNullForTextThatIsNotAnImage() {
        // Act
        final String type = ImageServiceImpl.detectType(NOT_AN_IMAGE);

        // Assert
        assertNull(type);
    }

    @Test
    public void testDetectTypeReturnsNullWhenTheDataIsShorterThanTheSignature() {
        // Act: dos bytes de una firma JPEG, truncada.
        final String type = ImageServiceImpl.detectType(new byte[] {(byte) 0xFF, (byte) 0xD8});

        // Assert
        assertNull(type);
    }

    // ---- findOwnerEventAccess() ----

    @Test
    public void testFindOwnerEventAccessReturnsTheAccessDataOfTheEventTheImageBelongsTo() {
        // Arrange
        final EventAccessSummary access = new EventAccessSummary(7L, 1L, true, true);
        when(imageDao.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));

        // Act
        final Optional<EventAccessSummary> result = imageService.findOwnerEventAccess(IMAGE_ID);

        // Assert
        assertEquals(Optional.of(access), result);
    }

    @Test
    public void testFindOwnerEventAccessReturnsEmptyWhenTheImageBelongsToNoEvent() {
        // Arrange
        when(imageDao.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<EventAccessSummary> result = imageService.findOwnerEventAccess(IMAGE_ID);

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
        assertEquals(IMAGE_ID, result.get().getId().longValue());
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
