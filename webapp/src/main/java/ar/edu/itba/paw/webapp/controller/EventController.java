package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventInvitationService;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReviewService;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.webapp.form.EventForm;
import ar.edu.itba.paw.webapp.form.EventSearchForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import ar.edu.itba.paw.webapp.util.Paging;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class EventController {

    private static final int FEATURED_COUNT = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final ReviewService reviewService;
    private final EventQuestionService eventQuestionService;
    private final EventAccessService accessChecker;
    private final EventInvitationService eventInvitationService;
    private final EventChatService eventChatService;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService,
                            final FavoriteService favoriteService, final ReviewService reviewService,
                            final EventQuestionService eventQuestionService, final EventAccessService accessChecker,
                            final EventInvitationService eventInvitationService,
                            final EventChatService eventChatService,
                            final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventQuestionService = eventQuestionService;
        this.accessChecker = accessChecker;
        this.eventInvitationService = eventInvitationService;
        this.eventChatService = eventChatService;
        this.messageSource = messageSource;
    }

    // Todos los mensajes que se muestran en pantalla (errores de validacion,
    // errores de negocio) se resuelven aca contra el idioma del pedido actual.
    private String message(final String code, final Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView home() {
        return new ModelAndView("redirect:/eventos");
    }

    // Todo el parseo de filtros (listas de ids, rango de precio/fecha, orden,
    // pagina) y las dos validaciones cruzadas (rango de precio/fecha invertido)
    // viven en EventSearchForm.
    @RequestMapping(value = "/eventos", method = RequestMethod.GET)
    public ModelAndView list(final EventSearchForm filters,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Map<String, String> filterErrors = new HashMap<>();
        if (filters.isPriceRangeInvalid()) {
            filterErrors.put("precioMax", message("event.list.priceRangeInvalid"));
        }
        if (filters.isDateRangeInvalid()) {
            filterErrors.put("fechaHasta", message("event.list.dateRangeInvalid"));
        }

        final PageResult<Event> result = eventService.search(filters.toCriteria());

        // Banner destacado
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
        mav.addObject("filterQuery", filters.toFilterQuery());
        mav.addObject("sortOptions", EventSort.values());
        mav.addObject("filterErrors", filterErrors);
        // Anonimo no ve el corazon de favorito.
        if (currentUser != null) {
            mav.addObject("favoriteEventIds", new HashSet<>(favoriteService.findEventIdsByUser(currentUser.getId())));
        }
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}", method = RequestMethod.GET)
    public ModelAndView detail(@PathVariable final long id,
                                @RequestParam(value = "preguntasPage", required = false) final String preguntasPage,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        // Privado y sin sesion: todavia no sabemos quien sos.
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
        accessChecker.requireViewable(event, currentUser, myRegistration);

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
            final RegistrationStatus myStatus = myRegistration.map(Registration::getStatus).orElse(null);
            mav.addObject("registered", myRegistration.isPresent()
                    && myStatus != RegistrationStatus.REJECTED);
            mav.addObject("waitlisted", myStatus == RegistrationStatus.WAITLISTED);
            mav.addObject("pendingApproval", myStatus == RegistrationStatus.PENDING);
            mav.addObject("rejected", myStatus == RegistrationStatus.REJECTED);
            // Grupo de chat: se ofrece "Unirme" a quien esta confirmado, y "Ir al chat" a quien ya
            // es miembro activo (aunque el evento ya haya terminado: queda de solo lectura).
            mav.addObject("chatConfirmed", myStatus == RegistrationStatus.CONFIRMED);
            mav.addObject("chatMember", eventChatService.isMember(id, currentUser.getId()));
            mav.addObject("chatClosed", event.isCancelled() || event.isFinished());
            // El creador no se anota a su propio evento.
            final boolean isOwner = Long.valueOf(currentUser.getId()).equals(event.getCreatorId());
            mav.addObject("isOwner", isOwner);
            // Un admin puede VER un evento privado para moderarlo (ver EventAccessService.canView()),
            // pero no inscribirse si nadie lo invito: register() lo trata como un id inexistente.
            // La pantalla no le ofrece un boton que terminaria en un 404.
            mav.addObject("viewOnly", event.isPrivate() && !isOwner && !myRegistration.isPresent()
                    && !eventInvitationService.isInvited(id, currentUser.getId()));
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
            // Una sola consulta para todos los eventos reseñados (findByIds()),
            // en vez de un findById() por cada reseña.
            final List<Long> eventIds = creatorReviews.stream().map(Review::getEventId).distinct().collect(Collectors.toList());
            final Map<Long, Event> reviewEventsById = eventService.findByIds(eventIds).stream()
                    .collect(Collectors.toMap(Event::getId, e -> e));
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

    // Toda la validacion de forma y de negocio ya corrio con @Valid, via los validators
    // custom de EventForm
    @RequestMapping(value = "/eventos/publicar", method = RequestMethod.POST)
    public ModelAndView publish(@Valid final EventForm form, final BindingResult errors,
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (errors.hasErrors()) {
            return publishFormWithErrors(form, errors, null);
        }

        final List<NewImageData> newImages;
        try {
            newImages = readImages(form.getImages());
        } catch (final IOException e) {
            return publishFormWithErrors(form, errors, message("event.publish.imageReadError"));
        }

        final EventDTO dto = form.toDTO();
        dto.setNewImages(newImages);
        // El creador es quien esta logueado.
        dto.setCreatorId(currentUser.getId());
        dto.setCreatorEmail(currentUser.getEmail());

        // Subir las imagenes y crear el evento pasa todo DENTRO de la misma
        // transaccion, adentro de eventService.create(): una imagen invalida
        // o una categoria inexistente deshacen las dos cosas juntas.
        final Event created;
        try {
            created = eventService.create(dto);
        } catch (final InvalidImageException e) {
            return publishFormWithErrors(form, errors, message(e.getMessageKey()));
        } catch (final InvalidEventException e) {
            return publishFormWithErrors(form, errors, message(e.getMessageKey()));
        }
        // Privado: a invitar. Publico: al detalle del evento.
        return created.isPrivate()
                ? new ModelAndView("redirect:/perfil/eventos/" + created.getId() + "/invitar")
                : new ModelAndView("redirect:/eventos/" + created.getId());
    }

    // Lee los bytes de cada archivo, sin guardar nada todavia.
    private static List<NewImageData> readImages(final List<MultipartFile> images) throws IOException {
        final List<NewImageData> result = new ArrayList<>();
        for (final MultipartFile image : images) {
            result.add(new NewImageData(image.getBytes(), image.getContentType(), image.getOriginalFilename()));
        }
        return result;
    }

    private ModelAndView publishFormWithErrors(final EventForm form, final BindingResult errors,
                                                final String imageError) {
        final Map<String, String> fieldErrors = FormErrors.of(errors, messageSource);
        final ModelAndView mav = new ModelAndView("events/publish");
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        mav.addObject("categoriasError", firstCategoriasError(fieldErrors));
        mav.addObject("imageError", imageError != null ? imageError : fieldErrors.get("images"));
        return mav;
    }

    private static String firstCategoriasError(final Map<String, String> fieldErrors) {
        if (fieldErrors.containsKey("categories")) {
            return fieldErrors.get("categories");
        }
        return fieldErrors.entrySet().stream()
                .filter(e -> e.getKey().startsWith("categories["))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = accessChecker.getOwnedEvent(id, currentUser);
        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", registrationService.findByEventId(id));
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.GET)
    public ModelAndView editarForm(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = accessChecker.getEditableEvent(id, currentUser);
        final List<Category> categories = categoryService.findAll();
        final ModelAndView mav = new ModelAndView("events/editar");
        mav.addObject("eventId", id);
        mav.addObject("categories", categories);
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", EventForm.from(event, categories));
        return mav;
    }

    // Toda la validacion (fecha, barrio, categorias, cantidad de imagenes) ya
    // corrio con @Valid, (tiene validaciones con srvice).
    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.POST)
    public ModelAndView editar(@PathVariable final long id, @Valid final EventForm form, final BindingResult errors,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        accessChecker.getEditableEvent(id, currentUser);
        if (errors.hasErrors()) {
            return editarFormWithErrors(id, form, errors, null);
        }

        // Si no se subio ninguna foto nueva (newImages vacio), eventService.update()
        // mantiene la galeria que el evento ya tenia; si se subio al menos una,
        // las sube y reemplaza toda la galeria vieja
        final List<NewImageData> newImages;
        try {
            newImages = readImages(form.getImages());
        } catch (final IOException e) {
            return editarFormWithErrors(id, form, errors, message("event.publish.imageReadError"));
        }

        final EventDTO dto = form.toDTO();
        dto.setNewImages(newImages);
        try {
            eventService.update(id, dto, currentUser.getId());
        } catch (final InvalidImageException e) {
            return editarFormWithErrors(id, form, errors, message(e.getMessageKey()));
        } catch (final InvalidEventException e) {
            if (e.getField() != null) {
                errors.rejectValue(e.getField(), e.getMessageKey());
                return editarFormWithErrors(id, form, errors, null);
            }
            return editarFormWithErrors(id, form, errors, message(e.getMessageKey()));
        }
        return new ModelAndView("redirect:/perfil/eventos");
    }

    private ModelAndView editarFormWithErrors(final long id, final EventForm form, final BindingResult errors,
                                               final String imageError) {
        final Map<String, String> fieldErrors = FormErrors.of(errors, messageSource);
        final ModelAndView mav = new ModelAndView("events/editar");
        mav.addObject("eventId", id);
        mav.addObject("categories", categoryService.findAll());
        mav.addObject("barrios", Barrio.values());
        mav.addObject("eventForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        mav.addObject("categoriasError", firstCategoriasError(fieldErrors));
        mav.addObject("imageError", imageError != null ? imageError : fieldErrors.get("images"));
        return mav;
    }

    @RequestMapping(value = "/eventos/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelar(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        eventService.cancel(id, currentUser.getId());
        return new ModelAndView("redirect:/perfil/eventos");
    }
}
