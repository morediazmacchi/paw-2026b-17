package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.AutoLogin;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;

@Controller
public class LoginController {

    private final UserService userService;
    private final AutoLogin autoLogin;

    @Autowired
    public LoginController(final UserService userService, final AutoLogin autoLogin) {
        this.userService = userService;
        this.autoLogin = autoLogin;
    }

    // El submit del login normal (POST /login) lo intercepta el filtro de Spring
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView login(@RequestParam(value = "error", required = false) final String error,
                               @RequestParam(value = "logout", required = false) final String logout,
                               @RequestParam(value = "registered", required = false) final String registered,
                               @RequestParam(value = "unverified", required = false) final String unverified,
                               @RequestParam(value = "suspended", required = false) final String suspended,
                               @RequestParam(value = "expired", required = false) final String expired,
                               @RequestParam(value = "resent", required = false) final String resent,
                               @RequestParam(value = "verifyError", required = false) final String verifyError,
                               @RequestParam(value = "passwordReset", required = false) final String passwordReset,
                               @RequestParam(value = "email", required = false) final String email,
                               @RequestParam(value = "redirect", required = false) final String redirect,
                               final Principal principal) {
        if (principal != null) {
            return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
        }
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("loginError", error != null);
        mav.addObject("loggedOut", logout != null);
        mav.addObject("justRegistered", registered != null);
        mav.addObject("unverified", unverified != null);
        mav.addObject("suspended", suspended != null);
        mav.addObject("sessionExpired", expired != null);
        mav.addObject("resent", resent != null);
        mav.addObject("verifyError", verifyError != null);
        mav.addObject("passwordReset", passwordReset != null);
        // El email del intento fallido: precarga el mini-form de "reenviar
        // confirmación"
        if (unverified != null && email != null) {
            mav.addObject("unverifiedEmail", email);
        }

        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }

    // GET porque es el link que llega por mail
    @RequestMapping(value = "/confirmar-cuenta", method = RequestMethod.GET)
    public ModelAndView confirmAccount(@RequestParam("token") final String token,
                                       @RequestParam(value = "redirect", required = false) final String redirect,
                                       final HttpServletRequest request) {
        final Optional<User> confirmed = userService.confirmEmail(token);
        if (!confirmed.isPresent()) {
            final String suffix = SafeRedirect.isSafe(redirect)
                    ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8) : "";
            return new ModelAndView("redirect:/login?verifyError" + suffix);
        }
        //confirmacion de cuenta y autologin.
        if (!autoLogin.login(confirmed.get().getEmail(), request)) {
            return new ModelAndView("redirect:/login?suspended");
        }
        return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
    }

    @RequestMapping(value = "/login/reenviar-confirmacion", method = RequestMethod.POST)
    public ModelAndView resendConfirmation(@RequestParam("email") final String email) {
        userService.resendVerificationEmail(email);
        return new ModelAndView("redirect:/login?resent");
    }
}
