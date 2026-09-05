package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.EventForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EventController {

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final ImageService imageService;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService, final ImageService imageService) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.imageService = imageService;
    }

    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam(value = "categoria", required = false) final String categoria,
                              @RequestParam(value = "rsvp", required = false) final String rsvp) {
        final ModelAndView mav = new ModelAndView("events/list");
        final Long categoryId = (categoria == null || categoria.isEmpty()) ? null : Long.valueOf(categoria);
        final List<Event> events = (categoryId == null)
                ? eventService.findAll()
                : eventService.findByCategory(categoryId);
        mav.addObject("events", events);
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("selectedCategoria", categoryId);
        mav.addObject("rsvp", rsvp);
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}", method = RequestMethod.GET)
    public ModelAndView detail(@PathVariable final long id,
                                @RequestParam(value = "rsvp", required = false) final String rsvp) {
        return eventService.findById(id)
                .map(event -> {
                    final ModelAndView mav = new ModelAndView("events/detail");
                    mav.addObject("event", event);
                    mav.addObject("rsvp", rsvp);
                    mav.addObject("full", registrationService.isFull(id));
                    return mav;
                })
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.GET)
    public ModelAndView publishForm() {
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        return mav;
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(@Valid final EventForm form, final BindingResult errors,
                                 @RequestParam(value = "image", required = false) final MultipartFile image)
            throws ParseException {
        final Barrio parsedBarrio = parseBarrio(form.getBarrio(), errors);

        if (errors.hasErrors()) {
            return publishFormWithErrors(form, errors, null);
        }

        final Date parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").parse(form.getFechaEvento());
        final double parsedPrice = Double.parseDouble(form.getPrecio());
        final Integer parsedCapacity = "0".equals(form.getCupoMaximo()) ? null : Integer.valueOf(form.getCupoMaximo());

        Long imageId = null;
        if (image != null && !image.isEmpty()) {
            try {
                final Image saved = imageService.create(
                        image.getBytes(), image.getContentType(), image.getOriginalFilename());
                imageId = saved.getId();
            } catch (final IOException e) {
                return publishFormWithErrors(form, errors, "No se pudo leer el archivo, proba de nuevo.");
            } catch (final IllegalArgumentException e) {
                return publishFormWithErrors(form, errors, e.getMessage());
            }
        }

        final Event event = new Event(form.getTitulo(), form.getDescripcion(), parsedDate, form.getDireccion(),
                parsedBarrio, imageId, parsedPrice, parsedCapacity, form.getCreadorEmail(), form.getCategoriaId());
        try {
            eventService.create(event);
        } catch (final IllegalArgumentException e) {
            errors.rejectValue("categoriaId", "categoria.invalida", e.getMessage());
            return publishFormWithErrors(form, errors, null);
        }
        return new ModelAndView("redirect:/eventos");
    }

    private ModelAndView publishFormWithErrors(final EventForm form, final BindingResult errors,
                                                final String imageError) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        mav.addObject("imageError", imageError);
        return mav;
    }

    // "typeMismatch" es el codigo que usa Spring cuando no puede convertir el
    // parametro del form al tipo del campo (hoy, solo pasa con categoriaId,
    // que es el unico campo no-String). El mensaje default de Spring es ilegible.
    private static String messageFor(final FieldError fieldError) {
        if ("typeMismatch".equals(fieldError.getCode()) && "categoriaId".equals(fieldError.getField())) {
            return "Elegi una categoria valida";
        }
        return fieldError.getDefaultMessage();
    }

    private static Barrio parseBarrio(final String barrio, final BindingResult errors) {
        if (barrio == null || barrio.isEmpty()) {
            return null;
        }
        try {
            return Barrio.valueOf(barrio);
        } catch (final IllegalArgumentException e) {
            errors.rejectValue("barrio", "barrio.invalido", "Elegi un barrio de la lista");
            return null;
        }
    }

    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id,
                              @RequestParam final String email,
                              @RequestParam(required = false) final String nombre) {
        final String status = registrationService.register(id, email, nombre);
        return new ModelAndView("redirect:/eventos/" + id + "?rsvp=" + status);
    }
}
