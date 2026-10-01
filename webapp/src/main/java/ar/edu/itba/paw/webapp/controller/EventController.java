package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventCancelResult;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.EventUpdateResult;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.webapp.exception.EventNotFoundException;
import ar.edu.itba.paw.webapp.form.EventForm;
import ar.edu.itba.paw.webapp.form.EventSearchForm;
import ar.edu.itba.paw.webapp.util.Paging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// Listar, ver el detalle, publicar, editar y cancelar un evento. Las acciones
// de un visitante (RSVP, favoritos, reportar) viven en
// EventRegistrationController, y las preguntas en EventQuestionController --
// el chequeo de acceso a eventos privados que comparten los tres vive en
// EventAccessChecker.
@Controller
public class EventController {

    private static final int PAGE_SIZE = 12;
    private static final int FEATURED_COUNT = 5;
    // Ver el comentario de multipart-config en web.xml: sus limites estan
    // calculados en base a este numero (5 fotos de 5 MB cada una, tope que
    // impone ImageServiceImpl). Publico porque EventForm.images
    // (@ValidImageCount) tambien lo referencia.
    public static final int MAX_IMAGES = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final ImageService imageService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final EventQuestionService eventQuestionService;
    private final EventAccessChecker accessChecker;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService, final ImageService imageService,
                            final FavoriteService favoriteService, final ReviewService reviewService,
                            final EventQuestionService eventQuestionService, final EventAccessChecker accessChecker,
                            final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.imageService = imageService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventQuestionService = eventQuestionService;
        this.accessChecker = accessChecker;
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

    // Todo el parseo de filtros (listas de ids, rango de precio/fecha, orden,
    // pagina) y las dos validaciones cruzadas (rango de precio/fecha invertido)
    // viven en EventSearchForm: un filtro invalido no rechaza la busqueda, solo
    // se ignora y se avisa, asi que no es un caso de @Valid + BindingResult.
    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(final EventSearchForm filters,
                              @RequestParam(value = "rsvp", required = false) final String rsvp,
                              @ModelAttribute("currentUser") final User currentUser) {
        final Map<String, String> filterErrors = new HashMap<>();
        if (filters.isPriceRangeInvalid()) {
            filterErrors.put("precioMax", message("event.list.priceRangeInvalid"));
        }
        if (filters.isDateRangeInvalid()) {
            filterErrors.put("fechaHasta", message("event.list.dateRangeInvalid"));
        }

        final PageResult<Event> result = eventService.search(filters.toCriteria(PAGE_SIZE));

        // Banner destacado: los que un admin eligio (ver AdminController.destacar()),
        // mas reciente primero. Antes eran "los proximos eventos por fecha" fijo;
        // ahora es curado a mano, sin los filtros de arriba.
        final List<Event> featured = eventService.findFeatured(FEATURED_COUNT);

        final ModelAndView mav = new ModelAndView("events/list");
        mav.addObject("events", result.getContent());
        mav.addObject("featuredEvents", featured);
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("pageNumbers", Paging.buildPageNumbers(result.getPage(), result.getTotalPages()));
        mav.addObject("totalElements", result.getTotalElements());
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("selectedCategorias", filters.getSelectedCategoryIds());
        mav.addObject("precioMin", filters.getPrecioMin());
        mav.addObject("precioMax", filters.getPrecioMax());
        mav.addObject("fechaDesde", filters.getFechaDesde());
        mav.addObject("fechaHasta", filters.getFechaHasta());
        mav.addObject("selectedBarrios", filters.getSelectedBarrios());
        mav.addObject("puntajeMin", filters.getPuntajeMin());
        mav.addObject("pocosCupos", filters.getPocosCupos());
        mav.addObject("agotados", filters.getAgotados());
        mav.addObject("hasActiveFilters", filters.hasActiveFilters());
        mav.addObject("sort", filters.getParsedSort().name());
        mav.addObject("sortOptions", EventSort.values());
        mav.addObject("filterErrors", filterErrors);
        mav.addObject("rsvp", rsvp);
        // Anonimo no ve el corazon de favorito (paw:card lo esconde con
        // sec:authorize), asi que ni consultamos: nadie va a leer este set.
        if (currentUser != null) {
            mav.addObject("favoriteEventIds", new HashSet<>(favoriteService.findEventIdsByUser(currentUser.getId())));
        }
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}", method = RequestMethod.GET)
    public ModelAndView detail(@PathVariable final long id,
                                @RequestParam(value = "preguntasPage", required = false) final String preguntasPage,
                                @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        // Privado y sin sesion: todavia no sabemos quien sos. Al login, y de
        // vuelta aca (WebAuthConfig.loginSuccessHandler) para chequear si estas invitado.
        if (event.isPrivate() && currentUser == null) {
            return new ModelAndView("redirect:/login?redirect=/eventos/" + id);
        }
        // Para un evento privado, la inscripcion propia (si existe) no es solo
        // dato para la tarjeta de RSVP: es tambien la prueba de que quien pide
        // esto tiene algo que ver con el evento. Por eso se busca ANTES del
        // chequeo de acceso, en vez de mas abajo como antes.
        final Optional<Registration> myRegistration = currentUser == null
                ? Optional.empty()
                : registrationService.findByEventAndUserId(id, currentUser.getId());
        if (!accessChecker.canView(event, currentUser, myRegistration)) {
            // Mismo 404 que un id inexistente: no hay que revelar que el
            // evento existe pero que no es para vos.
            throw new EventNotFoundException(id);
        }

        final ModelAndView mav = new ModelAndView("events/detail");
        mav.addObject("event", event);
        // Quien fue invitado a un evento privado tiene su lugar reservado: para esa
        // persona el evento no esta "lleno" aunque el cupo ya este contado entero.
        mav.addObject("full", currentUser != null
                ? registrationService.isFullFor(id, currentUser.getId())
                : registrationService.isFull(id));
        mav.addObject("finished", event.isFinished());
        mav.addObject("availableSpots", registrationService.availableSpots(id));
        if (currentUser != null) {
            mav.addObject("favorite", favoriteService.isFavorite(currentUser.getId(), id));
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
            mav.addObject("isOwner", Long.valueOf(currentUser.getId()).equals(event.getCreatorId()));
        }
        if (event.getCreatorId() != null) {
            addCreatorReputation(mav, event.getCreatorId());
        }
        final PageResult<EventQuestion> questions = eventQuestionService.findPage(id, Paging.parsePage(preguntasPage));
        mav.addObject("questions", questions.getContent());
        mav.addObject("questionsPage", questions.getPage());
        mav.addObject("questionsTotalPages", questions.getTotalPages());
        mav.addObject("questionsOpen", event.isOpenForQuestions());
        return mav;
    }

