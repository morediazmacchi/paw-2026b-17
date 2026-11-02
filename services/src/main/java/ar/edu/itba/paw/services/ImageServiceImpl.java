package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.persistence.ImageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ImageServiceImpl implements ImageService {

    // 5 MB. El web.xml permite hasta 15 MB por archivo a proposito: dejamos margen para que
    // salte esta validacion (con mensaje claro) antes que el muro del servidor.
    private static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    // Lista blanca de formatos: se reconocen por los primeros bytes del archivo,
    // no por el Content-Type que declara el cliente (que cualquiera puede
    // falsear). SVG queda afuera a proposito: es XML y puede traer <script>,
    // que se ejecuta cuando alguien abre /image/{id} (XSS almacenado).
    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String GIF = "image/gif";
    private static final String WEBP = "image/webp";

    private final ImageDao imageDao;

    @Autowired
    public ImageServiceImpl(final ImageDao imageDao) {
        this.imageDao = imageDao;
    }

    @Override
    @Transactional
    public Image create(final byte[] data, final String contentType, final String fileName) {
        if (data == null || data.length == 0) {
            throw new InvalidImageException("event.publish.imageEmpty");
        }
        if (data.length > MAX_SIZE_BYTES) {
            throw new InvalidImageException("event.publish.imageTooLarge");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidImageException("event.publish.imageNotAnImage");
        }
        // El tipo que se guarda (y que despues se manda como header al servirla)
        // es el que salio de mirar los bytes, nunca el que declaro el cliente.
        final String realType = detectType(data);
        if (realType == null) {
            throw new InvalidImageException("event.publish.imageNotAnImage");
        }

        return imageDao.create(data, realType, fileName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Image> findById(final long id) {
        return imageDao.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EventAccessSummary> findOwnerEventAccess(final long imageId) {
        return imageDao.findOwnerEventAccess(imageId);
    }

    /**
     * El formato real segun los primeros bytes, o null si no es JPEG/PNG/GIF/WebP.
     * Visibilidad de paquete a proposito: se testea directo, por valor de retorno.
     */
    static String detectType(final byte[] data) {
        if (startsWith(data, 0, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(data, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        // GIF87a o GIF89a
        if (startsWith(data, 0, 'G', 'I', 'F', '8') && (startsWith(data, 4, '7', 'a') || startsWith(data, 4, '9', 'a'))) {
            return GIF;
        }
        // WebP: "RIFF" + 4 bytes de largo + "WEBP"
        if (startsWith(data, 0, 'R', 'I', 'F', 'F') && startsWith(data, 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }
        return null;
    }

    private static boolean startsWith(final byte[] data, final int offset, final int... expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
