package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.UserForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class RegisterController {

    private final UserService userService;
    private final MessageSource messageSource;

    @Autowired
    public RegisterController(final UserService userService, final MessageSource messageSource) {
        this.userService = userService;
        this.messageSource = messageSource;
    }

    @RequestMapping(value = "/registro", method = RequestMethod.GET)
    // redirect: a donde volver cuando termine
    public ModelAndView registerForm(@RequestParam(value = "redirect", required = false) final String redirect,
                                      @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        if (currentUser != null) {
            return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
        }
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", new UserForm());
        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }

    @RequestMapping(value = "/registro", method = RequestMethod.POST)
    public ModelAndView register(@Valid final UserForm form, final BindingResult errors,
                                 @RequestParam(value = "redirect", required = false) final String redirect,
                                 @ModelAttribute(value = "currentUser", binding = false) final User currentUser) {
        final String safeRedirect = SafeRedirect.isSafe(redirect) ? redirect : null;
        if (currentUser != null) {
            return new ModelAndView("redirect:" + (safeRedirect == null ? "/eventos" : safeRedirect));
        }
        if (errors.hasErrors()) {
            return registerFormWithErrors(form, errors, safeRedirect);
        }

        final UserRegistrationResult result = userService.register(form.toDTO(safeRedirect));
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
        final ModelAndView mav = new ModelAndView("auth/register");
        mav.addObject("userForm", form);
        mav.addObject("fieldErrors", FormErrors.of(errors, messageSource));
        if (redirect != null) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }
}
