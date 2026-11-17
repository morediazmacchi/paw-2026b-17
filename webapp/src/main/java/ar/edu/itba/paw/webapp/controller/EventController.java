package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.dto.NewImageData;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.services.CreatorReputation;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventCreation;
import ar.edu.itba.paw.services.EventDetail;
import ar.edu.itba.paw.services.EventDetailService;
import ar.edu.itba.paw.services.EventParticipation;
import ar.edu.itba.paw.services.EventListing;
import ar.edu.itba.paw.services.EventListingService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.FollowService;
import ar.edu.itba.paw.services.PageResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.SaveSearchResult;
import ar.edu.itba.paw.services.SavedSearchService;
import ar.edu.itba.paw.model.exception.InvalidEventException;
import ar.edu.itba.paw.model.exception.InvalidImageException;
import ar.edu.itba.paw.model.Registration;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class EventController {

    private static final int FEATURED_COUNT = 5;

    private final EventService eventService;
    private final CategoryService categoryService;
    private final RegistrationService registrationService;
    private final EventListingService eventListingService;
    private final EventDetailService eventDetailService;
    private final EventAccessService accessChecker;
    private final SavedSearchService savedSearchService;
    private final MessageSource messageSource;
    private final FollowService followService;

    @Autowired
    public EventController(final EventService eventService, final CategoryService categoryService,
                            final RegistrationService registrationService,
                            final EventListingService eventListingService, final EventDetailService eventDetailService,
                            final EventAccessService accessChecker, final SavedSearchService savedSearchService,
                            final MessageSource messageSource, final FollowService followService) {
        this.eventService = eventService;
        this.categoryService = categoryService;
        this.registrationService = registrationService;
        this.eventListingService = eventListingService;
        this.eventDetailService = eventDetailService;
        this.accessChecker = accessChecker;
        this.savedSearchService = savedSearchService;
        this.messageSource = messageSource;
        this.followService = followService;
    }

    // Todos los mensajes que se muestran en pantalla (errores de validacion,
    // errores de negocio) se resuelven aca contra el idioma del pedido actual.
    private String message(final String code, final Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    // El resultado de "Guardar busqueda" vuelve en la URL (?busqueda=SAVED). Cualquier otro valor se ignora: la URL
    // la puede tocar cualquiera, y el JSP arma una clave de mensaje con esto.
    private static SaveSearchResult parseSaveSearchResult(final String value) {
        for (final SaveSearchResult result : SaveSearchResult.values()) {
            if (result.name().equals(value)) {
                return result;
            }
        }
        return null;
    }

    // "Guardar busqueda" (watchlist) de /eventos: guarda los filtros puestos para avisar de los eventos nuevos que
    // los cumplan. Exige sesion (WebAuthConfig: todo lo que no es publico la pide) y llama a UN solo metodo del
    // service (correccion 06). Vuelve al listado con los mismos filtros y el resultado.
    @RequestMapping(value = "/eventos/busquedas", method = RequestMethod.POST)
    public ModelAndView saveSearch(final EventSearchForm filters,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final SaveSearchResult result = savedSearchService.save(currentUser.getId(), filters.toCriteria());
        return new ModelAndView("redirect:/eventos?" + filters.toFilterQuery() + "busqueda=" + result.name());
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
                              @RequestParam(value = "busqueda", required = false) final String saveSearchResult,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Map<String, String> filterErrors = new HashMap<>();
        if (filters.isPriceRangeInvalid()) {
            filterErrors.put("precioMax", message("event.list.priceRangeInvalid"));
        }
        if (filters.isDateRangeInvalid()) {
            filterErrors.put("fechaHasta", message("event.list.dateRangeInvalid"));
        }

        // Quien navega: sus propios eventos no se listan (el banner si los muestra). El orden que se aplica
        // y los que se ofrecen los decide el service (ver EventService.search()).
        final EventSearchCriteria criteria = filters.toCriteria();
        criteria.setViewerId(currentUser == null ? null : currentUser.getId());
        // UN caso de uso del service arma toda la pantalla (resultados, banner, categorias y, con sesion, favoritos,
        // inscripciones y si estos filtros ya estan guardados): el controller solo lo pone en el model.
        final EventListing listing = eventListingService.getListing(criteria, FEATURED_COUNT, filters.hasActiveFilters());
        final PageResult<Event> result = listing.getPage();

        final ModelAndView mav = new ModelAndView("events/list");
        mav.addObject("events", result.getContent());
        mav.addObject("featuredEvents", listing.getFeatured());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
        mav.addObject("pageNumbers", Paging.buildPageNumbers(result.getPage(), result.getTotalPages()));
        mav.addObject("totalElements", result.getTotalElements());
        mav.addObject("categories", listing.getCategories());
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
        // La barra de busqueda: el texto para volver a mostrarlo, y si de verdad hay algo que buscar.
        mav.addObject("q", filters.getSearchText());
        mav.addObject("hasSearchText", filters.hasSearchText());
        mav.addObject("sort", listing.getEffectiveSort().name());
        mav.addObject("filterQuery", filters.toFilterQuery());
        mav.addObject("sortOptions", listing.getAvailableSorts());
        mav.addObject("filterErrors", filterErrors);
        // Sin sesion estos tres vienen vacios: la card no muestra corazon ni etiqueta de estado.
        mav.addObject("favoriteEventIds", listing.getFavoriteEventIds());
        mav.addObject("registrationStatuses", listing.getRegistrationStatuses());
        mav.addObject("searchSaved", listing.isSearchSaved());
        if (currentUser != null) {
            mav.addObject("saveSearchResult", parseSaveSearchResult(saveSearchResult));
        }
        return mav;
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
        final EventDetail detail = eventDetailService.getDetail(id, currentUser == null ? null : currentUser.getId(),
                Paging.parsePage(preguntasPage), Paging.parsePage(resenasPage));
        final ModelAndView mav = new ModelAndView("events/detail");
        mav.addObject("event", detail.getEvent());
        mav.addObject("availableSpots", detail.getAvailableSpots());
        // Que se le ofrece (inscribirse, darse de baja, nada...) y el acceso al chat ya vienen decididos por el
        // service: la vista solo hace un switch sobre estos valores.
        mav.addObject("action", detail.getAction());
        mav.addObject("chatAccess", detail.getChatAccess());
        mav.addObject("canUnregister", detail.isCanUnregister());
        mav.addObject("canAsk", detail.isCanAsk());
        mav.addObject("canAnswer", detail.isCanAnswer());
        mav.addObject("canReport", detail.isCanReport());
        final EventParticipation participation = detail.getParticipation();
        if (participation != null) {
            // La relacion de esta persona con el evento ya viene resuelta por el service.
            mav.addObject("favorite", detail.isFavorite());
            mav.addObject("waitlisted", participation.isWaitlisted());
            mav.addObject("pendingApproval", participation.isPendingApproval());
        }
        // Quien organiza, para mostrar su nombre con un link a su perfil publico (null en eventos sin organizador).
        mav.addObject("organizerId", detail.getOrganizerId());
        mav.addObject("organizerName", detail.getOrganizerName());
        addCreatorReputation(mav, detail.getCreatorReputation());
        mav.addObject("canFollowCreator", detail.isCanFollowCreator());
        mav.addObject("followingCreator", detail.isFollowingCreator());
        // "Eventos que te pueden interesar": vacio si el evento es privado o no esta aprobado.
        mav.addObject("relatedEvents", detail.getRelatedEvents());
        mav.addObject("relatedStatuses", detail.getRelatedStatuses());
        mav.addObject("relatedFavoriteIds", detail.getRelatedFavoriteIds());
        final PageResult<EventQuestion> questions = detail.getQuestions();
        mav.addObject("questions", questions.getContent());
        mav.addObject("questionsPage", questions.getPage());
        mav.addObject("questionsTotalPages", questions.getTotalPages());
        return mav;
    }

    // Puntaje del promotor (todas sus reseñas, de todos sus eventos: COUNT y AVG en la base) +
    // UNA pagina de reseñas para el popup "Ver reseñas de este promotor", con la mini info del
    // evento reseñado en cada una (reviewEventsById). Los links del popup piden otra pagina
    // con ?resenasPage=N, igual que las preguntas con ?preguntasPage=N. Un evento sin organizador
    // cargado no tiene reputacion (null).
    private void addCreatorReputation(final ModelAndView mav, final CreatorReputation reputation) {
        if (reputation == null) {
            return;
        }
        mav.addObject("creatorReviewCount", reputation.getReviewCount());
        if (reputation.hasReviews()) {
            mav.addObject("creatorAverageRating", reputation.getAverageRating());
            mav.addObject("creatorReviews", reputation.getReviews());
            mav.addObject("reviewEventsById", reputation.getEventsById());
            mav.addObject("creatorReviewsPage", reputation.getPage());
            mav.addObject("creatorReviewsTotalPages", reputation.getTotalPages());
        }
    }

    // Seguir al organizador de ESTE evento: el service lo resuelve a partir del evento (no se confia en un id
    // que mande el cliente). Quien puede lo decide @PreAuthorize antes de entrar (ver EventSecurity.canInteract):
    // tiene que poder ver el evento y no ser su organizador ("no te seguis a vos mismo"). El boton ya no se
    // ofrece en ese caso: esto solo se alcanza con un POST armado a mano, y da el mismo 404.
    @PreAuthorize("@eventSecurity.canInteract(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/seguir-organizador", method = RequestMethod.POST)
    public ModelAndView followOrganizer(@PathVariable final long id,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        followService.followOrganizerOf(id, currentUser.getId());
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Dejar de seguir: alcanza con poder ver el evento (un evento que no podes ver no sirve para llegar a su
    // organizador).
    @PreAuthorize("@eventSecurity.canView(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/dejar-de-seguir-organizador", method = RequestMethod.POST)
    public ModelAndView unfollowOrganizer(@PathVariable final long id,
                                          @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        followService.unfollowOrganizerOf(id, currentUser.getId());
        return new ModelAndView("redirect:/eventos/" + id);
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

        // El creador es quien esta logueado. Subir las imagenes y crear el evento pasa todo DENTRO de
        // la misma transaccion, adentro de eventService.create(): una imagen invalida o una categoria
        // inexistente deshacen las dos cosas juntas.
        final EventCreation created;
        try {
            created = eventService.create(currentUser.getId(), dto);
        } catch (final InvalidImageException e) {
            return publishFormWithErrors(form, errors, message(messageKeyOf(e.getReason())));
        } catch (final InvalidEventException e) {
            return publishFormWithErrors(form, errors, message(messageKeyOf(e.getReason())));
        }
        // Todo evento nuevo arranca pendiente de aprobacion (ver EventApprovalStatus):
        // se avisa con un popup en la pantalla a la que se redirige, sea cual sea.
        redirectAttributes.addFlashAttribute("justPublished", true);
        // A donde sigue lo decide el service (EventCreation): a invitar, o al detalle del evento.
        final long createdId = created.getEvent().getId();
        return created.isNeedsInvitations()
                ? new ModelAndView("redirect:/perfil/eventos/" + createdId + "/invitar")
                : new ModelAndView("redirect:/eventos/" + createdId);
    }

    // El service avisa POR QUE fallo con un codigo (no con un texto): la clave i18n del mensaje que ve la persona
    // se elige aca, en la capa web.
    private static String messageKeyOf(final InvalidImageException.Reason reason) {
        return switch (reason) {
            case EMPTY -> "event.publish.imageEmpty";
            case TOO_LARGE -> "event.publish.imageTooLarge";
            case NOT_AN_IMAGE -> "event.publish.imageNotAnImage";
            case TOO_MANY -> "ValidImageCount.eventForm.images";
        };
    }

    private static String messageKeyOf(final InvalidEventException.Reason reason) {
        return switch (reason) {
            case INVALID_DATA -> "event.publish.invalidData";
            case CATEGORY_NOT_FOUND -> "event.publish.categoryNotFound";
            case NO_CATEGORY -> "ValidCategorias.none";
            case CAPACITY_BELOW_OCCUPIED -> "cupo.muyBajo";
            case DATE_IN_PAST -> "ValidEventDate.past";
            case DATE_TOO_FAR -> "ValidEventDate.tooFar";
        };
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
    public ModelAndView inscriptos(@PathVariable final long id,
                                   @RequestParam(value = "page", required = false) final String page,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final Event event = eventService.findByIdOrThrow(id);
        final PageResult<Registration> result = registrationService.findPageByEventId(id, Paging.parsePage(page));
        final ModelAndView mav = new ModelAndView("events/inscriptos");
        mav.addObject("event", event);
        mav.addObject("registrations", result.getContent());
        mav.addObject("totalRegistrations", result.getTotalElements());
        mav.addObject("currentPage", result.getPage());
        mav.addObject("totalPages", result.getTotalPages());
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
    public ModelAndView editar(@PathVariable final long id, @Valid final EventForm form, final BindingResult errors) {
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
            eventService.update(id, dto);
        } catch (final InvalidImageException e) {
            return editarFormWithErrors(id, form, errors, message(messageKeyOf(e.getReason())));
        } catch (final InvalidEventException e) {
            if (e.getField() != null) {
                errors.rejectValue(e.getField(), messageKeyOf(e.getReason()));
                return editarFormWithErrors(id, form, errors, null);
            }
            return editarFormWithErrors(id, form, errors, message(messageKeyOf(e.getReason())));
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
    public ModelAndView cancelar(@PathVariable final long id) {
        eventService.cancel(id);
        return new ModelAndView("redirect:/perfil/eventos");
    }
}
