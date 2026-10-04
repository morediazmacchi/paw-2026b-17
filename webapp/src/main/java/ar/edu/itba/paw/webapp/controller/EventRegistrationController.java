package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
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
import java.util.Optional;

// Acciones que un visitante hace sobre un evento (no su organizador): RSVP,
// favoritos, reportar. No comparte nada con el armado del evento en si
// (EventController) salvo el chequeo de acceso a eventos privados
// (EventAccessService).
@Controller
public class EventRegistrationController {

    private final EventService eventService;
    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final ModerationService moderationService;
    private final EventAccessService accessChecker;

    @Autowired
    public EventRegistrationController(final EventService eventService, final RegistrationService registrationService,
                                        final FavoriteService favoriteService, final ModerationService moderationService,
                                        final EventAccessService accessChecker) {
        this.eventService = eventService;
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.moderationService = moderationService;
        this.accessChecker = accessChecker;
    }

    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                              final RedirectAttributes redirectAttributes) {
        // Mismo chequeo que el detalle: el boton ya esta escondido en la UI si
        // no tenes acceso, pero un POST directo no se puede confiar solo en eso.
        accessChecker.getViewableEvent(id, currentUser);
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
    private static String rsvpParam(final RegistrationResult result) {
        switch (result.getStatus()) {
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
            case FINISHED:
                return "finalizado";
            case CONFIRMED:
            default:
                return "confirmed";
        }
    }

    // Mismo patron que rsvpParam(): detail.jsp elige el cartel a partir de este
    // flash attribute "rsvp".
    private static String unregisterParam(final UnregisterResult result) {
        switch (result) {
            case REJECTED:
                return "rechazado";
            case CANCELLED_WAITLISTED:
                return "baja-waitlist";
            case CANCELLED_PENDING:
                return "baja-pendiente";
            case FINISHED:
                return "finalizado";
            case NOT_FOUND:
            case CANCELLED:
            default:
                return "baja";
        }
    }

    // Mismo mecanismo que "Cancelar" en Mis inscripciones (ProfileController),
    // pero desde el detalle: busca la inscripcion propia de este evento y la
    // cancela, en vez de recibir el id de la inscripcion por parametro.
    @RequestMapping(value = "/eventos/{id}/darme-de-baja", method = RequestMethod.POST)
    public ModelAndView unregister(@PathVariable final long id,
                                   @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                   final RedirectAttributes redirectAttributes) {
        // El service ya sabe que estado tenia la inscripcion antes de borrarla
        // (ver UnregisterResult): no hace falta leerlo antes aca para despues
        // reconstruir que cartel corresponde.
        final Optional<Registration> registration = registrationService.findByEventAndUserId(id, currentUser.getId());
        final UnregisterResult result = registration
                .map(r -> registrationService.cancel(r.getId(), currentUser.getId()))
                .orElse(UnregisterResult.NOT_FOUND);
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
        // Mismo chequeo de acceso que detail() y rsvp(): si no podes ver el evento,
        // tampoco podes guardarlo (si no, "Mis favoritos" mostraria sus datos).
        accessChecker.getViewableEvent(id, currentUser);
        favoriteService.add(currentUser.getId(), id);
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
