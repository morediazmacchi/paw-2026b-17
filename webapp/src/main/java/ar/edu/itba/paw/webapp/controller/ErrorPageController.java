package ar.edu.itba.paw.webapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Paginas de error para lo que NUNCA llega a un controller.
 *
 * ErrorAdvice cubre las excepciones que lanzan los controllers, pero una URL que
 * no coincide con ningun @RequestMapping no llega a ninguno: el DispatcherServlet
 * responde 404 y @ControllerAdvice no se entera. Ese caso lo agarra el
 * <error-page> del web.xml, que redirige a estas rutas.
 */
@Controller
public class ErrorPageController {

    @RequestMapping("/error/404")
    public ModelAndView notFound() {
        return new ModelAndView("errors/404");
    }

    @RequestMapping("/error/500")
    public ModelAndView serverError() {
        return new ModelAndView("errors/500");
    }
}
