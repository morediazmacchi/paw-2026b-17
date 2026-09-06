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
        imageService.create(null, "image/png", "foto.png");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithEmptyDataThrowsIllegalArgumentException() {
        imageService.create(new byte[0], "image/png", "foto.png");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithDataTooLargeThrowsIllegalArgumentException() {
        // MAX_SIZE_BYTES en ImageServiceImpl es 5 * 1024 * 1024. Un byte de mas alcanza.
        final byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        imageService.create(tooBig, "image/png", "foto.png");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNullContentTypeThrowsIllegalArgumentException() {
        imageService.create(SOME_DATA, null, "foto.png");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNonImageContentTypeThrowsIllegalArgumentException() {
        imageService.create(SOME_DATA, "application/pdf", "foto.pdf");
    }


    @Test
    public void testCreateReturnsTheSavedImageWithMetadataAndChecksum() {
        when(mockImageDao.create(any(Image.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Image result = imageService.create(SOME_DATA, "image/png", "foto.png");

        assertEquals("image/png", result.getContentType());
        assertEquals("foto.png", result.getFileName());
        assertEquals(Integer.valueOf(SOME_DATA.length), result.getFileSize());
        assertEquals(SOME_DATA_SHA256, result.getChecksum());
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsThePresentImage() {
        final Image image = new Image(SOME_DATA, "image/png", "foto.png", SOME_DATA.length, SOME_DATA_SHA256);
        image.setId(IMAGE_ID);
        when(mockImageDao.findById(IMAGE_ID)).thenReturn(Optional.of(image));

        final Optional<Image> result = imageService.findById(IMAGE_ID);

        assertTrue(result.isPresent());
        assertEquals(IMAGE_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheImageDoesNotExist() {
        when(mockImageDao.findById(IMAGE_ID)).thenReturn(Optional.empty());

        final Optional<Image> result = imageService.findById(IMAGE_ID);

        assertFalse(result.isPresent());
    }
}
