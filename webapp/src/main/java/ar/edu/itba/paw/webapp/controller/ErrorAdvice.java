package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.model.exception.EventRejectedException;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * Maneja los errores de TODOS los controllers desde un solo lugar.
 */
@ControllerAdvice
public class ErrorAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorAdvice.class);

    // Corre antes de CUALQUIER metodo de CUALQUIER controller (ControllerAdvice
    // es global) y deja "currentUser" en el Model de cada request. Sale del principal de la sesion
    // (PlancitoUserDetails: id, email, nombre y roles, armado una sola vez en el login), sin ir a la
    // base. Es un User "de sesion": solo trae esos cuatro datos. El nombre es el del login: si se edita
    // el perfil se ve el viejo hasta el proximo login. Sin sesion es null.
    @ModelAttribute("currentUser")
    public User currentUserFromPrincipal(final Authentication authentication) {
        return PlancitoUserDetails.from(authentication).map(ErrorAdvice::toUser).orElse(null);
    }

    private static User toUser(final PlancitoUserDetails principal) {
        final User user = new User(principal.getEmail(), principal.getName());
        user.setId(principal.getId());
        user.setRoles(Arrays.stream(Role.values())
                .filter(principal::hasRole)
                .collect(Collectors.toCollection(HashSet::new)));
        return user;
    }

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
