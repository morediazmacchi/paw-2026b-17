package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MailFormatsTest {

    private static final Locale ES = new Locale("es");
    private static final Locale EN = new Locale("en");

    private ReloadableResourceBundleMessageSource messages;

    @Before
    public void setUp() {
        // Los textos reales de los mails (mail-messages*.properties), no unos de mentira.
        messages = new ReloadableResourceBundleMessageSource();
        messages.setBasename("classpath:i18n/mail-messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
    }

    private static Event event(final BigDecimal price) {
        final Event event = new Event();
        event.setAddress("Corrientes 1234");
        event.setPrice(price);
        return event;
    }

    // ---- displayName() ----

    @Test
    public void testDisplayNameIsTheNameWithoutSurroundingSpaces() {
        assertEquals("Ana", MailFormats.displayName("  Ana ", "ana@mail.com"));
    }

    @Test
    public void testDisplayNameOfABlankNameIsWhatComesBeforeTheAtSign() {
        assertEquals("ana", MailFormats.displayName("   ", "ana@mail.com"));
    }

    @Test
    public void testDisplayNameOfAMissingNameIsWhatComesBeforeTheAtSign() {
        assertEquals("beto", MailFormats.displayName(null, "beto@mail.com"));
    }

    @Test
    public void testDisplayNameWithoutNameNorEmailIsEmpty() {
        assertEquals("", MailFormats.displayName(null, null));
    }

    @Test
    public void testDisplayNameOfAnEmailWithoutLocalPartIsTheWholeEmail() {
        assertEquals("@mail.com", MailFormats.displayName(null, "@mail.com"));
    }

    // ---- place() ----

    @Test
    public void testPlaceWithoutNeighborhoodIsTheAddress() {
        assertEquals("Corrientes 1234", MailFormats.place(event(BigDecimal.TEN)));
    }

    @Test
    public void testPlaceWithNeighborhoodAddsItsName() {
        final Event event = event(BigDecimal.TEN);
        event.setNeighborhood(Barrio.PALERMO);

        assertEquals("Corrientes 1234, " + Barrio.PALERMO.getDisplayName(), MailFormats.place(event));
    }

    // ---- date() ----

    @Test
    public void testDateOfAnEventWithoutDateIsEmpty() {
        assertEquals("", MailFormats.date(event(BigDecimal.TEN), ES));
    }

    @Test
    public void testDateIsWrittenInTheLanguageOfTheRecipient() {
        final Event event = event(BigDecimal.TEN);
        event.setEventDate(new Date(1_900_000_000_000L));

        final String spanish = MailFormats.date(event, ES);
        final String english = MailFormats.date(event, EN);

        assertTrue(spanish.contains("2030"));
        assertTrue(english.contains("2030"));
        assertTrue("el mismo evento se escribe distinto en espanol y en ingles", !spanish.equals(english));
    }

    // ---- price() ----

    @Test
    public void testPriceOfAFreeEventSaysFreeInTheLanguageOfTheRecipient() {
        assertEquals("Gratis", MailFormats.price(event(BigDecimal.ZERO), ES, messages));
        assertEquals("Free", MailFormats.price(event(null), EN, messages));
    }

    @Test
    public void testPriceIsWrittenWithoutCentsAndWithTheSeparatorOfEachLanguage() {
        assertEquals("$1.500", MailFormats.price(event(new BigDecimal("1500.00")), ES, messages));
        assertEquals("ARS 1,500", MailFormats.price(event(new BigDecimal("1500.00")), EN, messages));
    }
}
