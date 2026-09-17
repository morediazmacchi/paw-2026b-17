package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.SetPasswordForm;
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
import java.util.HashMap;
import java.util.Map;

@Controller
public class LoginController {

    private final UserService userService;
    private final MessageSource messageSource;

    @Autowired
    public LoginController(final UserService userService, final MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    // El submit del login normal (POST /login) lo intercepta el filtro de Spring
    // Security antes de llegar aca (ver WebAuthConfig): este metodo solo dibuja
    // la pagina. error/logout los agrega Spring Security solo, como query params,
    // al redirigir de vuelta a loginPage(). needsPassword+email los agrega
    // LoginFailureHandler cuando detecta un usuario fantasma (sin password).
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView login(@RequestParam(value = "error", required = false) final String error,
                               @RequestParam(value = "logout", required = false) final String logout,
                               @RequestParam(value = "registered", required = false) final String registered,
                               @RequestParam(value = "passwordSet", required = false) final String passwordSet,
                               @RequestParam(value = "needsPassword", required = false) final String needsPassword,
                               @RequestParam(value = "email", required = false) final String email) {
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("loginError", error != null);
        mav.addObject("loggedOut", logout != null);
        mav.addObject("justRegistered", registered != null);
        mav.addObject("passwordSet", passwordSet != null);
        mav.addObject("needsPassword", needsPassword != null);
        if (needsPassword != null) {
            final SetPasswordForm form = new SetPasswordForm();
            form.setEmail(email);
            mav.addObject("setPasswordForm", form);
        }
        return mav;
    }

    @RequestMapping(value = "/login/nueva-contrasena", method = RequestMethod.POST)
    public ModelAndView setPassword(@Valid final SetPasswordForm form, final BindingResult errors) {
        if (errors.hasErrors()) {
            return setPasswordFormWithErrors(form, errors);
        }

        if (!form.getPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
            return setPasswordFormWithErrors(form, errors);
        }

        // register() se banca este caso: el email ya existe sin password (fantasma),
        // le pone la que llego y listo. Si en el medio alguien ya la establecio
        // (carrera rarisima), EMAIL_TAKEN cae al error generico de login.
        final UserRegistrationResult result = userService.register(form.getEmail(), null, form.getPassword());
        if (result == UserRegistrationResult.EMAIL_TAKEN) {
            return new ModelAndView("redirect:/login?error");
        }

        return new ModelAndView("redirect:/login?passwordSet");
    }

    private ModelAndView setPasswordFormWithErrors(final SetPasswordForm form, final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("needsPassword", true);
        mav.addObject("setPasswordForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        return mav;
    }
}
