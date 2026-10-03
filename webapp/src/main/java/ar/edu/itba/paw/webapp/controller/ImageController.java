package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Sirve las imagenes guardadas en la base. Es la contraparte de la subida:
 * el <img src="/image/7"> del navegador termina aca.
 *
 * Una imagen hereda la visibilidad de su evento: las de un evento privado solo
 * las ve quien puede ver el evento (ver EventAccessService). Se contesta 404 y
 * no 403 para no confirmar que ese id existe.
 */
@Controller
public class ImageController {

    // Los unicos formatos que ImageServiceImpl deja guardar. Una fila vieja con
    // otro content_type (ej. un SVG subido antes de esa validacion) no se sirve
    // como lo que dice ser: se baja como archivo opaco, nunca se interpreta.
    private static final List<MediaType> SERVABLE_TYPES = Arrays.asList(
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.parseMediaType("image/webp"));

    private final ImageService imageService;
    private final EventService eventService;
    private final UserService userService;
    private final EventAccessService accessChecker;

    @Autowired
    public ImageController(final ImageService imageService, final EventService eventService,
                           final UserService userService, final EventAccessService accessChecker) {
        this.imageService = imageService;
        this.eventService = eventService;
        this.userService = userService;
        this.accessChecker = accessChecker;
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@PathVariable final long id, final Principal principal) {
        if (!canView(id, principal)) {
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
    // la vea: se trata como inexistente.
    private boolean canView(final long imageId, final Principal principal) {
        final Optional<Event> event = imageService.findOwnerEventId(imageId).flatMap(eventService::findById);
        if (!event.isPresent()) {
            return false;
        }
        if (!event.get().isPrivate()) {
            return true;
        }
        final User user = principal == null ? null : userService.findByEmail(principal.getName()).orElse(null);
        return accessChecker.canView(event.get(), user);
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
