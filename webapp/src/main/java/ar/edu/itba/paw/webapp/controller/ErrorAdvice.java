package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.EventFinishedException;
import ar.edu.itba.paw.model.exception.EventRejectedException;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maneja los errores de TODOS los controllers desde un solo lugar. (El usuario de la sesion en el Model de cada
 * pedido no es de esta clase: ver CurrentUserAdvice.)
 */
@ControllerAdvice
public class ErrorAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorAdvice.class);

    // Las dos pantallas, ademas de publicar, que suben archivos (ver imageTooLarge()).
    private static final Pattern EDIT_EVENT_PATH = Pattern.compile("/eventos/(\\d+)/editar");
    private static final String COVER_PATH = "/perfil/portada";

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView eventNotFound(final EventNotFoundException e) {
        return new ModelAndView("errors/404");
    }

    // Perfil de una cuenta que no existe o que no se puede mostrar: el mismo 404 que un evento inexistente.
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView userNotFound(final UserNotFoundException e) {
        return new ModelAndView("errors/404");
    }

    // @PreAuthorize (ver EventSecurity): con sesion, mismo 404 que un evento inexistente, para no
    // revelar que el evento existe pero no es tuyo. Sin sesion (solo pasa en rutas publicas como el
    // detalle de un evento) se relanza: lo toma el ExceptionTranslationFilter de Spring Security,
    // que manda al login y despues devuelve a la pagina pedida (asi siguen andando los links de
    // invitacion por mail). La denegacion por rol del filtro (AccessDeniedAsNotFoundHandler) responde lo mismo:
    // en toda la app, lo que no se puede acceder se ve como "no encontrado", sin distinguir el motivo.
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView accessDenied(final AccessDeniedException e, final Principal principal) {
        if (principal == null) {
            throw e;
        }
        return new ModelAndView("errors/404");
    }

    @ExceptionHandler(EventCancelledException.class)
    public ModelAndView eventCancelled(final EventCancelledException e) {
        return new ModelAndView("redirect:/perfil/eventos");
    }

    // Un evento rechazado tampoco se edita (el rechazo es definitivo): mismo destino que el cancelado.
    @ExceptionHandler(EventRejectedException.class)
    public ModelAndView eventRejected(final EventRejectedException e) {
        return new ModelAndView("redirect:/perfil/eventos");
    }

    // Uno que ya paso tampoco se edita: mismo destino.
    @ExceptionHandler(EventFinishedException.class)
    public ModelAndView eventFinished(final EventFinishedException e) {
        return new ModelAndView("redirect:/perfil/eventos");
    }

    // Un argumento numerico (ej. "long id", "int page") que llega con algo que no es un numero -- "/eventos/abc", o un
    // numero mas grande que el tipo -- hace que Spring falle ANTES de que el controller pueda buscarlo y tirar el
    // EventNotFoundException de siempre. Si es un id de la ruta, ese recurso no existe (404, la misma pagina de
    // siempre); si es un parametro de la URL (?page=abc), es un pedido mal armado (400).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ModelAndView invalidArgumentFormat(final MethodArgumentTypeMismatchException e,
                                              final HttpServletResponse response) {
        response.setStatus(e.getParameter().hasParameterAnnotation(PathVariable.class)
                ? HttpStatus.NOT_FOUND.value() : HttpStatus.BAD_REQUEST.value());
        return new ModelAndView("errors/404");
    }

    // Alguien abre con el navegador (GET) una URL que solo acepta POST
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ModelAndView methodNotAllowed(final HttpRequestMethodNotSupportedException e) {
        return new ModelAndView("errors/404");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView missingParameter(final MissingServletRequestParameterException e) {
        return new ModelAndView("errors/404");
    }

    // El archivo supera el limite duro del servidor (web.xml, multipart-config). Falla antes de llegar a ningun
    // controller, asi que no sabe de que pantalla vino: el destino se elige por la URL del pedido (editar un evento
    // vuelve a editarlo, cambiar la portada vuelve al perfil, lo demas es publicar).
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView imageTooLarge(final MaxUploadSizeExceededException e, final HttpServletRequest request,
                                      final Authentication authentication) {
        final String path = request.getRequestURI().substring(request.getContextPath().length());
        final Matcher editEvent = EDIT_EVENT_PATH.matcher(path);
        if (editEvent.matches()) {
            return new ModelAndView("redirect:/eventos/" + editEvent.group(1) + "/editar?imageError=true");
        }
        final Optional<PlancitoUserDetails> user = PlancitoUserDetails.from(authentication);
        if (COVER_PATH.equals(path) && user.isPresent()) {
            // El cartel viaja como flash attribute (el pedido ya no llega a ningun controller que lo pida por parametro).
            RequestContextUtils.getOutputFlashMap(request).put("coverMessage", InvalidImageException.Reason.TOO_LARGE.name());
            return new ModelAndView("redirect:/usuarios/" + user.get().getId());
        }
        return new ModelAndView("redirect:/eventos/publicar?imageError=true");
    }

    // Un pedido que no es multipart (o viene roto) a una pantalla que espera un archivo: no es un archivo grande, es un
    // pedido mal armado (ver imageTooLarge() para ese caso).
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView malformedUpload(final MultipartException e) {
        return new ModelAndView("errors/404");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView unexpectedError(final Exception e) {
        LOGGER.error("Error inesperado atendiendo un pedido", e);
        return new ModelAndView("errors/500");
    }
}
