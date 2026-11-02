package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserRegistrationResult;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.form.SetPasswordForm;
import ar.edu.itba.paw.webapp.util.FormErrors;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Optional;

@Controller
public class LoginController {

    private final UserService userService;
    private final MessageSource messageSource;
    private final UserDetailsService userDetailsService;
    // El mismo registro de WebAuthConfig: el login normal lo alimenta solo (sessionManagement), pero
    // autoLogin() arma la sesion a mano y tiene que registrarla el mismo.
    private final SessionRegistry sessionRegistry;

    @Autowired
    public LoginController(final UserService userService, final MessageSource messageSource,
                           final UserDetailsService userDetailsService, final SessionRegistry sessionRegistry) {
        this.userService = userService;
        this.messageSource = messageSource;
        this.userDetailsService = userDetailsService;
        this.sessionRegistry = sessionRegistry;
    }

    // El submit del login normal (POST /login) lo intercepta el filtro de Spring
    @RequestMapping(value = "/login", method = RequestMethod.GET)
    public ModelAndView login(@RequestParam(value = "error", required = false) final String error,
                               @RequestParam(value = "logout", required = false) final String logout,
                               @RequestParam(value = "registered", required = false) final String registered,
                               @RequestParam(value = "passwordSet", required = false) final String passwordSet,
                               @RequestParam(value = "needsPassword", required = false) final String needsPassword,
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
        mav.addObject("passwordSet", passwordSet != null);
        mav.addObject("needsPassword", needsPassword != null);
        mav.addObject("unverified", unverified != null);
        mav.addObject("suspended", suspended != null);
        mav.addObject("sessionExpired", expired != null);
        mav.addObject("resent", resent != null);
        mav.addObject("verifyError", verifyError != null);
        mav.addObject("passwordReset", passwordReset != null);
        if (needsPassword != null) {
            final SetPasswordForm form = new SetPasswordForm();
            form.setEmail(email);
            mav.addObject("setPasswordForm", form);
        }
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
        if (!autoLogin(confirmed.get(), request)) {
            return new ModelAndView("redirect:/login?suspended");
        }
        return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
    }

    // Arma la Authentication a mano y la guarda en la sesion HTTP.
    private boolean autoLogin(final User user, final HttpServletRequest request) {
        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        if (!userDetails.isEnabled()) {
            return false;
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        // Al armar la sesion a mano no pasa por la estrategia de Spring Security que la registra: sin
        // esto, SessionExpiringListener no la encuentra y una cuenta recien confirmada que despues
        // suspenden conserva su sesion hasta el timeout. Se registra con el MISMO PlancitoUserDetails
        // que lleva la Authentication, para que el listener lo encuentre por getId().
        sessionRegistry.registerNewSession(session.getId(), userDetails);
        return true;
    }

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
        final UserRegistrationResult result = userService.register(
                form.toDTO(SafeRedirect.isSafe(redirect) ? redirect : null));

        final String suffix = SafeRedirect.isSafe(redirect)
                ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8) : "";
        if (result == UserRegistrationResult.EMAIL_TAKEN) {
            return new ModelAndView("redirect:/login?error" + suffix);
        }

        return new ModelAndView("redirect:/login?passwordSet" + suffix);
    }

    private ModelAndView setPasswordFormWithErrors(final SetPasswordForm form, final BindingResult errors,
                                                    final String redirect) {
        final ModelAndView mav = new ModelAndView("auth/login");
        mav.addObject("needsPassword", true);
        mav.addObject("setPasswordForm", form);
        mav.addObject("fieldErrors", FormErrors.of(errors, messageSource));
        if (SafeRedirect.isSafe(redirect)) {
            mav.addObject("redirectTo", redirect);
        }
        return mav;
    }
}
