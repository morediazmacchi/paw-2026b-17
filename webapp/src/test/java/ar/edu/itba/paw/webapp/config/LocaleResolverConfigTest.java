package ar.edu.itba.paw.webapp.config;

import org.junit.Test;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// El boton de idioma (LocaleController) llama a setLocale() sobre el LocaleResolver que le inyectan.
// Tiene que ser el de cookie: el que registra @EnableWebMvc por su cuenta (AcceptHeaderLocaleResolver)
// tira UnsupportedOperationException y el boton terminaba en un 500. Como los dos contextos de Spring
// (raiz y MVC) definen un bean "localeResolver" y el del hijo tapa al del padre, el de WebConfig
// tiene que ser tambien de cookie.
public class LocaleResolverConfigTest {

    @Test
    public void testTheMvcContextResolverCanChangeTheLocale() {
        final LocaleResolver resolver = new WebConfig().localeResolver();

        assertTrue(resolver instanceof CookieLocaleResolver);
    }

    @Test
    public void testTheRootContextResolverIsTheCookieOneToo() {
        assertTrue(RootConfig.cookieLocaleResolver() instanceof CookieLocaleResolver);
    }

    @Test
    public void testBothContextsKeepTheLocaleInTheSameCookie() {
        final CookieLocaleResolver mvc = (CookieLocaleResolver) new WebConfig().localeResolver();
        final CookieLocaleResolver root = (CookieLocaleResolver) RootConfig.cookieLocaleResolver();

        assertEquals("paw_locale", mvc.getCookieName());
        assertEquals(root.getCookieName(), mvc.getCookieName());
    }
}
