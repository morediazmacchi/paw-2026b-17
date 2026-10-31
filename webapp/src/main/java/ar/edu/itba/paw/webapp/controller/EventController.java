package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.CreatorReputation;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventParticipation;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventSimilarityService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final EventChatService eventChatService;
    private final EventSimilarityService eventSimilarityService;
    private final MessageSource messageSource;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService,
                            final FavoriteService favoriteService, final ReviewService reviewService,
                            final EventQuestionService eventQuestionService, final EventAccessService accessChecker,
                            final EventChatService eventChatService,
                            final EventSimilarityService eventSimilarityService,
                            final MessageSource messageSource) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.reviewService = reviewService;
        this.eventQuestionService = eventQuestionService;
        this.accessChecker = accessChecker;
        this.eventChatService = eventChatService;
        this.eventSimilarityService = eventSimilarityService;
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

        // Quien navega: sus propios eventos no se listan (el banner si los muestra), y si ya asistio a
        // algo el orden por defecto pasa a ser RECOMENDADOS (ver EventSimilarityService).
        final Long viewerId = currentUser == null ? null : currentUser.getId();
        final boolean hasHistory = viewerId != null && eventSimilarityService.hasAttendanceHistory(viewerId);
        final EventSort sort = effectiveSort(filters, hasHistory);
        final EventSearchCriteria criteria = filters.toCriteria();
        criteria.setViewerId(viewerId);
        criteria.setSort(sort);
        final PageResult<Event> result = eventService.search(criteria);

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
        mav.addObject("sort", sort.name());
        mav.addObject("filterQuery", filters.toFilterQuery());
        mav.addObject("sortOptions", sortOptions(hasHistory));
        mav.addObject("filterErrors", filterErrors);
        // Anonimo no ve el corazon de favorito ni las etiquetas de estado.
        if (currentUser != null) {
            mav.addObject("favoriteEventIds", new HashSet<>(favoriteService.findEventIdsByUser(currentUser.getId())));
            // Estado de la inscripcion propia en cada evento de la pagina: una card sin etiqueta
            // significa que la persona nunca se inscribio (o se dio de baja).
            mav.addObject("registrationStatuses", registrationService.findStatusesByEventIds(
                    currentUser.getId(), result.getContent().stream().map(Event::getId).collect(Collectors.toList())));
        }
        return mav;
    }

    // El orden que pidio la persona; si no pidio ninguno, RECOMENDADOS para quien ya asistio a
    // algo y FECHA_ASC para el resto. RECOMENDADOS pedido a mano sin historial no dice nada, asi
    // que cae a FECHA_ASC.
    private static EventSort effectiveSort(final EventSearchForm filters, final boolean hasHistory) {
        if (!filters.isSortExplicit()) {
            return hasHistory ? EventSort.RECOMENDADOS : EventSort.FECHA_ASC;
        }
        final EventSort requested = filters.getParsedSort();
        return requested == EventSort.RECOMENDADOS && !hasHistory ? EventSort.FECHA_ASC : requested;
    }

    // RECOMENDADOS solo se ofrece a quien tiene historial: sin el, el orden seria identico a
    // "Mas proximos" y la opcion no diria nada.
    private static List<EventSort> sortOptions(final boolean hasHistory) {
        return Arrays.stream(EventSort.values())
                .filter(option -> hasHistory || option != EventSort.RECOMENDADOS)
                .collect(Collectors.toList());
    }

    // Quien puede ver el evento lo decide @PreAuthorize antes de entrar (ver EventSecurity.canView):
    // un evento inexistente o que no se puede ver nunca llega aca. Un anonimo que no puede verlo
    // va al login (ErrorAdvice.accessDenied), un usuario con sesion ve el 404.
    @PreAuthorize("@eventSecurity.canView(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}", method = RequestMethod.GET)
    public ModelAndView detail(@PathVariable final long id,
                                @RequestParam(value = "preguntasPage", required = false) final String preguntasPage,
                                @RequestParam(value = "resenasPage", required = false) final String resenasPage,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        final ModelAndView mav = new ModelAndView("events/detail");
        mav.addObject("event", event);
        // Quien fue invitado a un evento privado tiene su lugar reservado: para esa
        // persona el evento no esta "lleno" aunque el cupo ya este contado entero.
        mav.addObject("full", currentUser != null
                ? registrationService.isFullFor(id, currentUser.getId())
                : registrationService.isFull(id));
        mav.addObject("availableSpots", registrationService.availableSpots(id));
        if (currentUser != null) {
            // La relacion de esta persona con el evento ya viene resuelta por el service.
            final EventParticipation participation = registrationService.findParticipation(id, currentUser.getId());
            mav.addObject("favorite", favoriteService.isFavorite(currentUser.getId(), id));
            mav.addObject("registered", participation.isRegistered());
            mav.addObject("waitlisted", participation.isWaitlisted());
            mav.addObject("pendingApproval", participation.isPendingApproval());
            mav.addObject("rejected", participation.isRejected());
            // Grupo de chat: se ofrece "Unirme" a quien esta confirmado, y "Ir al chat" a quien ya
            // es miembro activo (aunque el evento ya haya terminado: queda de solo lectura).
            mav.addObject("chatConfirmed", participation.isChatConfirmed());
            mav.addObject("chatMember", eventChatService.isMember(id, currentUser.getId()));
            mav.addObject("isOwner", participation.isOwner());
            mav.addObject("adminReviewOnly", participation.isAdminReviewOnly());
            mav.addObject("viewOnly", participation.isViewOnly());
        }
        if (event.getCreatorId() != null) {
            addCreatorReputation(mav, event.getCreatorId(), Paging.parsePage(resenasPage));
        }
        addRelatedEvents(mav, event, currentUser);
        final PageResult<EventQuestion> questions = eventQuestionService.findPage(id, Paging.parsePage(preguntasPage));
        mav.addObject("questions", questions.getContent());
        mav.addObject("questionsPage", questions.getPage());
        mav.addObject("questionsTotalPages", questions.getTotalPages());
        return mav;
    }

    // "Eventos que te pueden interesar": solo en el detalle de un evento publico y aprobado (uno
    // privado, pendiente o rechazado no sugiere nada). Es evento contra evento: no depende de
    // quien mira, salvo que sus propios eventos no se sugieren.
    private void addRelatedEvents(final ModelAndView mav, final Event event, final User currentUser) {
        if (event.isPrivate() || !event.isApproved()) {
            return;
        }
        final List<Event> related = eventSimilarityService.findSimilar(
                event.getId(), currentUser == null ? null : currentUser.getId());
        mav.addObject("relatedEvents", related);
        if (currentUser != null && !related.isEmpty()) {
            mav.addObject("relatedStatuses", registrationService.findStatusesByEventIds(
                    currentUser.getId(), related.stream().map(Event::getId).collect(Collectors.toList())));
        }
    }

    // Puntaje del promotor (todas sus reseñas, de todos sus eventos: COUNT y AVG en la base) +
    // UNA pagina de reseñas para el popup "Ver reseñas de este promotor", con la mini info del
    // evento reseñado en cada una (reviewEventsById). Los links del popup piden otra pagina
    // con ?resenasPage=N, igual que las preguntas con ?preguntasPage=N.
    private void addCreatorReputation(final ModelAndView mav, final long creatorId, final int reviewsPage) {
        final CreatorReputation reputation = reviewService.findCreatorReputation(creatorId, reviewsPage);
        mav.addObject("creatorReviewCount", reputation.getReviewCount());
        if (reputation.hasReviews()) {
            mav.addObject("creatorAverageRating", reputation.getAverageRating());
            mav.addObject("creatorReviews", reputation.getReviews());
            mav.addObject("reviewEventsById", reputation.getEventsById());
            mav.addObject("creatorReviewsPage", reputation.getPage());
            mav.addObject("creatorReviewsTotalPages", reputation.getTotalPages());
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
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                 final RedirectAttributes redirectAttributes) {
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
        // Todo evento nuevo arranca pendiente de aprobacion (ver EventApprovalStatus):
        // se avisa con un popup en la pantalla a la que se redirige, sea cual sea.
        redirectAttributes.addFlashAttribute("justPublished", true);
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

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/inscriptos", method = RequestMethod.GET)
    public ModelAndView inscriptos(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", registrationService.findByEventId(id));
        return mav;
    }

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.GET)
    public ModelAndView editarForm(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = accessChecker.getEditableEvent(id);
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
    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/editar", method = RequestMethod.POST)
    public ModelAndView editar(@PathVariable final long id, @Valid final EventForm form, final BindingResult errors,
                                @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        accessChecker.getEditableEvent(id);
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

    @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelar(@PathVariable final long id, @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        eventService.cancel(id, currentUser.getId());
        return new ModelAndView("redirect:/perfil/eventos");
    }
}
