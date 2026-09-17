package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.UserForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
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
    public ModelAndView registerForm() {
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", new UserForm());
        return mav;
    }

    @RequestMapping(value = "/registro", method = RequestMethod.POST)
    public ModelAndView register(@Valid final UserForm form, final BindingResult errors) {
        if (errors.hasErrors()) {
            return registerFormWithErrors(form, errors);
        }

        if (!form.getPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
            return registerFormWithErrors(form, errors);
        }

        final UserRegistrationResult result = userService.register(form.getEmail(), form.getNombre(), form.getPassword());
        if (result == UserRegistrationResult.EMAIL_TAKEN) {
            errors.rejectValue("email", "email.taken", "Ese email ya está registrado");
            return registerFormWithErrors(form, errors);
        }

        return new ModelAndView("redirect:/login?registered");
    }

    private ModelAndView registerFormWithErrors(final UserForm form, final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        return mav;
    }
}
