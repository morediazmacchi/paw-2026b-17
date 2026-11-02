package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// El boton de idioma de la navbar.
@Controller
public class LocaleController {

    // Los unicos dos idiomas.
    private static final List<String> SUPPORTED_LANGUAGES = Arrays.asList("es", "en");

    private final LocaleResolver localeResolver;
    private final UserService userService;

    @Autowired
    public LocaleController(final LocaleResolver localeResolver, final UserService userService) {
        this.localeResolver = localeResolver;
        this.userService = userService;
    }

    @RequestMapping(value = "/idioma", method = RequestMethod.GET)
    public ModelAndView changeLocale(@RequestParam("lang") final String lang,
                                     @RequestParam(value = "redirect", required = false) final String redirect,
                                     final HttpServletRequest request, final HttpServletResponse response,
                                     final Authentication authentication) {
        if (SUPPORTED_LANGUAGES.contains(lang)) {
            localeResolver.setLocale(request, response, new Locale(lang));
            // El id sale del principal (como en el resto de la app); un visitante sin sesion solo cambia la cookie.
            PlancitoUserDetails.from(authentication).ifPresent(user -> userService.updateLocale(user.getId(), lang));
        }
        return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
    }
}
