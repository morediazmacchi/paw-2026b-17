package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Sirve las imagenes guardadas en la base.
 * Una imagen hereda la visibilidad de su evento (ver EventSecurity.canViewImage).
 */
@Controller
public class ImageController {

    // Los unicos formatos que ImageServiceImpl deja guardar.
    private static final List<MediaType> SERVABLE_TYPES = Arrays.asList(
            MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.IMAGE_GIF, MediaType.parseMediaType("image/webp"));

    // Una imagen de evento nunca cambia de contenido (editar la galeria crea imagenes nuevas con otro id):
    // el navegador la guarda una hora. "private": hereda la visibilidad de su evento, asi que ningun cache
    // compartido (un proxy) puede entregarsela a otra persona.
    private static final CacheControl EVENT_IMAGE_CACHE = CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate();
    // La portada de un perfil vive en una URL fija (/usuarios/{id}/portada) pero la persona puede cambiarla:
    // el navegador la guarda, pero pregunta en cada uso si sigue siendo la misma (ETag) y, si lo es, el
    // servidor contesta 304 sin volver a mandar los bytes.
    private static final CacheControl COVER_CACHE = CacheControl.noCache().cachePrivate();

    private final ImageService imageService;
    private final UserProfileService userProfileService;

    @Autowired
    public ImageController(final ImageService imageService, final UserProfileService userProfileService) {
        this.imageService = imageService;
        this.userProfileService = userProfileService;
    }

    // Una imagen hereda la visibilidad de su evento: la regla vive en EventSecurity.canViewImage.
    // Una imagen inexistente, o que nunca se asocio a un evento, tampoco la ve nadie.
    @PreAuthorize("@eventSecurity.canViewImage(#id, authentication)")
    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@PathVariable final long id) {
        final Optional<Image> maybeImage = imageService.findById(id);
        if (!maybeImage.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        return imageResponse(maybeImage.get(), EVENT_IMAGE_CACHE);
    }

    // La portada del perfil publico la ve cualquiera (como el perfil). Sin portada elegida, o con una cuenta que no se
    // puede mostrar, 404: la pantalla usa la portada por defecto y ni pide este link.
    @RequestMapping(value = "/usuarios/{id:[0-9]+}/portada", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getCover(@PathVariable final long id) {
        return userProfileService.findCoverImage(id)
                .map(image -> imageResponse(image, COVER_CACHE))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> imageResponse(final Image image, final CacheControl cacheControl) {
        final MediaType type = servableType(image.getContentType());
        final ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(type)
                // Sin esto Spring Security manda "no-store" y la pagina bajaria cada foto en cada carga.
                // El ETag es el id: una imagen no cambia su contenido sin cambiar de id.
                .cacheControl(cacheControl)
                .eTag("\"" + image.getId() + "\"")
                // El navegador no tiene que "adivinar" otro tipo mirando los bytes.
                .header("X-Content-Type-Options", "nosniff")
                // Aunque algo raro llegara a abrirse, sin scripts ni recursos externos.
                .header("Content-Security-Policy", "default-src 'none'; sandbox");
        if (!SERVABLE_TYPES.contains(type)) {
            response.header("Content-Disposition", "attachment");
        }
        return response.body(image.getData());
    }

    // Una imagen que no puede ver da 404 sin cuerpo: para un <img> no tiene sentido renderizar la
    // pagina de error (la de ErrorAdvice), y asi no se revela que la imagen existe.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Void> accessDenied() {
        return ResponseEntity.notFound().build();
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
