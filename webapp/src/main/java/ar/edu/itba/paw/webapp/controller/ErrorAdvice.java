package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maneja los errores de TODOS los controllers desde un solo lugar.
 * Sin esto, cualquier excepcion le muestra al usuario el stack trace crudo del servidor.
 */
@ControllerAdvice
public class ErrorAdvice {

    private static final Log LOGGER = LogFactory.getLog(ErrorAdvice.class);

    @ExceptionHandler(EventNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView eventNotFound(final EventNotFoundException e) {
        // No se loguea como error: que alguien escriba un id inexistente es normal.
        return new ModelAndView("errors/404");
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
