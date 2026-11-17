package ar.edu.itba.paw.webapp.config;

import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.LocaleResolver;

import javax.servlet.http.Cookie;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AcceptLanguageCookieLocaleResolverTest {

    private LocaleResolver resolver;
    private MockHttpServletRequest request;

    @Before
    public void setUp() {
        resolver = RootConfig.cookieLocaleResolver();
        request = new MockHttpServletRequest();
    }

    @Test
    public void testWithoutCookieNorHeaderTheLanguageIsSpanish() {
        // Un request sin header devuelve ingles en getLocales() (como un servidor configurado en ingles):
        // no es una preferencia de la persona, y no tiene que ganarle al espanol.
        assertEquals("es", resolver.resolveLocale(request).getLanguage());
    }

    @Test
    public void testWithoutCookieTheSupportedBrowserLanguageIsUsed() {
        request.addHeader("Accept-Language", "en-US,en;q=0.9");

        assertEquals("en", resolver.resolveLocale(request).getLanguage());
    }

    @Test
    public void testWithoutCookieAnUnsupportedBrowserLanguageFallsBackToSpanish() {
        request.addHeader("Accept-Language", "fr-FR,fr;q=0.9");

        assertEquals("es", resolver.resolveLocale(request).getLanguage());
    }

    @Test
    public void testTheFirstSupportedLanguageOfTheBrowserListIsUsed() {
        request.addHeader("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8,es;q=0.7");

        assertEquals("en", resolver.resolveLocale(request).getLanguage());
    }

    @Test
    public void testTheCookieWinsOverTheBrowserLanguage() {
        request.addHeader("Accept-Language", "en-US,en;q=0.9");
        request.setCookies(new Cookie("paw_locale", "es"));

        assertEquals("es", resolver.resolveLocale(request).getLanguage());
    }

    @Test
    public void testTheLanguageButtonWritesTheChoiceInTheCookie() {
        final MockHttpServletResponse response = new MockHttpServletResponse();

        new WebConfig().localeResolver().setLocale(request, response, Locale.ENGLISH);

        final Cookie cookie = response.getCookie("paw_locale");
        assertNotNull(cookie);
        assertEquals("en", cookie.getValue());
    }
}
