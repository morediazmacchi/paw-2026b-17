package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.persistence.ImageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

@Service
public class ImageServiceImpl implements ImageService {

    // 5 MB. El web.xml permite hasta 6 MB a proposito: dejamos margen para que
    // salte esta validacion (con mensaje claro) antes que el muro del servidor.
    private static final int MAX_SIZE_BYTES = 5 * 1024 * 1024;

    private final ImageDao imageDao;

    @Autowired
    public ImageServiceImpl(final ImageDao imageDao) {
        this.imageDao = imageDao;
    }

    @Override
    @Transactional
    public Image create(final byte[] data, final String contentType, final String fileName) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("El archivo esta vacio.");
        }
        if (data.length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("La imagen no puede pesar mas de 5 MB.");
        }
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo tiene que ser una imagen.");
        }

        final Image image = new Image(data, contentType, fileName, data.length, checksum(data));
        return imageDao.create(image);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Image> findById(final long id) {
        return imageDao.findById(id);
    }

    /**
     * SHA-256 en hexadecimal: 64 caracteres, que es justo el largo de la columna
     * checksum VARCHAR(64). Sirve para detectar imagenes repetidas o corrompidas.
     */
    private String checksum(final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(data);
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            // SHA-256 esta garantizado en toda JVM, esto no deberia pasar nunca.
            return null;
        }
    }
}
