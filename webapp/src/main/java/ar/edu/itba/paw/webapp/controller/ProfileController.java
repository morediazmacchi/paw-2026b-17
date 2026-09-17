package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ProfileUpdateResult;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ChangePasswordForm;
import ar.edu.itba.paw.webapp.form.ProfileForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ProfileController {

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final RegistrationService registrationService;
    private final EventService eventService;
    private final MessageSource messageSource;

    @Autowired
    public ProfileController(final UserService userService, final UserDetailsService userDetailsService,
                              final RegistrationService registrationService, final EventService eventService,
                              final MessageSource messageSource) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
        this.registrationService = registrationService;
        this.eventService = eventService;
        this.messageSource = messageSource;
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    private Map<String, String> fieldErrorsOf(final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        return fieldErrors;
    }

    private User currentUser(final Principal principal) {
        return userService.findByEmail(principal.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado sin fila en la base: " + principal.getName()));
    }

    // Para el badge de "Mis inscripciones" en accountSidebar.tag: se pide en
    // todas las paginas de la cuenta, no solo en /perfil/inscripciones.
    private int registrationsCountFor(final User user) {
        return registrationService.findByEmail(user.getEmail()).size();
    }

    // No hace falta antMatcher propio para /perfil ni /perfil/contrasena: no
    // estan en el permitAll de WebAuthConfig, asi que ya caen bajo
    // anyRequest().authenticated().
    @RequestMapping(value = "/perfil", method = RequestMethod.GET)
    public ModelAndView perfil(@RequestParam(value = "updated", required = false) final String updated,
                                @RequestParam(value = "passwordChanged", required = false) final String passwordChanged,
                                final Principal principal) {
        final User user = currentUser(principal);
        final ProfileForm profileForm = new ProfileForm();
        profileForm.setNombre(user.getName());
        profileForm.setEmail(user.getEmail());

        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", user);
        mav.addObject("profileForm", profileForm);
        mav.addObject("changePasswordForm", new ChangePasswordForm());
        mav.addObject("profileUpdated", updated != null);
        mav.addObject("passwordChanged", passwordChanged != null);
        mav.addObject("registrationsCount", registrationsCountFor(user));
        return mav;
    }

    @RequestMapping(value = "/perfil", method = RequestMethod.POST)
    public ModelAndView updateProfile(@Valid final ProfileForm form, final BindingResult errors,
                                       final Principal principal) {
        final User user = currentUser(principal);

        if (!errors.hasErrors()) {
            final ProfileUpdateResult result = userService.updateProfile(user.getId(), form.getNombre(), form.getEmail());
            if (result == ProfileUpdateResult.EMAIL_TAKEN) {
                errors.rejectValue("email", "email.taken", "Ese email ya está en uso");
            }
        }

        if (errors.hasErrors()) {
            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", user);
            mav.addObject("profileForm", form);
            mav.addObject("profileFieldErrors", fieldErrorsOf(errors));
            mav.addObject("changePasswordForm", new ChangePasswordForm());
            mav.addObject("registrationsCount", registrationsCountFor(user));
            return mav;
        }

        // El email pudo haber cambiado: refrescamos el Authentication de la sesion
        // actual con el nuevo username, si no principal.getName() queda apuntando
        // al email viejo hasta el proximo login (y findByEmail en el proximo
        // pedido a /perfil ya no lo encontraria).
        refreshAuthentication(form.getEmail().trim().toLowerCase());
        return new ModelAndView("redirect:/perfil?updated");
    }

    @RequestMapping(value = "/perfil/contrasena", method = RequestMethod.POST)
    public ModelAndView changePassword(@Valid final ChangePasswordForm form, final BindingResult errors,
                                        final Principal principal) {
        final User user = currentUser(principal);

        if (!errors.hasErrors() && !form.getNewPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
        }
        if (!errors.hasErrors()) {
            final boolean ok = userService.changePassword(user.getId(), form.getCurrentPassword(), form.getNewPassword());
            if (!ok) {
                errors.rejectValue("currentPassword", "wrongCurrentPassword", "La contraseña actual no es correcta");
            }
        }

        if (errors.hasErrors()) {
            final ProfileForm profileForm = new ProfileForm();
            profileForm.setNombre(user.getName());
            profileForm.setEmail(user.getEmail());

            final ModelAndView mav = new ModelAndView("auth/perfil");
            mav.addObject("user", user);
            mav.addObject("profileForm", profileForm);
            mav.addObject("changePasswordForm", form);
            mav.addObject("passwordFieldErrors", fieldErrorsOf(errors));
            mav.addObject("registrationsCount", registrationsCountFor(user));
            return mav;
        }

        return new ModelAndView("redirect:/perfil?passwordChanged");
    }

    @RequestMapping(value = "/perfil/inscripciones", method = RequestMethod.GET)
    public ModelAndView misInscripciones(@RequestParam(value = "cancelled", required = false) final String cancelled,
                                          final Principal principal) {
        final User user = currentUser(principal);
        final List<Registration> registrations = registrationService.findByEmail(user.getEmail());

        final Map<Long, Event> eventsById = new HashMap<>();
        for (final Registration registration : registrations) {
            eventService.findById(registration.getEventId()).ifPresent(event -> eventsById.put(event.getId(), event));
        }

        final ModelAndView mav = new ModelAndView("auth/inscripciones");
        mav.addObject("user", user);
        mav.addObject("registrations", registrations);
        mav.addObject("eventsById", eventsById);
        mav.addObject("cancelled", cancelled != null);
        mav.addObject("registrationsCount", registrations.size());
        return mav;
    }

    @RequestMapping(value = "/perfil/inscripciones/{id}/cancelar", method = RequestMethod.POST)
    public ModelAndView cancelarInscripcion(@PathVariable final long id, final Principal principal) {
        registrationService.cancel(id, principal.getName());
        return new ModelAndView("redirect:/perfil/inscripciones?cancelled");
    }

    private void refreshAuthentication(final String newEmail) {
        final UserDetails updatedDetails = userDetailsService.loadUserByUsername(newEmail);
        final Authentication current = SecurityContextHolder.getContext().getAuthentication();
        final Authentication refreshed = new UsernamePasswordAuthenticationToken(
                updatedDetails, current.getCredentials(), updatedDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(refreshed);
    }
}
