package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.EventForm;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
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
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class EventController {

    private static final int PAGE_SIZE = 12;
    private static final int FEATURED_COUNT = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final ImageService imageService;
    private final UserService userService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService, final ImageService imageService,
                            final UserService userService, final FavoriteService favoriteService,
                            final ReviewService reviewService, final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.imageService = imageService;
        this.userService = userService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.messageSource = messageSource;
    }

    private User currentUser(final Principal principal) {
        return userService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado sin fila en la base: " + principal.getName()));
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
                              @RequestParam(value = "puntajeMin", required = false) final String puntajeMin,
                              @RequestParam(value = "page", required = false) final String page,
                              @RequestParam(value = "sort", required = false) final String sort,
                              @RequestParam(value = "rsvp", required = false) final String rsvp,
                              final Principal principal) {
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
        criteria.setMinOrganizerRating(parseInteger(puntajeMin));
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
                || (fechaHasta != null && !fechaHasta.isEmpty())
                || (puntajeMin != null && !puntajeMin.isEmpty());

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
        mav.addObject("puntajeMin", puntajeMin);
        mav.addObject("hasActiveFilters", hasActiveFilters);
        mav.addObject("sort", sort);
        mav.addObject("filterErrors", filterErrors);
        mav.addObject("rsvp", rsvp);
        // Anonimo no ve el corazon de favorito (paw:card lo esconde con
        // sec:authorize), asi que ni consultamos: nadie va a leer este set.
        if (principal != null) {
            mav.addObject("favoriteEventIds", new HashSet<>(favoriteService.findEventIdsByUser(currentUser(principal).getId())));
        }
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
    private static Integer parseInteger(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

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
                                @RequestParam(value = "rsvp", required = false) final String rsvp,
                                final Principal principal) {
        return eventService.findById(id)
                .map(event -> {
                    final ModelAndView mav = new ModelAndView("events/detail");
                    mav.addObject("event", event);
                    mav.addObject("rsvp", rsvp);
                    mav.addObject("full", registrationService.isFull(id));
                    mav.addObject("finished", event.getEventDate().before(new Date()));
                    mav.addObject("availableSpots", registrationService.availableSpots(id));
                    if (principal != null) {
                        final User user = currentUser(principal);
                        mav.addObject("favorite", favoriteService.isFavorite(user.getId(), id));
                        // No alcanza con saber SI esta anotada: el boton dice "Darme de
                        // baja" o "Salir de la lista de espera" segun en que estado quedo.
                        final Optional<Registration> myRegistration =
                                registrationService.findByEventAndEmail(id, user.getEmail());
                        mav.addObject("registered", myRegistration.isPresent());
                        mav.addObject("waitlisted", myRegistration
                                .map(r -> Registration.STATUS_WAITLISTED.equals(r.getStatus()))
                                .orElse(false));
                    }
                    addCreatorReputation(mav, event.getCreatorEmail());
                    return mav;
                })
                .orElseThrow(() -> new EventNotFoundException(id));
    }

    // Puntaje del promotor (todas sus reseñas, de todos sus eventos) + la lista
    // completa para el popup "Ver reseñas de este promotor", con la mini info
    // del evento reseñado en cada una (reviewEventsById).
    private void addCreatorReputation(final ModelAndView mav, final String creatorEmail) {
        final List<Review> creatorReviews = reviewService.findByCreatorEmail(creatorEmail);
        mav.addObject("creatorReviewCount", creatorReviews.size());
        if (!creatorReviews.isEmpty()) {
            final double average = creatorReviews.stream().mapToInt(Review::getRating).average().orElse(0);
            mav.addObject("creatorAverageRating", average);
            mav.addObject("creatorReviews", creatorReviews);
            final Map<Long, Event> reviewEventsById = new HashMap<>();
            for (final Review review : creatorReviews) {
                eventService.findById(review.getEventId()).ifPresent(e -> reviewEventsById.put(e.getId(), e));
            }
            mav.addObject("reviewEventsById", reviewEventsById);
        }
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
                                 @RequestParam(value = "image", required = false) final MultipartFile image,
                                 final Principal principal) {
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

        // El creador es quien esta logueado, nunca un campo del form: si viniera del
        // form, cualquiera podria publicar un evento a nombre de otro email.
        final Event event = new Event(form.getTitulo(), form.getDescripcion(), parsedDate, form.getDireccion(),
                parsedBarrio, imageId, parsedPrice, parsedCapacity, principal.getName(), form.getCategoriaId());
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

    // No hace falta antMatcher propio: esto no esta en el permitAll de
    // WebAuthConfig, asi que ya cae bajo anyRequest().authenticated(). Un
    // Principal siempre presente en este metodo implica un User existente.
    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id, final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // La UI ya esconde el boton en este caso (ver detail.jsp), pero un POST
        // directo igual no tiene que poder anotarte a algo que ya paso.
        if (event.getEventDate().before(new Date())) {
            return new ModelAndView("redirect:/eventos/" + id + "?rsvp=finalizado");
        }
        // Quien se inscribe es quien esta logueado, nunca un campo del form:
        // mismo motivo que el creator_email de publish().
        final User user = currentUser(principal);
        // Ya no hace falta atrapar IllegalStateException: el service dejo de
        // lanzarla cuando el evento esta lleno, ahora devuelve WAITLISTED.
        final RegistrationResult result = registerRetryingOnConflict(id, user);
        return new ModelAndView("redirect:/eventos/" + id + "?rsvp=" + rsvpParam(result));
    }

    // Los 4 finales posibles del RSVP viajan como parametro en la URL
    // (POST-Redirect-GET) y detail.jsp elige el cartel a partir de esto.
    private static String rsvpParam(final RegistrationResult result) {
        switch (result) {
            case WAITLISTED:
                return "waitlist";
            case ALREADY_REGISTERED:
                return "ya-anotado";
            case ALREADY_WAITLISTED:
                return "ya-en-waitlist";
            case CONFIRMED:
            default:
                return "confirmed";
        }
    }

    private RegistrationResult registerRetryingOnConflict(final long id, final User user) {
        try {
            return registrationService.register(id, user.getEmail(), user.getName());
        } catch (final DataIntegrityViolationException e) {
            // Reintento en una transaccion nueva: la anterior quedo abortada por el conflicto.
            return registrationService.register(id, user.getEmail(), user.getName());
        }
    }

    // Mismo mecanismo que "Cancelar" en Mis inscripciones (ProfileController),
    // pero desde el detalle: busca la inscripcion propia de este evento y la
    // cancela, en vez de recibir el id de la inscripcion por parametro.
    @RequestMapping(value = "/eventos/{id}/darme-de-baja", method = RequestMethod.POST)
    public ModelAndView unregister(@PathVariable final long id, final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // La UI ya esconde el boton en este caso (ver detail.jsp): un evento que
        // ya paso no se puede "des-anotar", ya sucedio.
        if (event.getEventDate().before(new Date())) {
            return new ModelAndView("redirect:/eventos/" + id + "?rsvp=finalizado");
        }
        final User user = currentUser(principal);
        final Optional<Registration> registration = registrationService.findByEventAndEmail(id, user.getEmail());
        // Se mira ANTES de borrar: despues la fila ya no existe y no hay forma
        // de saber si el cartel tiene que decir "te diste de baja del evento"
        // o "saliste de la lista de espera".
        final boolean wasWaitlisted = registration
                .map(r -> Registration.STATUS_WAITLISTED.equals(r.getStatus()))
                .orElse(false);
        registration.ifPresent(r -> registrationService.cancel(r.getId(), user.getEmail()));
        return new ModelAndView("redirect:/eventos/" + id + "?rsvp=" + (wasWaitlisted ? "baja-waitlist" : "baja"));
    }

    // Solo el creador del evento puede ver esta lista. Si no lo es (o el
    // evento no existe), tira el mismo EventNotFoundException en los dos
    // casos -- no hay que confirmarle a nadie que un evento ajeno existe.
    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id, final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        final User user = currentUser(principal);
        if (!event.getCreatorEmail().equalsIgnoreCase(user.getEmail())) {
            throw new EventNotFoundException(id);
        }

        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", registrationService.findByEventId(id));
        return mav;
    }

    // returnTo viaja como hidden en el form del corazon (paw:card/detail.jsp):
    // desde el listado tiene que volver al listado (con sus filtros), no
    // siempre al detalle. Sin el, o si viene manipulado a una URL ajena
    // (SafeRedirect), cae al detalle como default razonable.
    @RequestMapping(value = "/eventos/{id}/favorito", method = RequestMethod.POST)
    public ModelAndView addFavorite(@PathVariable final long id,
                                     @RequestParam(value = "returnTo", required = false) final String returnTo,
                                     final Principal principal) {
        if (!eventService.findById(id).isPresent()) {
            throw new EventNotFoundException(id);
        }
        favoriteService.add(currentUser(principal).getId(), id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    @RequestMapping(value = "/eventos/{id}/favorito/quitar", method = RequestMethod.POST)
    public ModelAndView removeFavorite(@PathVariable final long id,
                                        @RequestParam(value = "returnTo", required = false) final String returnTo,
                                        final Principal principal) {
        if (!eventService.findById(id).isPresent()) {
            throw new EventNotFoundException(id);
        }
        favoriteService.remove(currentUser(principal).getId(), id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    private static String safeReturnTo(final String returnTo, final long id) {
        return SafeRedirect.isSafe(returnTo) ? returnTo : "/eventos/" + id;
    }
}
