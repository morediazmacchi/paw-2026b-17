package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.SetPasswordForm;
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
                               @RequestParam(value = "unverified", required = false) final String unverified,
                               @RequestParam(value = "resent", required = false) final String resent,
                               @RequestParam(value = "verified", required = false) final String verified,
                               @RequestParam(value = "verifyError", required = false) final String verifyError,
                               @RequestParam(value = "passwordReset", required = false) final String passwordReset,
                               @RequestParam(value = "email", required = false) final String email,
                               @RequestParam(value = "redirect", required = false) final String redirect) {
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("loginError", error != null);
        mav.addObject("loggedOut", logout != null);
        mav.addObject("justRegistered", registered != null);
        mav.addObject("passwordSet", passwordSet != null);
        mav.addObject("needsPassword", needsPassword != null);
        mav.addObject("unverified", unverified != null);
        mav.addObject("resent", resent != null);
        mav.addObject("verified", verified != null);
        mav.addObject("verifyError", verifyError != null);
        mav.addObject("passwordReset", passwordReset != null);
        if (needsPassword != null) {
            final SetPasswordForm form = new SetPasswordForm();
            form.setEmail(email);
            mav.addObject("setPasswordForm", form);
        }
        // El email del intento fallido: precarga el mini-form de "reenviar
        // confirmación" que se muestra junto al alert de cuenta sin confirmar.
        if (unverified != null && email != null) {
            mav.addObject("unverifiedEmail", email);
        }
        // Va como hidden en el form de login (auth/login.jsp): si el submit tiene
        // exito, WebAuthConfig.loginSuccessHandler() te devuelve ahi en vez de al
        // home. Solo rutas propias de la app, nunca una URL externa.
        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }

    // GET porque es el link que llega por mail (se abre desde Gmail con un
    // click, no hay forma de que sea un POST).
    @RequestMapping(value = "/confirmar-cuenta", method = RequestMethod.GET)
    public ModelAndView confirmAccount(@RequestParam("token") final String token) {
        final boolean confirmed = userService.confirmEmail(token);
        return new ModelAndView(confirmed ? "redirect:/login?verified" : "redirect:/login?verifyError");
    }

    // Siempre redirige al mismo "resent", exista o no ese email y este o no ya
    // confirmado: no tiene que servir para averiguar si un mail esta registrado.
    @RequestMapping(value = "/login/reenviar-confirmacion", method = RequestMethod.POST)
    public ModelAndView resendConfirmation(@RequestParam("email") final String email) {
        userService.resendVerificationEmail(email);
        return new ModelAndView("redirect:/login?resent");
    }

    @RequestMapping(value = "/login/nueva-contrasena", method = RequestMethod.POST)
    public ModelAndView setPassword(@Valid final SetPasswordForm form, final BindingResult errors,
                                     @RequestParam(value = "redirect", required = false) final String redirect) {
        if (errors.hasErrors()) {
            return setPasswordFormWithErrors(form, errors, redirect);
        }

        if (!form.getPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
            return setPasswordFormWithErrors(form, errors, redirect);
        }

        // register() se banca este caso: el email ya existe sin password (fantasma),
        // le pone la que llego y listo. Si en el medio alguien ya la establecio
        // (carrera rarisima), EMAIL_TAKEN cae al error generico de login.
        final UserRegistrationResult result = userService.register(form.getEmail(), null, form.getPassword());
        // El redirect original (si vino) sigue viajando hasta aca: se lo pasamos
        // al ultimo salto para que el form de login que sigue lo tenga de nuevo
        // como hidden, y quien se loguee ahora si vuelva a donde estaba.
        final String suffix = SafeRedirect.isSafe(redirect)
                ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8) : "";
        if (result == UserRegistrationResult.EMAIL_TAKEN) {
            return new ModelAndView("redirect:/login?error" + suffix);
        }

        return new ModelAndView("redirect:/login?passwordSet" + suffix);
    }

    private ModelAndView setPasswordFormWithErrors(final SetPasswordForm form, final BindingResult errors,
                                                    final String redirect) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("needsPassword", true);
        mav.addObject("setPasswordForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }
}
