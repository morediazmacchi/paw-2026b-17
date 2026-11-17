package ar.edu.itba.paw.services;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class PageResultTest {

    private static final int PAGE_SIZE = 10;

    @Test
    public void testTheFirstPageShowsFromOne() {
        // Arrange
        final PageResult<String> page = new PageResult<>(Arrays.asList("a", "b", "c"), 3, 1, PAGE_SIZE);

        // Assert
        assertEquals(1, page.getFirstItem());
        assertEquals(3, page.getLastItem());
    }

    @Test
    public void testALaterPageShowsItsOwnRange() {
        // Arrange: la pagina 3 de 25 elementos tiene 5 (21 al 25)
        final PageResult<String> page = new PageResult<>(Arrays.asList("a", "b", "c", "d", "e"), 25, 3, PAGE_SIZE);

        // Assert
        assertEquals(21, page.getFirstItem());
        assertEquals(25, page.getLastItem());
    }

    @Test
    public void testAnEmptyPageShowsZeroToZero() {
        // Arrange
        final PageResult<String> page = new PageResult<>(Collections.emptyList(), 0, 1, PAGE_SIZE);

        // Assert
        assertEquals(0, page.getFirstItem());
        assertEquals(0, page.getLastItem());
    }
}
