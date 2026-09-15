package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.EventForm;
import ar.edu.itba.paw.webapp.form.RegistrationForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class EventController {

    private static final int PAGE_SIZE = 12;
    private static final int FEATURED_COUNT = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final ImageService imageService;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService, final ImageService imageService,
                            final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.imageService = imageService;
        this.messageSource = messageSource;
    }

    // Todos los mensajes que se muestran en pantalla (errores de validacion,
    // errores de negocio) se resuelven aca contra el idioma del pedido actual.
    private String message(final String code, final Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView home() {
        return new ModelAndView("redirect:/eventos");
    }

    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam(value = "categoria", required = false) final List<String> categoria,
                              @RequestParam(value = "precioMin", required = false) final String precioMin,
                              @RequestParam(value = "precioMax", required = false) final String precioMax,
                              @RequestParam(value = "fechaDesde", required = false) final String fechaDesde,
                              @RequestParam(value = "fechaHasta", required = false) final String fechaHasta,
                              @RequestParam(value = "barrio", required = false) final List<String> barrio,
                              @RequestParam(value = "page", required = false) final String page,
                              @RequestParam(value = "sort", required = false) final String sort,
                              @RequestParam(value = "rsvp", required = false) final String rsvp) {
        final List<Long> categoryIds = parseLongList(categoria);
        final List<Barrio> barrios = parseBarrioList(barrio);
        final Map<String, String> filterErrors = new HashMap<>();

        Double minPrice = parseDouble(precioMin);
        Double maxPrice = parseDouble(precioMax);
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            filterErrors.put("precioMax", message("event.list.priceRangeInvalid"));
            minPrice = null;
            maxPrice = null;
        }

        Date fromDate = parseDate(fechaDesde);
        Date toDate = parseDateEndOfDay(fechaHasta);
        final Date toDateRaw = parseDate(fechaHasta);
        if (fromDate != null && toDateRaw != null && toDateRaw.before(fromDate)) {
            filterErrors.put("fechaHasta", message("event.list.dateRangeInvalid"));
            fromDate = null;
            toDate = null;
        }

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(categoryIds);
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setFromDate(fromDate);
        criteria.setToDate(toDate);
        criteria.setBarrios(barrios);
        criteria.setSort(parseSort(sort));
        criteria.setPage(parsePage(page));
        criteria.setPageSize(PAGE_SIZE);

        final PageResult<Event> result = eventService.search(criteria);

        // "N eventos encontrados" / "Limpiar filtros" solo tienen sentido si el
        // usuario realmente filtro algo. Se mira lo que vino crudo del request,
        // no lo ya parseado: un rango invalido (que se resetea a null arriba)
        // igual cuenta como "hay algo para limpiar".
        final boolean hasActiveFilters = categoryIds != null || barrios != null
                || (precioMin != null && !precioMin.isEmpty())
                || (precioMax != null && !precioMax.isEmpty())
                || (fechaDesde != null && !fechaDesde.isEmpty())
                || (fechaHasta != null && !fechaHasta.isEmpty());

        // Banner destacado: los proximos eventos por fecha, sin los filtros de arriba.
        final EventSearchCriteria featuredCriteria = new EventSearchCriteria();
        featuredCriteria.setPageSize(FEATURED_COUNT);
        final List<Event> featured = eventService.search(featuredCriteria).getContent();

        final ModelAndView mav = new ModelAndView("events/list");
        mav.addObject("events", result.getContent());
        mav.addObject("featuredEvents", featured);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("pageNumbers", buildPageNumbers(result.getPage(), result.getTotalPages()));
        mav.addObject("totalElements", result.getTotalElements());
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("selectedCategorias", categoryIds);
        mav.addObject("precioMin", precioMin);
        mav.addObject("precioMax", precioMax);
        mav.addObject("fechaDesde", fechaDesde);
        mav.addObject("fechaHasta", fechaHasta);
        mav.addObject("selectedBarrios", barrios);
        mav.addObject("hasActiveFilters", hasActiveFilters);
        mav.addObject("sort", sort);
        mav.addObject("filterErrors", filterErrors);
        mav.addObject("rsvp", rsvp);
        return mav;
    }

    // Numeros de pagina a mostrar en el paginador. Con pocas paginas se muestran
    // todas; con muchas, primera + ultima + una ventana alrededor de la actual,
    // con null como marcador de "..." (el JSP lo detecta con <c:if test="${empty p}">).
    private static List<Integer> buildPageNumbers(final int currentPage, final int totalPages) {
        final List<Integer> pages = new ArrayList<>();
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) {
                pages.add(i);
            }
            return pages;
        }
        pages.add(1);
        if (currentPage > 3) {
            pages.add(null);
        }
        final int start = Math.max(2, currentPage - 1);
        final int end = Math.min(totalPages - 1, currentPage + 1);
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        if (currentPage < totalPages - 2) {
            pages.add(null);
        }
        pages.add(totalPages);
        return pages;
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

    // Multi-seleccion: cada elemento se parsea por separado, el que este mal
    // formado se descarta solo (no invalida los demas). Lista vacia = sin filtro.
    private static List<Long> parseLongList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final List<Long> result = values.stream()
                .map(EventController::parseLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
    }

    private static List<Barrio> parseBarrioList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final List<Barrio> result = values.stream()
                .map(EventController::parseBarrioFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
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

    // page=0, page=-1, page=abc caen a la primera pagina. Un page mas alla del
    // total no se corrige aca (no se sabe el total todavia): lo ajusta el service.
    private static int parsePage(final String value) {
        if (value == null || value.isEmpty()) {
            return 1;
        }
        try {
            final int parsed = Integer.parseInt(value);
            return parsed < 1 ? 1 : parsed;
        } catch (final NumberFormatException e) {
            return 1;
        }
    }

    private static EventSort parseSort(final String value) {
        if (value == null || value.isEmpty()) {
            return EventSort.FECHA;
        }
        try {
            return EventSort.valueOf(value);
        } catch (final IllegalArgumentException e) {
            return EventSort.FECHA;
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
                    mav.addObject("availableSpots", registrationService.availableSpots(id));
                    return mav;
                })
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.GET)
    public ModelAndView publishForm(@RequestParam(value = "imageError", required = false) final String imageError) {
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        if (imageError != null) {
            mav.addObject("imageError", message("event.publish.imageTooLargeError"));
        }
        return mav;
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(@Valid final EventForm form, final BindingResult errors,
                                 @RequestParam(value = "image", required = false) final MultipartFile image) {
        final Barrio parsedBarrio = parseBarrio(form.getBarrio(), errors);

        if (errors.hasErrors()) {
            return publishFormWithErrors(form, errors, null);
        }

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        // Sin setLenient(false), SimpleDateFormat "desborda" las fechas imposibles en
        // silencio: 2026-13-45T99:99 pasa el @Pattern del form (que solo valida la forma)
        // y se convierte en 2027-02-18 04:39.
        dateFormat.setLenient(false);
        final Date parsedDate;
        try {
            parsedDate = dateFormat.parse(form.getFechaEvento());
        } catch (final ParseException e) {
            // Codigo "Pattern": reusa la clave Pattern.eventForm.fechaEvento que ya existe.
            errors.rejectValue("fechaEvento", "Pattern",
                    "La fecha y horario no tienen un formato valido");
            return publishFormWithErrors(form, errors, null);
        }
        // No se puede publicar un evento que ya paso. El formato lo valida el @Pattern
        // del form, pero "que sea futura" no se puede expresar con una expresion regular:
        // hay que comparar contra el momento actual, y eso se hace aca.
        if (parsedDate.before(new Date())) {
            // El codigo "Future" hace que Spring busque la clave
            // Future.eventForm.fechaEvento en los messages.properties, igual que
            // hace con NotBlank y Pattern. El tercer argumento es el texto por
            // defecto, para que no explote si falta la clave.
            errors.rejectValue("fechaEvento", "Future",
                    "La fecha del evento tiene que ser futura");
            return publishFormWithErrors(form, errors, null);
        }

        final double parsedPrice = Double.parseDouble(form.getPrecio());
        final Integer parsedCapacity = "0".equals(form.getCupoMaximo()) ? null : Integer.valueOf(form.getCupoMaximo());

        Long imageId = null;
        if (image != null && !image.isEmpty()) {
            try {
                final Image saved = imageService.create(
                        image.getBytes(), image.getContentType(), image.getOriginalFilename());
                imageId = saved.getId();
            } catch (final IOException e) {
                return publishFormWithErrors(form, errors, message("event.publish.imageReadError"));
            } catch (final IllegalArgumentException e) {
                return publishFormWithErrors(form, errors, message(e.getMessage()));
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

    private Barrio parseBarrio(final String barrio, final BindingResult errors) {
        if (barrio == null || barrio.isEmpty()) {
            return null;
        }
        try {
            return Barrio.valueOf(barrio);
        } catch (final IllegalArgumentException e) {
            errors.rejectValue("barrio", "barrio.invalido", message("barrio.invalido"));
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
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        return eventService.findById(id)
                .map(event -> {
                    final ModelAndView mav = new ModelAndView("events/detail");
                    mav.addObject("event", event);
                    mav.addObject("full", registrationService.isFull(id));
                    mav.addObject("availableSpots", registrationService.availableSpots(id));
                    mav.addObject("registrationForm", form);
                    mav.addObject("fieldErrors", fieldErrors);
                    return mav;
                })
                .orElseThrow(() -> new EventNotFoundException(id));
    }
}
