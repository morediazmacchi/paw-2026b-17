package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.EventForm;
import ar.edu.itba.paw.webapp.form.RegistrationForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Calendar;
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

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView home() {
        return new ModelAndView("redirect:/eventos");
    }

    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam(value = "categoria", required = false) final String categoria,
                              @RequestParam(value = "precioMin", required = false) final String precioMin,
                              @RequestParam(value = "precioMax", required = false) final String precioMax,
                              @RequestParam(value = "fechaDesde", required = false) final String fechaDesde,
                              @RequestParam(value = "fechaHasta", required = false) final String fechaHasta,
                              @RequestParam(value = "barrio", required = false) final String barrio,
                              @RequestParam(value = "rsvp", required = false) final String rsvp) {
        final Long categoryId = parseLong(categoria);
        final Map<String, String> filterErrors = new HashMap<>();

        Double minPrice = parseDouble(precioMin);
        Double maxPrice = parseDouble(precioMax);
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            filterErrors.put("precioMax", "Tiene que ser mayor o igual al precio desde");
            minPrice = null;
            maxPrice = null;
        }

        Date fromDate = parseDate(fechaDesde);
        Date toDate = parseDateEndOfDay(fechaHasta);
        final Date toDateRaw = parseDate(fechaHasta);
        if (fromDate != null && toDateRaw != null && toDateRaw.before(fromDate)) {
            filterErrors.put("fechaHasta", "Tiene que ser posterior a la fecha desde");
            fromDate = null;
            toDate = null;
        }

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryId(categoryId);
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setFromDate(fromDate);
        criteria.setToDate(toDate);
        criteria.setBarrio(parseBarrioFilter(barrio));

        final ModelAndView mav = new ModelAndView("events/list");
        mav.addObject("events", eventService.search(criteria));
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("selectedCategoria", categoryId);
        mav.addObject("precioMin", precioMin);
        mav.addObject("precioMax", precioMax);
        mav.addObject("fechaDesde", fechaDesde);
        mav.addObject("fechaHasta", fechaHasta);
        mav.addObject("barrio", barrio);
        mav.addObject("filterErrors", filterErrors);
        mav.addObject("rsvp", rsvp);
        return mav;
    }

    // Los filtros son de la barra de busqueda: si vienen mal formados, se ignoran
    // (como si no se hubieran cargado) en vez de romper la pagina con un 500.
    private static Long parseLong(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Double parseDouble(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Date parseDate(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (final ParseException e) {
            return null;
        }
    }

    // "Hasta" tiene que incluir todo ese dia: se corre al dia siguiente a las 00:00
    // y en la query se compara con "<", no "<=".
    private static Date parseDateEndOfDay(final String value) {
        final Date date = parseDate(value);
        if (date == null) {
            return null;
        }
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }

    private static Barrio parseBarrioFilter(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Barrio.valueOf(value);
        } catch (final IllegalArgumentException e) {
            return null;
        }
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

    // El mensaje default de Spring para "typeMismatch" es ilegible.
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
                              @Valid final RegistrationForm form, final BindingResult errors) {
        if (!eventService.findById(id).isPresent()) {
            throw new EventNotFoundException(id);
        }
        if (errors.hasErrors()) {
            return detailWithErrors(id, form, errors);
        }
        try {
            final RegistrationResult result = registerRetryingOnConflict(id, form);
            final String rsvpParam = result == RegistrationResult.ALREADY_REGISTERED ? "ya-anotado" : "confirmed";
            return new ModelAndView("redirect:/eventos/" + id + "?rsvp=" + rsvpParam);
        } catch (final IllegalStateException e) {
            return new ModelAndView("redirect:/eventos/" + id + "?rsvp=lleno");
        }
    }

    private RegistrationResult registerRetryingOnConflict(final long id, final RegistrationForm form) {
        try {
            return registrationService.register(id, form.getEmail(), form.getNombre());
        } catch (final DataIntegrityViolationException e) {
            // Reintento en una transaccion nueva: la anterior quedo abortada por el conflicto.
            return registrationService.register(id, form.getEmail(), form.getNombre());
        }
    }

    private ModelAndView detailWithErrors(final long id, final RegistrationForm form, final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return eventService.findById(id)
                .map(event -> {
                    final ModelAndView mav = new ModelAndView("events/detail");
                    mav.addObject("event", event);
                    mav.addObject("full", registrationService.isFull(id));
                    mav.addObject("registrationForm", form);
                    mav.addObject("fieldErrors", fieldErrors);
                    return mav;
                })
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
