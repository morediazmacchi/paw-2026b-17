package ar.edu.itba.paw.webapp.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

// La clave que firma la cookie de "recordarme" nunca esta en el codigo: se toma de la configuracion si sirve y, si no,
// se genera una al azar (ver WebAuthConfig.rememberMeKey()).
public class WebAuthConfigRememberMeKeyTest {

    private static final String STRONG_KEY = "k".repeat(WebAuthConfig.MIN_REMEMBER_ME_KEY_LENGTH);

    @Test
    public void testAConfiguredKeyLongEnoughIsUsedAsIs() {
        // Act
        final String key = WebAuthConfig.rememberMeKey(STRONG_KEY);

        // Assert
        assertEquals(STRONG_KEY, key);
    }

    @Test
    public void testSurroundingSpacesOfAConfiguredKeyAreIgnored() {
        // Act
        final String key = WebAuthConfig.rememberMeKey("  " + STRONG_KEY + "  ");

        // Assert
        assertEquals(STRONG_KEY, key);
    }

    @Test
    public void testWithoutAConfiguredKeyARandomLongOneIsGenerated() {
        // Act
        final String key = WebAuthConfig.rememberMeKey(null);

        // Assert
        assertTrue(key.length() >= WebAuthConfig.MIN_REMEMBER_ME_KEY_LENGTH);
    }

    @Test
    public void testAConfiguredKeyTooShortIsDiscarded() {
        // Arrange
        final String weak = "abc123";

        // Act
        final String key = WebAuthConfig.rememberMeKey(weak);

        // Assert
        assertNotEquals(weak, key);
    }

    @Test
    public void testTwoGeneratedKeysAreDifferent() {
        // Act
        final String first = WebAuthConfig.rememberMeKey(null);
        final String second = WebAuthConfig.rememberMeKey(null);

        // Assert
        assertNotEquals(first, second);
    }
}
