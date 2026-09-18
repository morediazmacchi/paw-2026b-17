package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.PasswordResetResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ForgotPasswordForm;
import ar.edu.itba.paw.webapp.form.ResetPasswordForm;
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

// "Olvidé mi contraseña": pedir el link (recuperar) y despues elegir la nueva
// contraseña con ese link (restablecer). Separado de LoginController porque
// ese ya tiene bastante con el flujo de la cuenta fantasma.
@Controller
public class PasswordResetController {

    private final UserService userService;
    private final MessageSource messageSource;

    @Autowired
    public PasswordResetController(final UserService userService, final MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    private String messageFor(final FieldError fieldError) {
        return messageSource.getMessage(fieldError, LocaleContextHolder.getLocale());
    }

    @RequestMapping(value = "/login/recuperar", method = RequestMethod.GET)
    public ModelAndView forgotPasswordForm(@RequestParam(value = "sent", required = false) final String sent) {
        final ModelAndView mav = new ModelAndView("auth/recuperar");
        mav.addObject("forgotPasswordForm", new ForgotPasswordForm());
        mav.addObject("sent", sent != null);
        return mav;
    }

    // Siempre redirige a "sent", exista o no ese email: no tiene que servir
    // para averiguar si un mail esta registrado (mismo motivo que
    // LoginController.resendConfirmation).
    @RequestMapping(value = "/login/recuperar", method = RequestMethod.POST)
    public ModelAndView forgotPassword(@Valid final ForgotPasswordForm form, final BindingResult errors) {
        if (errors.hasErrors()) {
            final Map<String, String> fieldErrors = new HashMap<>();
            for (final FieldError fieldError : errors.getFieldErrors()) {
                fieldErrors.put(fieldError.getField(), messageFor(fieldError));
            }
            final ModelAndView mav = new ModelAndView("auth/recuperar");
            mav.addObject("forgotPasswordForm", form);
            mav.addObject("fieldErrors", fieldErrors);
            return mav;
        }

        userService.sendPasswordResetEmail(form.getEmail());
        return new ModelAndView("redirect:/login/recuperar?sent");
    }

    @RequestMapping(value = "/login/restablecer", method = RequestMethod.GET)
    public ModelAndView resetPasswordForm(@RequestParam("token") final String token) {
        final ModelAndView mav = new ModelAndView("auth/restablecer");
        final boolean tokenValid = userService.isResetTokenValid(token);
        mav.addObject("tokenValid", tokenValid);
        if (tokenValid) {
            final ResetPasswordForm form = new ResetPasswordForm();
            form.setToken(token);
            mav.addObject("resetPasswordForm", form);
        }
        return mav;
    }

    @RequestMapping(value = "/login/restablecer", method = RequestMethod.POST)
    public ModelAndView resetPassword(@Valid final ResetPasswordForm form, final BindingResult errors) {
        if (!errors.hasErrors() && !form.getPassword().equals(form.getConfirmarPassword())) {
            errors.rejectValue("confirmarPassword", "PasswordMismatch", "Las contraseñas no coinciden");
        }
        if (errors.hasErrors()) {
            return resetPasswordFormWithErrors(form, errors);
        }

        final PasswordResetResult result = userService.resetPassword(form.getToken(), form.getPassword());
        if (result == PasswordResetResult.INVALID_TOKEN) {
            // El link se debe haber usado o vencido justo en el medio: no tiene
            // sentido re-mostrar el mismo formulario, mandarlo a pedir uno nuevo.
            return new ModelAndView("redirect:/login/recuperar");
        }

        return new ModelAndView("redirect:/login?passwordReset");
    }

    private ModelAndView resetPasswordFormWithErrors(final ResetPasswordForm form, final BindingResult errors) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageFor(fieldError));
        }
        final ModelAndView mav = new ModelAndView("auth/restablecer");
        mav.addObject("tokenValid", true);
        mav.addObject("resetPasswordForm", form);
        mav.addObject("fieldErrors", fieldErrors);
        return mav;
    }
}
