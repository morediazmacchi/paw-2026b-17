package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventCancelResult;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.EventUpdateResult;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.CategoriaSeleccionForm;
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
import java.math.BigDecimal;
import java.security.Principal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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
    // Ver el comentario de multipart-config en web.xml: sus limites estan
    // calculados en base a este numero (5 fotos de 5 MB cada una, tope que
    // impone ImageServiceImpl).
    private static final int MAX_IMAGES = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final ImageService imageService;
    private final UserService userService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final EventInvitationService eventInvitationService;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService, final ImageService imageService,
                            final UserService userService, final FavoriteService favoriteService,
                            final ReviewService reviewService, final EventInvitationService eventInvitationService,
                            final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.imageService = imageService;
        this.userService = userService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventInvitationService = eventInvitationService;
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
                              @RequestParam(value = "pocosCupos", required = false) final String pocosCupos,
                              @RequestParam(value = "agotados", required = false) final String agotados,
                              @RequestParam(value = "page", required = false) final String page,
                              @RequestParam(value = "sort", required = false) final String sort,
                              @RequestParam(value = "rsvp", required = false) final String rsvp,
                              final Principal principal) {
        final List<Long> categoryIds = parseLongList(categoria);
        final List<Barrio> barrios = parseBarrioList(barrio);
        final Map<String, String> filterErrors = new HashMap<>();

        BigDecimal minPrice = parseDecimal(precioMin);
        BigDecimal maxPrice = parseDecimal(precioMax);
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
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
        // Un si/no, no un rango: cuantos lugares son "pocos" lo define el modelo
        // (EventSearchCriteria.FEW_SPOTS_MIN/MAX), no lo que venga en la URL.
        criteria.setOnlyFewSpotsLeft(pocosCupos != null && !pocosCupos.isEmpty());
        criteria.setOnlySoldOut(agotados != null && !agotados.isEmpty());
        final EventSort parsedSort = parseSort(sort);
        criteria.setSort(parsedSort);
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
                || (puntajeMin != null && !puntajeMin.isEmpty())
                || (pocosCupos != null && !pocosCupos.isEmpty())
                || (agotados != null && !agotados.isEmpty());

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
        mav.addObject("pocosCupos", pocosCupos);
        mav.addObject("agotados", agotados);
        mav.addObject("hasActiveFilters", hasActiveFilters);
        // El ya parseado (siempre un valor valido): el JSP lo usa para marcar la
        // opcion elegida y los links de la paginacion lo repiten tal cual.
        mav.addObject("sort", parsedSort.name());
        mav.addObject("sortOptions", EventSort.values());
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

    private static BigDecimal parseDecimal(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
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
            return EventSort.FECHA_ASC;
        }
        try {
            return EventSort.valueOf(value);
        } catch (final IllegalArgumentException e) {
            // Un valor que no existe (link viejo con "FECHA", o la URL tocada a
            // mano) no es un error: se ordena como siempre, por fecha.
            return EventSort.FECHA_ASC;
        }
    }

    @RequestMapping(value = "/eventos/{id}", method = RequestMethod.GET)
    public ModelAndView detail(@PathVariable final long id,
                                @RequestParam(value = "rsvp", required = false) final String rsvp,
                                final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // Privado y sin sesion: todavia no sabemos quien sos. Al login, y de
        // vuelta aca (WebAuthConfig.loginSuccessHandler) para chequear si estas invitado.
        if (event.isPrivate() && principal == null) {
            return new ModelAndView("redirect:/login?redirect=/eventos/" + id);
        }
        // Para un evento privado, la inscripcion propia (si existe) no es solo
        // dato para la tarjeta de RSVP: es tambien la prueba de que quien pide
        // esto tiene algo que ver con el evento. Por eso se busca ANTES del
        // chequeo de acceso, en vez de mas abajo como antes.
        final Optional<Registration> myRegistration = principal == null
                ? Optional.empty()
                : registrationService.findByEventAndEmail(id, principal.getName());
        if (!canView(event, principal, myRegistration)) {
            // Mismo 404 que un id inexistente: no hay que revelar que el
            // evento existe pero que no es para vos.
            throw new EventNotFoundException(id);
        }

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
            // baja", "Salir de la lista de espera" o "Cancelar mi pedido"
            // segun en que estado quedo. Un rechazo NO cuenta como "anotado":
            // ese estado no tiene nada que cancelar, es terminal.
            final RegistrationStatus myStatus = myRegistration.map(Registration::getStatus).orElse(null);
            mav.addObject("registered", myRegistration.isPresent()
                    && myStatus != RegistrationStatus.REJECTED);
            mav.addObject("waitlisted", myStatus == RegistrationStatus.WAITLISTED);
            mav.addObject("pendingApproval", myStatus == RegistrationStatus.PENDING);
            mav.addObject("rejected", myStatus == RegistrationStatus.REJECTED);
            // El creador no se anota a su propio evento (ver RegistrationServiceImpl.
            // register()): la tarjeta de RSVP se lo dice en vez de ofrecerle un
            // boton que el service le va a rechazar igual.
            mav.addObject("isOwner", event.getCreatorEmail().equalsIgnoreCase(user.getEmail()));
        }
        addCreatorReputation(mav, event.getCreatorEmail());
        return mav;
    }

    // Un evento publico lo puede ver cualquiera, como siempre. Uno privado
    // solo su creador, alguien que ya tenga una fila de inscripcion (en
    // cualquier estado, incluido rechazado: si te rechazaron es porque en
    // algun momento tuviste acceso, no tiene sentido que ahora te vuelvas a
    // topar con un 404), o alguien cuyo email (el de su sesion) fue invitado.
    // El link del mail solo dice que evento es: quien sos lo dice tu sesion.
    private boolean canView(final Event event, final Principal principal,
                             final Optional<Registration> myRegistration) {
        if (!event.isPrivate()) {
            return true;
        }
        if (principal != null && event.getCreatorEmail().equalsIgnoreCase(principal.getName())) {
            return true;
        }
        if (myRegistration.isPresent()) {
            return true;
        }
        return principal != null && eventInvitationService.isInvited(event.getId(), principal.getName());
    }

    // Puntaje del promotor (todas sus reseñas, de todos sus eventos) + la lista
    // completa para el popup "Ver reseñas de este promotor", con la mini info
    // del evento reseñado en cada una (reviewEventsById).
    private void addCreatorReputation(final ModelAndView mav, final String creatorEmail) {
        final List<Review> creatorReviews = reviewService.findByCreatorEmail(creatorEmail);
        mav.addObject("creatorReviewCount", creatorReviews.size());
        if (!creatorReviews.isEmpty()) {
            mav.addObject("creatorAverageRating", reviewService.findCreatorAverageRating(creatorEmail));
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
        final List<Category> categories = categoryService.findAll();
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categories);
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", emptyForm(categories));
        if (imageError != null) {
            mav.addObject("imageError", message("event.publish.imageTooLargeError"));
        }
        return mav;
    }

    // Un CategoriaSeleccionForm por cada categoria existente, todas sin
    // tildar: hace falta que la lista ya tenga una entrada por indice para
    // que el binding indexado de Spring (categorias[0].seleccionada, etc.) y
    // el JSP (que itera "categories" y lee eventForm.categorias[idx]) esten
    // sincronizados incluso en el primer GET, antes de cualquier submit.
    private static EventForm emptyForm(final List<Category> categories) {
        final EventForm form = new EventForm();
        form.setCategorias(categoriaFormsFor(Collections.emptyList(), categories));
        return form;
    }

    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(@Valid final EventForm form, final BindingResult errors,
                                 @RequestParam(value = "images", required = false) final List<MultipartFile> images,
                                 final Principal principal) {
        final Barrio parsedBarrio = parseBarrio(form.getBarrio(), errors);
        final List<EventCategoryInfo> categorias = selectedCategories(form, errors);

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

        final BigDecimal parsedPrice = new BigDecimal(form.getPrecio());
        final Integer parsedCapacity = "0".equals(form.getCupoMaximo()) ? null : Integer.valueOf(form.getCupoMaximo());

        final List<MultipartFile> nonEmptyImages = nonEmptyImages(images);
        if (nonEmptyImages.size() > MAX_IMAGES) {
            return publishFormWithErrors(form, errors, message("event.publish.tooManyImagesError"));
        }
        final List<Long> imageIds;
        try {
            imageIds = uploadImages(nonEmptyImages);
        } catch (final IOException e) {
            return publishFormWithErrors(form, errors, message("event.publish.imageReadError"));
        } catch (final IllegalArgumentException e) {
            return publishFormWithErrors(form, errors, message(e.getMessage()));
        }

        // El creador es quien esta logueado, nunca un campo del form: si viniera del
        // form, cualquiera podria publicar un evento a nombre de otro email.
        final Event event = new Event(form.getTitulo(), form.getDescripcion(), parsedDate, form.getDireccion(),
                parsedBarrio, imageIds, parsedPrice, parsedCapacity, principal.getName(), null);
        event.setCategories(categorias);
        event.setRequiresApproval(form.isRequiereAprobacion());
        event.setVisibility(form.isPrivado() ? EventVisibility.PRIVATE : EventVisibility.PUBLIC);
        event.setLatitude(parseCoordinate(form.getLatitud()));
        event.setLongitude(parseCoordinate(form.getLongitud()));
        try {
            eventService.create(event);
        } catch (final IllegalArgumentException e) {
            errors.rejectValue("categorias", "categoria.invalida", e.getMessage());
            return publishFormWithErrors(form, errors, null);
        }
        return new ModelAndView("redirect:/eventos");
    }

    // Al menos una categoria tildada. No es una regla JSR-303 (ver el
    // comentario sobre @Valid en EventForm.categorias): se agrega a mano,
    // mismo patron que parseBarrio() con "barrio.invalido".
    private List<EventCategoryInfo> selectedCategories(final EventForm form, final BindingResult errors) {
        final List<EventCategoryInfo> selected = form.getCategorias().stream()
                .filter(CategoriaSeleccionForm::isSeleccionada)
                .map(c -> new EventCategoryInfo(c.getCategoryId(), blankToNull(c.getExtraInfo())))
                .collect(Collectors.toList());
        if (selected.isEmpty()) {
            errors.rejectValue("categorias", "categoria.vacia", message("categoria.vacia"));
        }
        return selected;
    }

    private static String blankToNull(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    // El pin del mapa (latitud/longitud) es un campo oculto que solo el JS
    // completa: si vino vacio (nadie toco el mapa) o con algo que no es un
    // numero (request manipulado a mano), se ignora en silencio en vez de
    // rechazar la publicacion entera por un dato que ni siquiera es obligatorio.
    private static Double parseCoordinate(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // El <input type="file" multiple> manda un MultipartFile por cada slot
    // del picker, tildado o no: los que quedaron vacios (usuario abrio el
    // dialogo pero no eligio nada en ese slot) se descartan aca, antes de
    // contarlos contra MAX_IMAGES.
    private static List<MultipartFile> nonEmptyImages(final List<MultipartFile> images) {
        if (images == null) {
            return Collections.emptyList();
        }
        return images.stream().filter(f -> f != null && !f.isEmpty()).collect(Collectors.toList());
    }

    // Sube cada archivo y devuelve sus ids, en el mismo orden en que el
    // usuario los eligio: ese orden es el de la galeria (ver Event.imageIds),
    // la primera es la portada.
    private List<Long> uploadImages(final List<MultipartFile> images) throws IOException {
        final List<Long> ids = new ArrayList<>();
        for (final MultipartFile image : images) {
            final Image saved = imageService.create(image.getBytes(), image.getContentType(), image.getOriginalFilename());
            ids.add(saved.getId());
        }
        return ids;
    }

    // Un CategoriaSeleccionForm por cada categoria existente: "existing" trae
    // las que el evento ya tiene (si las hay) para tildarlas y precargar su
    // extraInfo; el resto queda sin tildar.
    private static List<CategoriaSeleccionForm> categoriaFormsFor(
            final List<EventCategoryInfo> existing, final List<Category> allCategories) {
        final Map<Long, EventCategoryInfo> byId = existing.stream()
                .collect(Collectors.toMap(EventCategoryInfo::getCategoryId, c -> c));
        return allCategories.stream().map(cat -> {
            final CategoriaSeleccionForm categoriaForm = new CategoriaSeleccionForm();
            categoriaForm.setCategoryId(cat.getId());
            final EventCategoryInfo info = byId.get(cat.getId());
            categoriaForm.setSeleccionada(info != null);
            categoriaForm.setExtraInfo(info == null ? null : info.getExtraInfo());
            return categoriaForm;
        }).collect(Collectors.toList());
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
        mav.addObject("categoriasError", firstCategoriasError(fieldErrors));
        mav.addObject("imageError", imageError);
        return mav;
    }

    private static String firstCategoriasError(final Map<String, String> fieldErrors) {
        if (fieldErrors.containsKey("categorias")) {
            return fieldErrors.get("categorias");
        }
        return fieldErrors.entrySet().stream()
                .filter(e -> e.getKey().startsWith("categorias["))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
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
    public ModelAndView rsvp(@PathVariable final long id,
                              final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // Mismo chequeo que el detalle: el boton ya esta escondido en la UI si
        // no tenes acceso, pero un POST directo no se puede confiar solo en eso.
        final Optional<Registration> myRegistration =
                registrationService.findByEventAndEmail(id, principal.getName());
        if (!canView(event, principal, myRegistration)) {
            throw new EventNotFoundException(id);
        }
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

    // Los finales posibles del RSVP viajan como parametro en la URL
    // (POST-Redirect-GET) y detail.jsp elige el cartel a partir de esto.
    private static String rsvpParam(final RegistrationResult result) {
        switch (result) {
            case WAITLISTED:
                return "waitlist";
            case PENDING:
                return "pendiente";
            case ALREADY_REGISTERED:
                return "ya-anotado";
            case ALREADY_WAITLISTED:
                return "ya-en-waitlist";
            case ALREADY_PENDING:
                return "ya-pendiente";
            case REJECTED:
                return "rechazado";
            case OWN_EVENT:
                return "propio-evento";
            case CANCELLED:
                return "cancelado";
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
        // de saber que cartel corresponde ("te diste de baja del evento",
        // "saliste de la lista de espera" o "cancelaste tu pedido pendiente").
        final RegistrationStatus status = registration.map(Registration::getStatus).orElse(null);
        registration.ifPresent(r -> registrationService.cancel(r.getId(), user.getEmail()));
        final String rsvpParam;
        if (status == RegistrationStatus.WAITLISTED) {
            rsvpParam = "baja-waitlist";
        } else if (status == RegistrationStatus.PENDING) {
            rsvpParam = "baja-pendiente";
        } else {
            rsvpParam = "baja";
        }
        return new ModelAndView("redirect:/eventos/" + id + "?rsvp=" + rsvpParam);
    }

    // Busca el evento y confirma que quien esta pidiendolo es su creador. Si no
    // existe, o existe pero no es el dueño, tira el mismo EventNotFoundException
    // en los dos casos -- no hay que confirmarle a nadie que un evento ajeno
    // existe. La usan "ver inscriptos" y "editar", que comparten este mismo chequeo.
    private Event eventForOwner(final long id, final Principal principal) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        if (!event.getCreatorEmail().equalsIgnoreCase(currentUser(principal).getEmail())) {
            throw new EventNotFoundException(id);
        }
        return event;
    }

    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id, final Principal principal) {
        final Event event = eventForOwner(id, principal);
        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", registrationService.findByEventId(id));
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.GET)
    public ModelAndView editarForm(@PathVariable final long id, final Principal principal) {
        final Event event = eventForOwner(id, principal);
        // Un evento cancelado ya no se edita: de vuelta a Mis eventos.
        if (event.isCancelled()) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        final List<Category> categories = categoryService.findAll();
        final ModelAndView mav = new ModelAndView("events/editar");
        mav.addObject("eventId", id);
        mav.addObject("categories", categories);
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", formFrom(event, categories));
        return mav;
    }

    private static EventForm formFrom(final Event event, final List<Category> categories) {
        final EventForm form = new EventForm();
        form.setTitulo(event.getTitle());
        form.setDescripcion(event.getDescription());
        form.setCategorias(categoriaFormsFor(event.getCategories(), categories));
        form.setFechaEvento(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm").format(event.getEventDate()));
        form.setDireccion(event.getAddress());
        form.setBarrio(event.getNeighborhood() == null ? null : event.getNeighborhood().name());
        form.setPrecio(formatDecimal(event.getPrice()));
        // "0" es el valor que usa el atajo "Sin limite" (quick-options.js lo
        // detecta por igualdad exacta de string) -- mismo criterio que publish().
        form.setCupoMaximo(event.getMaxCapacity() == null ? "0" : String.valueOf(event.getMaxCapacity()));
        form.setRequiereAprobacion(event.isRequiresApproval());
        form.setPrivado(event.isPrivate());
        if (event.isLocationPinned()) {
            form.setLatitud(String.valueOf(event.getLatitude()));
            form.setLongitud(String.valueOf(event.getLongitude()));
        }
        return form;
    }

    // 2500.00 -> "2500" (sin decimales de sobra), 1500.50 -> "1500.5". Los dos
    // formatos matchean el @Pattern de EventForm.precio. 0 es un caso aparte:
    // BigDecimal.ZERO.stripTrailingZeros() no es confiable entre versiones de
    // Java (bug historico que a veces devuelve "0E-...").
    private static String formatDecimal(final BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.POST)
    public ModelAndView editar(@PathVariable final long id, @Valid final EventForm form, final BindingResult errors,
                                @RequestParam(value = "images", required = false) final List<MultipartFile> images,
                                final Principal principal) {
        // Mismo chequeo que el GET: si no sos el dueño, ni siquiera llega a
        // mirar el resto del form.
        final Event existing = eventForOwner(id, principal);
        if (existing.isCancelled()) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        final Barrio parsedBarrio = parseBarrio(form.getBarrio(), errors);
        final List<EventCategoryInfo> categorias = selectedCategories(form, errors);

        if (errors.hasErrors()) {
            return editarFormWithErrors(id, form, errors, null);
        }

        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        dateFormat.setLenient(false);
        final Date parsedDate;
        try {
            parsedDate = dateFormat.parse(form.getFechaEvento());
        } catch (final ParseException e) {
            errors.rejectValue("fechaEvento", "Pattern", "La fecha y horario no tienen un formato valido");
            return editarFormWithErrors(id, form, errors, null);
        }
        if (parsedDate.before(new Date())) {
            errors.rejectValue("fechaEvento", "Future", "La fecha del evento tiene que ser futura");
            return editarFormWithErrors(id, form, errors, null);
        }

        final BigDecimal parsedPrice = new BigDecimal(form.getPrecio());
        final Integer parsedCapacity = "0".equals(form.getCupoMaximo()) ? null : Integer.valueOf(form.getCupoMaximo());

        // Si no se subio ninguna foto nueva, la galeria que ya tenia el evento
        // se mantiene entera; si se subio al menos una, reemplaza toda la
        // galeria vieja (no hay forma de sacar o agregar una foto suelta).
        final List<MultipartFile> nonEmptyImages = nonEmptyImages(images);
        if (nonEmptyImages.size() > MAX_IMAGES) {
            return editarFormWithErrors(id, form, errors, message("event.publish.tooManyImagesError"));
        }
        List<Long> imageIds = existing.getImageIds();
        if (!nonEmptyImages.isEmpty()) {
            try {
                imageIds = uploadImages(nonEmptyImages);
            } catch (final IOException e) {
                return editarFormWithErrors(id, form, errors, message("event.publish.imageReadError"));
            } catch (final IllegalArgumentException e) {
                return editarFormWithErrors(id, form, errors, message(e.getMessage()));
            }
        }

        final Event changes = new Event(form.getTitulo(), form.getDescripcion(), parsedDate, form.getDireccion(),
                parsedBarrio, imageIds, parsedPrice, parsedCapacity, existing.getCreatorEmail(), null);
        changes.setCategories(categorias);
        changes.setRequiresApproval(form.isRequiereAprobacion());
        changes.setVisibility(form.isPrivado() ? EventVisibility.PRIVATE : EventVisibility.PUBLIC);
        changes.setLatitude(parseCoordinate(form.getLatitud()));
        changes.setLongitude(parseCoordinate(form.getLongitud()));
        final EventUpdateResult result = eventService.update(id, changes, principal.getName());
        switch (result) {
            case CAPACITY_TOO_LOW:
                errors.rejectValue("cupoMaximo", "cupo.muyBajo",
                        "El cupo maximo no puede ser menor a los lugares ya ocupados");
                return editarFormWithErrors(id, form, errors, null);
            case CANCELLED:
                // Se cancelo justo entre medio: no se guardo nada.
                return new ModelAndView("redirect:/perfil/eventos");
            case NOT_FOUND:
                // Ya se confirmo el ownership arriba en eventForOwner(): solo se
                // llegaria aca si el evento se borro justo entre medio.
                throw new EventNotFoundException(id);
            case UPDATED:
            default:
                return new ModelAndView("redirect:/perfil/eventos");
        }
    }

    private ModelAndView editarFormWithErrors(final long id, final EventForm form, final BindingResult errors,
                                               final String imageError) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("events/editar");
        mav.addObject("eventId", id);
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        mav.addObject("categoriasError", firstCategoriasError(fieldErrors));
        mav.addObject("imageError", imageError);
        return mav;
    }

    // No hace falta un eventForOwner() previo aca (a diferencia de editar()):
    // no hay ningun dato del evento que usar mas alla del resultado mismo, y
    // EventService.cancel() ya distingue "no existe" de "no sos el dueño" con
    // el mismo NOT_FOUND de siempre.
    @RequestMapping(value = "/eventos/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelar(@PathVariable final long id, final Principal principal) {
        final EventCancelResult result = eventService.cancel(id, principal.getName());
        if (result == EventCancelResult.NOT_FOUND) {
            throw new EventNotFoundException(id);
        }
        // ALREADY_CANCELLED redirige igual que CANCELLED: no es un error que
        // el usuario tenga que ver, ya esta cancelado, que es justo lo que pidio.
        return new ModelAndView("redirect:/perfil/eventos");
    }

    // returnTo viaja como hidden en el form del corazon (paw:card/detail.jsp):
    // desde el listado tiene que volver al listado (con sus filtros), no
    // siempre al detalle. Sin el, o si viene manipulado a una URL ajena
    // (SafeRedirect), cae al detalle como default razonable.
    @RequestMapping(value = "/eventos/{id}/favorito", method = RequestMethod.POST)
    public ModelAndView addFavorite(@PathVariable final long id,
                                     @RequestParam(value = "returnTo", required = false) final String returnTo,
                                     final Principal principal) {
        // Mismo chequeo de acceso que detail() y rsvp(): si no podes ver el evento,
        // tampoco podes guardarlo (si no, "Mis favoritos" mostraria sus datos).
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        final Optional<Registration> myRegistration =
                registrationService.findByEventAndEmail(id, principal.getName());
        if (!canView(event, principal, myRegistration)) {
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