    // Puntaje del promotor (todas sus reseñas, de todos sus eventos) + la lista
    // completa para el popup "Ver reseñas de este promotor", con la mini info
    // del evento reseñado en cada una (reviewEventsById).
    private void addCreatorReputation(final ModelAndView mav, final long creatorId) {
        final List<Review> creatorReviews = reviewService.findByCreatorId(creatorId);
        mav.addObject("creatorReviewCount", creatorReviews.size());
        if (!creatorReviews.isEmpty()) {
            mav.addObject("creatorAverageRating", reviewService.findCreatorAverageRating(creatorId));
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
        mav.addObject("eventForm", EventForm.empty(categories));
        if (imageError != null) {
            mav.addObject("imageError", message("event.publish.imageTooLargeError"));
        }
        return mav;
    }

    // Toda la validacion de forma y de negocio (fecha valida/futura/no muy
    // lejana, barrio valido, al menos una categoria y que exista de verdad,
    // no mas de MAX_IMAGES fotos) ya corrio con @Valid, via los validators
    // custom de EventForm (ver ar.edu.itba.paw.webapp.validation). Si eso
    // paso, form.toDTO() no puede fallar: arma el DTO parseado y listo para
    // el service, sin que este metodo tenga que parsear ni rechazar nada el
    // mismo. Lo unico que queda aca es subir las imagenes: es una accion con
    // efecto secundario (escribe en disco/base), no una validacion, asi que
    // no puede vivir en un ConstraintValidator.
    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(@Valid final EventForm form, final BindingResult errors,
                                 @ModelAttribute("currentUser") final User currentUser) {
        if (errors.hasErrors()) {
            return publishFormWithErrors(form, errors, null);
        }

        final List<Long> imageIds;
        try {
            imageIds = uploadImages(nonEmptyImages(form.getImages()));
        } catch (final IOException e) {
            return publishFormWithErrors(form, errors, message("event.publish.imageReadError"));
        } catch (final IllegalArgumentException e) {
            return publishFormWithErrors(form, errors, message(e.getMessage()));
        }

        final EventDTO dto = form.toDTO();
        dto.setImageIds(imageIds);
        // El creador es quien esta logueado, nunca un campo del form: si viniera del
        // form, cualquiera podria publicar un evento a nombre de otro email.
        dto.setCreatorId(currentUser.getId());
        dto.setCreatorEmail(currentUser.getEmail());

        final Event created = eventService.create(dto);
        // Privado: derecho a invitar, para no tener que ir hasta "Mis eventos" a buscarlo.
        if (created.isPrivate()) {
            return new ModelAndView("redirect:/perfil/eventos/" + created.getId() + "/invitar");
        }
        return new ModelAndView("redirect:/eventos");
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
        // "Demasiadas imagenes" ahora es un error de @Valid (ver EventForm.images),
        // asi que cae en fieldErrors.images en vez de venir explicito por parametro,
        // como los errores de la subida en si (que son una accion, no una validacion).
        mav.addObject("imageError", imageError != null ? imageError : fieldErrors.get("images"));
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

    // Busca el evento y confirma que quien esta pidiendolo es su creador. Si no
    // existe, o existe pero no es el dueño, tira el mismo EventNotFoundException
    // en los dos casos -- no hay que confirmarle a nadie que un evento ajeno
    // existe. La usan "ver inscriptos" y "editar", que comparten este mismo chequeo.
    private Event eventForOwner(final long id, final User user) {
        final Event event = eventService.findById(id).orElseThrow(() -> new EventNotFoundException(id));
        if (!Long.valueOf(user.getId()).equals(event.getCreatorId())) {
            throw new EventNotFoundException(id);
        }
        return event;
    }

    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id, @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventForOwner(id, currentUser);
        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", registrationService.findByEventId(id));
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.GET)
    public ModelAndView editarForm(@PathVariable final long id, @ModelAttribute("currentUser") final User currentUser) {
        final Event event = eventForOwner(id, currentUser);
        // Un evento cancelado ya no se edita: de vuelta a Mis eventos.
        if (event.isCancelled()) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        final List<Category> categories = categoryService.findAll();
        final ModelAndView mav = new ModelAndView("events/editar");
        mav.addObject("eventId", id);
        mav.addObject("categories", categories);
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", EventForm.from(event, categories));
        return mav;
    }

    // Toda la validacion (fecha, barrio, categorias, cantidad de imagenes) ya
    // corrio con @Valid, via los mismos validators custom de EventForm que usa
    // publish() -- es el mismo form. Lo unico que no se puede prevenir mirando
    // el form es lo que depende del estado actual del evento en la base
    // (cupo ya ocupado, cancelado o borrado justo entre medio): eso solo lo
    // sabe el service en el momento de escribir, por eso EventUpdateResult +
    // switch siguen siendo necesarios aca (a diferencia de publish()).
    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.POST)
    public ModelAndView editar(@PathVariable final long id, @Valid final EventForm form, final BindingResult errors,
                                @ModelAttribute("currentUser") final User currentUser) {
        // Mismo chequeo que el GET: si no sos el dueño, ni siquiera llega a
        // mirar el resto del form.
        final Event existing = eventForOwner(id, currentUser);
        if (existing.isCancelled()) {
            return new ModelAndView("redirect:/perfil/eventos");
        }
        if (errors.hasErrors()) {
            return editarFormWithErrors(id, form, errors, null);
        }

        // Si no se subio ninguna foto nueva, la galeria que ya tenia el evento
        // se mantiene entera; si se subio al menos una, reemplaza toda la
        // galeria vieja (no hay forma de sacar o agregar una foto suelta).
        final List<MultipartFile> nonEmptyImages = nonEmptyImages(form.getImages());
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

        final EventDTO dto = form.toDTO();
        dto.setImageIds(imageIds);
        // Quien creo el evento no cambia al editarlo.
        final Event changes = dto.toEvent(existing.getCreatorEmail());
        final EventUpdateResult result = eventService.update(id, changes, currentUser.getId());
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
        // "Demasiadas imagenes" ahora es un error de @Valid (ver EventForm.images),
        // asi que cae en fieldErrors.images en vez de venir explicito por parametro,
        // como los errores de la subida en si (que son una accion, no una validacion).
        mav.addObject("imageError", imageError != null ? imageError : fieldErrors.get("images"));
        return mav;
    }

    // No hace falta un eventForOwner() previo aca (a diferencia de editar()):
    // no hay ningun dato del evento que usar mas alla del resultado mismo, y
    // EventService.cancel() ya distingue "no existe" de "no sos el dueño" con
    // el mismo NOT_FOUND de siempre.
    @RequestMapping(value = "/eventos/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelar(@PathVariable final long id, @ModelAttribute("currentUser") final User currentUser) {
        final EventCancelResult result = eventService.cancel(id, currentUser.getId());
        if (result == EventCancelResult.NOT_FOUND) {
            throw new EventNotFoundException(id);
        }
        // ALREADY_CANCELLED redirige igual que CANCELLED: no es un error que
        // el usuario tenga que ver, ya esta cancelado, que es justo lo que pidio.
        return new ModelAndView("redirect:/perfil/eventos");
    }
}
