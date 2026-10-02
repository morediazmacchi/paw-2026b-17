package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
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
 * Sin esto, cualquier excepcion le muestra al usuario el stack trace crudo del servidor.
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
    // es global) y deja "currentUser" en el Model de cada request. Sirve para
    // dos cosas: los JSP pueden usar ${currentUser} sin que cada controller lo
    // agregue a mano, y cualquier metodo de controller puede pedirlo directo
    // como parametro en vez de resolver el Principal el mismo (ver
    // EventController.publish()).
    // OJO: ese parametro SIEMPRE se declara con binding = false:
    //     @ModelAttribute(value = "currentUser", binding = false) final User currentUser
    // Sin eso, Spring ademas de tomar el User del Model le "bindea" encima los
    // parametros del pedido y las {variables} de la URL (User tiene setId, setName
    // y setEmail): en POST /eventos/7/quiero-ir el {id}=7 del evento terminaba
    // pisando el id del usuario logueado.
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
        // No se loguea como error: que alguien escriba un id inexistente es normal.
        return new ModelAndView("errors/404");
    }

    // Un @PathVariable numerico (ej. "long id") que llega con algo que no es
    // un numero -- "/eventos/abc", o un numero mas grande que Long.MAX_VALUE --
    // hace que Spring falle ANTES de que el controller pueda buscarlo y tirar
    // el EventNotFoundException de siempre. Para quien lo escribio a mano es
    // exactamente lo mismo que un id inexistente, asi que se muestra la misma
    // pantalla en vez de la generica de error inesperado.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView invalidIdFormat(final MethodArgumentTypeMismatchException e) {
        return new ModelAndView("errors/404");
    }

    // Alguien abre con el navegador (GET) una URL que solo acepta POST -- un
    // link viejo, o escribir a mano /eventos/7/quiero-ir --, o falta un parametro
    // obligatorio. No es un fallo nuestro, asi que no es un 500 ni se loguea como
    // error: se contesta con el codigo HTTP que corresponde y la misma pantalla
    // de "no encontrado".
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

    // El archivo supera el limite duro del servidor (web.xml, <multipart-config>).
    // Sin esto, la request se corta antes de llegar a ImageServiceImpl: en Pampero
    // (Apache adelante de Tomcat) eso se ve como un 502 Bad Gateway crudo.
    // No se loguea como error: que alguien suba un archivo muy pesado es normal.
    @ExceptionHandler(MultipartException.class)
    public ModelAndView imageTooLarge(final MultipartException e) {
        return new ModelAndView("redirect:/eventos/publicar?imageError=true");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ModelAndView unexpectedError(final Exception e) {
        // Al usuario le mostramos una pagina prolija, pero el detalle queda en el log
        // del servidor para que nosotras podamos investigar.
        LOGGER.error("Error inesperado atendiendo un pedido", e);
        return new ModelAndView("errors/500");
    }
}
