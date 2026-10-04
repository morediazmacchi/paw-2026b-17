package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

/**
 * Maneja los errores de TODOS los controllers desde un solo lugar.
 */
@ControllerAdvice
public class ErrorAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorAdvice.class);

    private final UserService userService;

    @Autowired
    public ErrorAdvice(final UserService userService) {
        this.userService = userService;
    }

    // Corre antes de CUALQUIER metodo de CUALQUIER controller (ControllerAdvice
    // es global) y deja "currentUser" en el Model de cada request.
    @ModelAttribute("currentUser")
    public User currentUserFromPrincipal(final Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.findByEmail(principal.getName()).orElse(null);
    }

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView eventNotFound(final EventNotFoundException e) {
        return new ModelAndView("errors/404");
    }

    @ExceptionHandler(EventCancelledException.class)
    public ModelAndView eventCancelled(final EventCancelledException e) {
        return new ModelAndView("redirect:/perfil/eventos");
    }

    // Un @PathVariable numerico (ej. "long id") que llega con algo que no es
    // un numero -- "/eventos/abc", o un numero mas grande que Long.MAX_VALUE --
    // hace que Spring falle ANTES de que el controller pueda buscarlo y tirar
    // el EventNotFoundException de siempre.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView invalidIdFormat(final MethodArgumentTypeMismatchException e) {
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

    // El archivo supera el limite duro del servidor
    @ExceptionHandler(MultipartException.class)
    public ModelAndView imageTooLarge(final MultipartException e) {
        return new ModelAndView("redirect:/eventos/publicar?imageError=true");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView unexpectedError(final Exception e) {
        LOGGER.error("Error inesperado atendiendo un pedido", e);
        return new ModelAndView("errors/500");
    }
}
