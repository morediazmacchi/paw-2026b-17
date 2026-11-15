package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.FavoriteService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.RegistrationResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.ReportResult;
import ar.edu.itba.paw.services.UnregisterResult;
import ar.edu.itba.paw.webapp.form.ReportForm;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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

// Acciones que un visitante hace sobre un evento
@Controller
public class EventRegistrationController {

    private final RegistrationService registrationService;
    private final FavoriteService favoriteService;
    private final ModerationService moderationService;

    @Autowired
    public EventRegistrationController(final RegistrationService registrationService,
                                        final FavoriteService favoriteService, final ModerationService moderationService) {
        this.registrationService = registrationService;
        this.favoriteService = favoriteService;
        this.moderationService = moderationService;
    }

    // Quien se puede anotar (evento aprobado y, si es privado, con invitacion; sin excepcion de
    // admin; el organizador no se anota a su evento) lo decide @PreAuthorize antes de entrar (ver EventSecurity.canRegister).
    @PreAuthorize("@eventSecurity.canRegister(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/quiero-ir", method = RequestMethod.POST)
    public ModelAndView rsvp(@PathVariable final long id,
                              @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                              final RedirectAttributes redirectAttributes) {
        final RegistrationResult result = registrationService.register(id, currentUser.getId());
        redirectAttributes.addFlashAttribute("rsvp", rsvpParam(result));
        return new ModelAndView("redirect:/eventos/" + id);
    }

    private static String rsvpParam(final RegistrationResult result) {
        return switch (result.getStatus()) {
            case WAITLISTED -> "waitlist";
            case PENDING -> "pendiente";
            case ALREADY_REGISTERED -> "ya-anotado";
            case ALREADY_WAITLISTED -> "ya-en-waitlist";
            case ALREADY_PENDING -> "ya-pendiente";
            case REJECTED -> "rechazado";
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
            case EVENT_CANCELLED -> "cancelado";
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

    // Reportar: ver el evento sin ser su organizador lo decide @PreAuthorize (ver EventSecurity.canInteract).
    @PreAuthorize("@eventSecurity.canInteract(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/reportar", method = RequestMethod.POST)
    public ModelAndView reportar(@PathVariable final long id, @Valid final ReportForm form,
                                  final BindingResult errors, @ModelAttribute(value = "currentUser", binding = false) final User currentUser,
                                  final RedirectAttributes redirectAttributes) {
        if (errors.hasErrors()) {
            redirectAttributes.addFlashAttribute("reportMessage", "INVALID");
        } else {
            final ReportResult result = moderationService.report(id, currentUser, form.getMotivo());
            redirectAttributes.addFlashAttribute("reportMessage", result.name());
        }
        return new ModelAndView("redirect:/eventos/" + id);
    }

    // Si no podes ver el evento, tampoco podes guardarlo: lo decide @PreAuthorize antes de entrar.
    @PreAuthorize("@eventSecurity.canView(#id, authentication)")
    @RequestMapping(value = "/eventos/{id}/favorito", method = RequestMethod.POST)
    public ModelAndView addFavorite(@PathVariable final long id,
                                     @RequestParam(value = "returnTo", required = false) final String returnTo,
                                     @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        favoriteService.add(currentUser, id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    @RequestMapping(value = "/eventos/{id}/favorito/quitar", method = RequestMethod.POST)
    public ModelAndView removeFavorite(@PathVariable final long id,
                                        @RequestParam(value = "returnTo", required = false) final String returnTo,
                                        @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        favoriteService.remove(currentUser.getId(), id);
        return new ModelAndView("redirect:" + safeReturnTo(returnTo, id));
    }

    private static String safeReturnTo(final String returnTo, final long id) {
        return SafeRedirect.isSafe(returnTo) ? returnTo : "/eventos/" + id;
    }
}
