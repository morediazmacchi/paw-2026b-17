package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.InscripcionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Controller
public class EventController {

    private final EventService eventService;
    private final CategoryService categoryService;
    private final InscripcionService inscripcionService;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final InscripcionService inscripcionService) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.inscripcionService = inscripcionService;
    }

    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam(value = "categoria", required = false) final String categoria,
                              @RequestParam(value = "rsvp", required = false) final String rsvp) {
        final ModelAndView mav = new ModelAndView("events/list");
        final Long categoriaId = (categoria == null || categoria.isEmpty()) ? null : Long.valueOf(categoria);
        final List<Event> events = (categoriaId == null)
                ? eventService.findAll()
                : eventService.findByCategoria(categoriaId);
        mav.addObject("events", events);
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("selectedCategoria", categoriaId);
        mav.addObject("rsvp", rsvp);
        return mav;
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.GET)
    public ModelAndView publishForm() {
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categoryService.findAll());
        return mav;
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(
            @RequestParam final String titulo,
            @RequestParam(required = false) final String descripcion,
            @RequestParam final long categoriaId,
            @RequestParam final String fechaEvento,
            @RequestParam final String direccion,
            @RequestParam(required = false) final String barrio,
            @RequestParam(required = false) final String imagenUrl,
            @RequestParam(required = false) final String precio,
            @RequestParam(required = false) final String cupoMaximo,
            @RequestParam final String creadorEmail) throws ParseException {

        final Date parsedFecha = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(fechaEvento);
        final double parsedPrecio = (precio == null || precio.trim().isEmpty()) ? 0.0 : Double.parseDouble(precio);
        final Integer parsedCupo = (cupoMaximo == null || cupoMaximo.trim().isEmpty()) ? null : Integer.valueOf(cupoMaximo);

        final Event event = new Event(titulo, descripcion, parsedFecha, direccion, barrio, imagenUrl,
                parsedPrecio, parsedCupo, creadorEmail, categoriaId);
        eventService.create(event);
        return new ModelAndView("redirect:/eventos");
    }

    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id,
                              @RequestParam final String email,
                              @RequestParam(required = false) final String nombre) {
        final String estado = inscripcionService.register(id, email, nombre);
        return new ModelAndView("redirect:/eventos?rsvp=" + estado);
    }
}
