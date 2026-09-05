package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.services.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Optional;

/**
 * Sirve las imagenes guardadas en la base. Es la contraparte de la subida:
 * el <img src="/image/7"> del navegador termina aca.
 */
@Controller
public class ImageController {

    private final ImageService imageService;

    @Autowired
    public ImageController(final ImageService imageService) {
        this.imageService = imageService;
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@PathVariable final long id) {
        final Optional<Image> maybeImage = imageService.findById(id);
        if (!maybeImage.isPresent()) {
            // image_id es una referencia suelta (no hay FK), asi que puede apuntar
            // a una imagen que ya no existe. Devolvemos 404 y la pagina sigue viva.
            return ResponseEntity.notFound().build();
        }

        final Image image = maybeImage.get();
        return ResponseEntity.ok()
                .contentType(parseContentType(image.getContentType()))
                .body(image.getData());
    }

    private MediaType parseContentType(final String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (final RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
