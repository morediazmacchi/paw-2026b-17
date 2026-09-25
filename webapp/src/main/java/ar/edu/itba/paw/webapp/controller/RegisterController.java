package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.UserForm;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
public class RegisterController {

    private final UserService userService;
    private final MessageSource messageSource;

    @Autowired
    public RegisterController(final UserService userService, final MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    @RequestMapping(value = "/registro", method = RequestMethod.GET)
    // redirect: a donde volver cuando termine (viene del login, por ejemplo desde
    // un evento privado al que la invitaron). Viaja como hidden en el form y
    // despues dentro del link del mail de confirmacion, hasta el login final.
    public ModelAndView registerForm(@RequestParam(value = "redirect", required = false) final String redirect) {
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", new UserForm());
        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }

    @RequestMapping(value = "/registro", method = RequestMethod.POST)
    public ModelAndView register(@Valid final UserForm form, final BindingResult errors,
                                 @RequestParam(value = "redirect", required = false) final String redirect) {
        final String safeRedirect = SafeRedirect.isSafe(redirect) ? redirect : null;
        if (errors.hasErrors()) {
            return registerFormWithErrors(form, errors, safeRedirect);
        }

        if (!form.getPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
            return registerFormWithErrors(form, errors, safeRedirect);
        }

        final UserRegistrationResult result = userService.register(form.getEmail(), form.getNombre(),
                form.getPassword(), safeRedirect);
        if (result == UserRegistrationResult.EMAIL_TAKEN) {
            errors.rejectValue("email", "email.taken", "Ese email ya está registrado");
            return registerFormWithErrors(form, errors, safeRedirect);
        }

        final String suffix = safeRedirect == null ? ""
                : "&redirect=" + URLEncoder.encode(safeRedirect, StandardCharsets.UTF_8);
        return new ModelAndView("redirect:/login?registered" + suffix);
    }

    private ModelAndView registerFormWithErrors(final UserForm form, final BindingResult errors,
                                                final String redirect) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        if (redirect != null) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }
}
