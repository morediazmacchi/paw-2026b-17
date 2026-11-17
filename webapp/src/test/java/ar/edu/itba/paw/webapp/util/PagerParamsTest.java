package ar.edu.itba.paw.webapp.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PagerParamsTest {

    @Test
    public void testASearchTextIsRepeatedWithoutTheSurroundingSpaces() {
        // Act
        final Map<String, String> result = PagerParams.of("  rock  ");

        // Assert
        assertEquals("rock", result.get("q"));
    }

    @Test
    public void testAMissingOrBlankSearchTextIsNotRepeated() {
        // Assert
        assertTrue(PagerParams.of(null).isEmpty());
        assertTrue(PagerParams.of("   ").isEmpty());
    }

    @Test
    public void testTheExtraParameterGoesAfterTheSearchText() {
        // Act
        final Map<String, String> result = PagerParams.of("rock", "filtro", "NEW");

        // Assert
        assertEquals(Arrays.asList("q", "filtro"), Arrays.asList(result.keySet().toArray()));
    }

    @Test
    public void testTheExtraParameterIsKeptEvenWithoutASearchText() {
        // Arrange
        final Map<String, String> expected = new LinkedHashMap<>();
        expected.put("sort", "TITLE_ASC");

        // Act
        final Map<String, String> result = PagerParams.of(null, "sort", "TITLE_ASC");

        // Assert
        assertEquals(expected, result);
    }
}
