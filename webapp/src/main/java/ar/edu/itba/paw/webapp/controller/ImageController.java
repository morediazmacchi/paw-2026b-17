package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Sirve las imagenes guardadas en la base.
 * Una imagen hereda la visibilidad de su evento:
 */
@Controller
public class ImageController {

    // Los unicos formatos que ImageServiceImpl deja guardar.
    private static final List<MediaType> SERVABLE_TYPES = Arrays.asList(
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.parseMediaType("image/webp"));

    private final ImageService imageService;
    private final EventService eventService;
    private final EventAccessService accessChecker;

    @Autowired
    public ImageController(final ImageService imageService, final EventService eventService,
                           final EventAccessService accessChecker) {
        this.imageService = imageService;
        this.eventService = eventService;
        this.accessChecker = accessChecker;
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@PathVariable final long id,
                                            @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (!canView(id, currentUser)) {
            return ResponseEntity.notFound().build();
        }
        final Optional<Image> maybeImage = imageService.findById(id);
        if (!maybeImage.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        final Image image = maybeImage.get();
        final MediaType type = servableType(image.getContentType());
        final ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(type)
                // El navegador no tiene que "adivinar" otro tipo mirando los bytes.
                .header("X-Content-Type-Options", "nosniff")
                // Aunque algo raro llegara a abrirse, sin scripts ni recursos externos.
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
        if (!SERVABLE_TYPES.contains(type)) {
            response.header("Content-Disposition", "attachment");
        }
        return response.body(image.getData());
    }

    // Una imagen que no cuelga de ningun evento (nunca se asocio) no tiene quien
    // la vea: se trata como inexistente
    private boolean canView(final long imageId, final User user) {
        return imageService.findOwnerEventId(imageId)
                .flatMap(eventService::findById)
                .map(event -> accessChecker.canView(event, user))
                .orElse(false);
    }

    private MediaType servableType(final String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            final MediaType parsed = MediaType.parseMediaType(contentType);
            return SERVABLE_TYPES.stream().filter(parsed::equalsTypeAndSubtype).findFirst()
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);
        } catch (final RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
