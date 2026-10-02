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

    @Autowired
    public LoginController(final UserService userService, final MessageSource messageSource,
                           final UserDetailsService userDetailsService) {
        this.userService = userService;
        this.messageSource = messageSource;
        this.userDetailsService = userDetailsService;
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
                               @RequestParam(value = "suspended", required = false) final String suspended,
                               @RequestParam(value = "resent", required = false) final String resent,
                               @RequestParam(value = "verified", required = false) final String verified,
                               @RequestParam(value = "verifyError", required = false) final String verifyError,
                               @RequestParam(value = "passwordReset", required = false) final String passwordReset,
                               @RequestParam(value = "email", required = false) final String email,
                               @RequestParam(value = "redirect", required = false) final String redirect,
                               final Principal principal) {
        // PAW20-78: si ya tenes sesion iniciada, esta pantalla no pinta nada
        // (no tiene sentido volver a loguearse). El redirect (si vino, ej. un
        // link viejo a "/login?redirect=/eventos/7") se sigue respetando.
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
    public ModelAndView confirmAccount(@RequestParam("token") final String token,
                                       @RequestParam(value = "redirect", required = false) final String redirect,
                                       final HttpServletRequest request) {
        final Optional<User> confirmed = userService.confirmEmail(token);
        if (!confirmed.isPresent()) {
            // El redirect viene del link del mail (ver RegisterController): se
            // vuelve a validar aca porque un link se puede armar a mano.
            final String suffix = SafeRedirect.isSafe(redirect)
                    ? "&redirect=" + URLEncoder.encode(redirect, StandardCharsets.UTF_8) : "";
            return new ModelAndView("redirect:/login?verifyError" + suffix);
        }
        // PAW20-81: el link del mail ya demuestra que es dueno de esta cuenta
        // (nadie mas pudo haberlo recibido), asi que la sesion arranca aca mismo:
        // no tiene sentido mandarlo a /login a tipear la contraseña que recien
        // eligio. Es sesion normal de ahi en mas (cookie de sesion de siempre,
        // no "quedar logueado para siempre"): un logout la cierra igual que a
        // cualquiera.
        // Una cuenta que un admin suspendio antes de que confirmara el mail no
        // entra por este atajo: tiene que ver el mismo aviso que en el login.
        if (!autoLogin(confirmed.get(), request)) {
            return new ModelAndView("redirect:/login?suspended");
        }
        return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
    }

    // Arma la Authentication a mano y la guarda en la sesion HTTP, el mismo
    // resultado al que llega el filtro de formLogin (WebAuthConfig) despues de
    // validar usuario+contraseña: aca no hay contraseña que validar, el token
    // consumido en confirmEmail() ya cumplio ese rol.
    // Devuelve false (y no inicia sesion) si la cuenta esta deshabilitada: es el
    // mismo chequeo que hace el login normal, que aca habria que hacer a mano.
    private boolean autoLogin(final User user, final HttpServletRequest request) {
        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        if (!userDetails.isEnabled()) {
            return false;
        }
        // Igual que el login normal de Spring: si ya habia una sesion, se le cambia
        // el id al autenticarse. Sin esto, quien hubiera logrado fijarle a la victima
        // un id de sesion conocido (session fixation) quedaria logueado como ella.
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        final UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext());
        return true;
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

        // register() se banca este caso: el email ya existe sin password (fantasma),
        // le pone la que llego y listo. Si en el medio alguien ya la establecio
        // (carrera rarisima), EMAIL_TAKEN cae al error generico de login.
        final UserRegistrationResult result = userService.register(form.getEmail(), null, form.getPassword(),
                SafeRedirect.isSafe(redirect) ? redirect : null);
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
