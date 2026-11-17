package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.SimilarityParameters;
import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del service de recomendaciones. La cuenta (S, el ranking, el corte de la pagina) es SQL y se prueba
 * contra la base en EventRecommendationJpaDaoTest; aca solo se prueba lo que arma el service: el total, la pagina
 * efectiva y que el DAO reciba la pagina y los coeficientes correctos. Cada stub con argThat solo devuelve datos si
 * el argumento es el correcto, asi que si el service pidiera otra cosa el resultado no coincidiria.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventSimilarityServiceImplTest {

    private static final long VIEWER_ID = 5;
    private static final int PAGE_SIZE = 12;
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Date NOW_DATE = Date.from(NOW);

    @Mock
    private EventDao eventDao;

    private final SimilarityParameters parameters = SimilarityParameters.defaults();
    private EventSimilarityServiceImpl service;

    @Before
    public void setUp() {
        service = new EventSimilarityServiceImpl(eventDao, parameters, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EventSearchCriteria criteria(final int page) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setViewerId(VIEWER_ID);
        criteria.setPage(page);
        criteria.setPageSize(PAGE_SIZE);
        return criteria;
    }

    private static Event event(final long id) {
        final Event event = new Event();
        event.setId(id);
        return event;
    }

    // ---- searchRecommended() ----

    @Test
    public void testSearchRecommendedRequiresAViewer() {
        final EventSearchCriteria anonymous = new EventSearchCriteria();

        assertThrows(NullPointerException.class, () -> service.searchRecommended(anonymous));
    }

    @Test
    public void testSearchRecommendedReturnsTheRequestedPageWithTheListingTotal() {
        // El DAO solo devuelve la pagina si le piden la 2; el total sale de countSearch
        final EventSearchCriteria asked = criteria(2);
        when(eventDao.countSearch(asked)).thenReturn(30L);
        when(eventDao.findRecommendedPage(argThat(c -> c != null && c.getPage() == 2 && c.getPageSize() == PAGE_SIZE
                && Long.valueOf(VIEWER_ID).equals(c.getViewerId())), same(parameters)))
                .thenReturn(Arrays.asList(event(13), event(14)));

        final PageResult<Event> result = service.searchRecommended(asked);

        assertEquals(Arrays.asList(13L, 14L), result.getContent().stream().map(Event::getId).collect(java.util.stream.Collectors.toList()));
        assertEquals(30, result.getTotalElements());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testAPageBeyondTheLastFallsBackToTheLastPage() {
        // 25 resultados en paginas de 12: la ultima es la 3. El DAO solo responde si le piden la 3
        final EventSearchCriteria asked = criteria(9);
        when(eventDao.countSearch(asked)).thenReturn(25L);
        when(eventDao.findRecommendedPage(argThat(c -> c != null && c.getPage() == 3), same(parameters)))
                .thenReturn(Collections.singletonList(event(25)));

        final PageResult<Event> result = service.searchRecommended(asked);

        assertEquals(3, result.getPage());
        assertEquals(1, result.getContent().size());
    }

    @Test
    public void testNothingMatchingGivesAnEmptyFirstPage() {
        final EventSearchCriteria asked = criteria(4);
        when(eventDao.countSearch(asked)).thenReturn(0L);
        when(eventDao.findRecommendedPage(argThat(c -> c != null && c.getPage() == 1), same(parameters)))
                .thenReturn(Collections.emptyList());

        final PageResult<Event> result = service.searchRecommended(asked);

        assertTrue(result.getContent().isEmpty());
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalElements());
    }

    // ---- findSimilar() ----

    @Test
    public void testFindSimilarReturnsWhatTheDatabaseRanked() {
        when(eventDao.findSimilarTo(eq(10L), eq(VIEWER_ID), same(parameters)))
                .thenReturn(Arrays.asList(event(3), event(1)));

        final List<Event> result = service.findSimilar(10, VIEWER_ID);

        assertEquals(Arrays.asList(3L, 1L), result.stream().map(Event::getId).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void testFindSimilarForAnAnonymousVisitorHasNoViewer() {
        when(eventDao.findSimilarTo(eq(10L), isNull(), any(SimilarityParameters.class)))
                .thenReturn(Collections.singletonList(event(2)));

        final List<Event> result = service.findSimilar(10, null);

        assertEquals(1, result.size());
    }

    @Test
    public void testFindSimilarIsEmptyWhenTheDatabaseFindsNone() {
        when(eventDao.findSimilarTo(eq(10L), isNull(), any(SimilarityParameters.class)))
                .thenReturn(Collections.emptyList());

        assertTrue(service.findSimilar(10, null).isEmpty());
    }

    // ---- hasAttendanceHistory() ----

    @Test
    public void testHasAttendanceHistoryWhenThereIsAnAttendedEvent() {
        when(eventDao.hasAttendanceHistory(VIEWER_ID, NOW_DATE)).thenReturn(true);

        assertTrue(service.hasAttendanceHistory(VIEWER_ID));
    }

    @Test
    public void testHasNoAttendanceHistoryWhenNothingWasAttended() {
        when(eventDao.hasAttendanceHistory(VIEWER_ID, NOW_DATE)).thenReturn(false);

        assertFalse(service.hasAttendanceHistory(VIEWER_ID));
    }
}
