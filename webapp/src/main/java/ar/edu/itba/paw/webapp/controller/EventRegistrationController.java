package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReportResult;
import ar.edu.itba.paw.services.UnregisterResult;
import ar.edu.itba.paw.webapp.form.ReportForm;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;

// Acciones que un visitante hace sobre un evento (no su organizador): RSVP,
// favoritos, reportar. No comparte nada con el armado del evento en si
// (EventController) salvo el chequeo de acceso a eventos privados
// (EventAccessService).
@Controller
public class EventRegistrationController {

    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final ModerationService moderationService;
    private final EventAccessService accessChecker;

    @Autowired
    public EventRegistrationController(final RegistrationService registrationService,
                                        final FavoriteService favoriteService, final ModerationService moderationService,
                                        final EventAccessService accessChecker) {
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.moderationService = moderationService;
        this.accessChecker = accessChecker;
    }

    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                              final RedirectAttributes redirectAttributes) {
        // El chequeo de acceso (evento privado sin invitacion -> EventNotFoundException)
        // ya lo hace register() el mismo (ver RegistrationServiceImpl): no hace
        // falta repetirlo aca.
        // Ya no hace falta atrapar IllegalStateException: el service dejo de
        // lanzarla cuando el evento esta lleno, ahora devuelve WAITLISTED.
        // Dos clics simultaneos del mismo usuario se serializan dentro de register()
        // (lockea el evento antes de chequear si ya existe): no hace falta reintentar.
        final RegistrationResult result = registrationService.register(id, currentUser);
        redirectAttributes.addFlashAttribute("rsvp", rsvpParam(result));
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Los finales posibles del RSVP viajan como flash attribute "rsvp"
    // (POST-Redirect-GET) y detail.jsp elige el cartel a partir de esto. No en la
    // URL: con un link viejo el cartel volvia a salir sin haber hecho nada.
    // Switch expression (sin default): si manana se agrega un Status nuevo y
    // nos olvidamos de agregarlo aca, esto no compila -- en vez de caer en
    // silencio en un default equivocado.
    private static String rsvpParam(final RegistrationResult result) {
        return switch (result.getStatus()) {
            case WAITLISTED -> "waitlist";
            case PENDING -> "pendiente";
            case ALREADY_REGISTERED -> "ya-anotado";
            case ALREADY_WAITLISTED -> "ya-en-waitlist";
            case ALREADY_PENDING -> "ya-pendiente";
            case REJECTED -> "rechazado";
            case OWN_EVENT -> "propio-evento";
            case CANCELLED -> "cancelado";
            case FINISHED -> "finalizado";
            case CONFIRMED -> "confirmed";
        };
    }

    // Mismo patron que rsvpParam(): detail.jsp elige el cartel a partir de este
    // flash attribute "rsvp". Idem: switch expression sin default.
    private static String unregisterParam(final UnregisterResult result) {
        return switch (result) {
            case REJECTED -> "rechazado";
            case CANCELLED_WAITLISTED -> "baja-waitlist";
            case CANCELLED_PENDING -> "baja-pendiente";
            case FINISHED -> "finalizado";
            case NOT_FOUND, CANCELLED -> "baja";
        };
    }

    // Mismo mecanismo que "Cancelar" en Mis inscripciones (ProfileController),
    // pero desde el detalle: busca la inscripcion propia de este evento y la
    // cancela, en vez de recibir el id de la inscripcion por parametro.
    @RequestMapping(value = "/eventos/{id}/darme-de-baja", method = RequestMethod.POST)
    public ModelAndView unregister(@PathVariable final long id,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        final UnregisterResult result = registrationService.cancelOwnRegistration(id, currentUser.getId());
        redirectAttributes.addFlashAttribute("rsvp", unregisterParam(result));
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Reportar. No hace falta antMatcher propio: no esta en el permitAll, asi
    // que ya cae bajo anyRequest().authenticated(). El resultado vuelve como
    // flash attribute "reportMessage" y detail.jsp elige el cartel con eso.
    @RequestMapping(value = "/eventos/{id}/reportar", method = RequestMethod.POST)
    public ModelAndView reportar(@PathVariable final long id, @Valid final ReportForm form,
                                  final BindingResult errors, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                  final RedirectAttributes redirectAttributes) {
        // Mismo chequeo que preguntar/rsvp: si no existe o no podes verlo, 404.
        // Sin el canView(), la diferencia entre "404" y "reporte recibido"
        // delataria que un id de evento privado existe.
        accessChecker.getViewableEvent(id, currentUser);
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("reportMessage", "INVALID");
        } else {
            final ReportResult result = moderationService.report(id, currentUser, form.getMotivo());
            redirectAttributes.addFlashAttribute("reportMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // returnTo viaja como hidden en el form del corazon (paw:card/detail.jsp):
    // desde el listado tiene que volver al listado (con sus filtros), no
    // siempre al detalle. Sin el, o si viene manipulado a una URL ajena
    // (SafeRedirect), cae al detalle como default razonable.
    @RequestMapping(value = "/eventos/{id}/favorito", method = RequestMethod.POST)
    public ModelAndView addFavorite(@PathVariable final long id,
                                     @RequestParam(value = "returnTo", required = false) final String returnTo,
                                     @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        // El chequeo de acceso ya lo hace favoriteService.add() el mismo.
        favoriteService.add(currentUser, id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    @RequestMapping(value = "/eventos/{id}/favorito/quitar", method = RequestMethod.POST)
    public ModelAndView removeFavorite(@PathVariable final long id,
                                        @RequestParam(value = "returnTo", required = false) final String returnTo,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        // A proposito sin canView(): quitar un favorito es siempre inofensivo, y
        // quien ya perdio el acceso al evento (ej. le revocaron la invitacion)
        // tiene que poder sacarlo igual de su lista.
        favoriteService.remove(currentUser.getId(), id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    private static String safeReturnTo(final String returnTo, final long id) {
        return SafeRedirect.isSafe(returnTo) ? returnTo : "/eventos/" + id;
    }
}
