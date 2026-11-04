package ar.edu.itba.paw.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: el SavedSearchService es un @Mock. notifyNewEvents() devuelve a cuantas busquedas se
 * les aviso, y los tests asertan sobre eso.
 */
@RunWith(MockitoJUnitRunner.class)
public class SavedSearchSchedulerTest {

    @Mock
    private SavedSearchService savedSearchService;

    private SavedSearchScheduler scheduler;

    @Before
    public void setUp() {
        scheduler = new SavedSearchScheduler(savedSearchService);
    }

    @Test
    public void testNotifiesEverySearchWithNewEvents() {
        // Arrange
        when(savedSearchService.findIdsAfter(0, SavedSearchScheduler.TANDA)).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(savedSearchService.notifyNewEvents(1L)).thenReturn(true);
        when(savedSearchService.notifyNewEvents(2L)).thenReturn(false);
        when(savedSearchService.notifyNewEvents(3L)).thenReturn(true);

        // Act
        final int result = scheduler.notifyNewEvents();

        // Assert
        assertEquals(2, result);
    }

    /** Con mas busquedas que una tanda, sigue pidiendo la siguiente a partir del ultimo id. */
    @Test
    public void testGoesThroughEveryBatch() {
        // Arrange: una tanda llena (ids 1..TANDA) y despues una con un solo id
        final List<Long> fullBatch = LongStream.rangeClosed(1, SavedSearchScheduler.TANDA).boxed()
                .collect(Collectors.toList());
        final long lastOfFirstBatch = SavedSearchScheduler.TANDA;
        when(savedSearchService.findIdsAfter(0, SavedSearchScheduler.TANDA)).thenReturn(fullBatch);
        when(savedSearchService.findIdsAfter(lastOfFirstBatch, SavedSearchScheduler.TANDA))
                .thenReturn(Collections.singletonList(lastOfFirstBatch + 1));
        when(savedSearchService.notifyNewEvents(anyLong())).thenReturn(true);

        // Act
        final int result = scheduler.notifyNewEvents();

        // Assert
        assertEquals(SavedSearchScheduler.TANDA + 1, result);
    }

    /** Una busqueda que falla no deja sin aviso a las que vienen despues. */
    @Test
    public void testOneBrokenSearchDoesNotStopTheRest() {
        // Arrange
        when(savedSearchService.findIdsAfter(0, SavedSearchScheduler.TANDA)).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(savedSearchService.notifyNewEvents(1L)).thenReturn(true);
        when(savedSearchService.notifyNewEvents(2L)).thenThrow(new RuntimeException("se rompio"));
        when(savedSearchService.notifyNewEvents(3L)).thenReturn(true);

        // Act
        final int result = scheduler.notifyNewEvents();

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testWithoutSavedSearchesNotifiesNothing() {
        // Arrange
        when(savedSearchService.findIdsAfter(0, SavedSearchScheduler.TANDA)).thenReturn(Collections.emptyList());

        // Act
        final int result = scheduler.notifyNewEvents();

        // Assert
        assertEquals(0, result);
    }
}
