package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.PasswordResetResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.ForgotPasswordForm;
import ar.edu.itba.paw.webapp.form.ResetPasswordForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;

// "Olvidé mi contraseña".
@Controller
public class PasswordResetController {

    private final UserService userService;
    private final MessageSource messageSource;

    @Autowired
    public PasswordResetController(final UserService userService, final MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    @RequestMapping(value = "/login/recuperar", method = RequestMethod.GET)
    public ModelAndView forgotPasswordForm(@RequestParam(value = "sent", required = false) final String sent) {
        final ModelAndView mav = new ModelAndView("auth/recuperar");
        mav.addObject("forgotPasswordForm", new ForgotPasswordForm());
        mav.addObject("sent", sent != null);
        return mav;
    }

    // Siempre redirige a "sent".
    @RequestMapping(value = "/login/recuperar", method = RequestMethod.POST)
    public ModelAndView forgotPassword(@Valid final ForgotPasswordForm form, final BindingResult errors) {
        if (errors.hasErrors()) {
            final ModelAndView mav = new ModelAndView("auth/recuperar");
            mav.addObject("forgotPasswordForm", form);
            mav.addObject("fieldErrors", FormErrors.of(errors, messageSource));
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
        if (errors.hasErrors()) {
            return resetPasswordFormWithErrors(form, errors);
        }
        final PasswordResetResult result = userService.resetPassword(form.getToken(), form.getPassword());
        if (result == PasswordResetResult.INVALID_TOKEN) {
            return new ModelAndView("redirect:/login/recuperar");
        }
        return new ModelAndView("redirect:/login?passwordReset");
    }

    private ModelAndView resetPasswordFormWithErrors(final ResetPasswordForm form, final BindingResult errors) {
        final ModelAndView mav = new ModelAndView("auth/restablecer");
        // Re-validar, no asumir true: el token que llega aca es el del body del
        // POST (lo que haya puesto quien mando el request), no uno que ya haya
        // pasado por isResetTokenValid() en este pedido.
        mav.addObject("tokenValid", userService.isResetTokenValid(form.getToken()));
        mav.addObject("resetPasswordForm", form);
        mav.addObject("fieldErrors", FormErrors.of(errors, messageSource));
        return mav;
    }
}
