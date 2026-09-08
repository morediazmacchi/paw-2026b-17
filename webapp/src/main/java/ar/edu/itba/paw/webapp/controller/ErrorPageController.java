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
 *
 * Podrian apuntar al JSP directo, pero entonces la vista se renderizaria sin el
 * contexto de Spring y los <spring:message> de las paginas de error no
 * resolverian. Pasando por un controller, se renderizan igual que cualquier
 * otra vista de la aplicacion.
 *
 * A proposito no llevan @ResponseStatus: el codigo de estado real (400, 404, 500)
 * ya lo puso el servidor antes de redirigir aca, y ponerlo de nuevo lo pisaria.
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
