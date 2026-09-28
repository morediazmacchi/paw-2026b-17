package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.util.SafeRedirect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// El boton de idioma de la navbar (PAW20-75) pega aca. GET porque es un link,
// no un form.
@Controller
public class LocaleController {

    // Los unicos dos idiomas con textos completos (ver webapp/i18n): un "lang"
    // distinto (o ausente) se ignora en silencio, el idioma actual no cambia.
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
                                     final Principal principal) {
        if (SUPPORTED_LANGUAGES.contains(lang)) {
            localeResolver.setLocale(request, response, new Locale(lang));
            // PAW20-82: si esta logueado, ademas de la cookie (que solo vale para
            // este navegador) lo guardamos en la cuenta: la proxima vez que entre
            // desde otro dispositivo (o con la cookie borrada) recibe el mismo
            // idioma (ver WebAuthConfig.loginSuccessHandler()), y los mails que
            // se le manden tambien lo usan (ver NotificationServiceImpl).
            if (principal != null) {
                userService.findByEmail(principal.getName())
                        .ifPresent(user -> userService.updateLocale(user.getId(), lang));
            }
        }
        // redirect es la pagina donde estaba parado cuando apreto el boton (ver
        // navbar.tag): se vuelve a validar aca porque un link se puede armar a mano.
        return new ModelAndView("redirect:" + (SafeRedirect.isSafe(redirect) ? redirect : "/eventos"));
    }
}
