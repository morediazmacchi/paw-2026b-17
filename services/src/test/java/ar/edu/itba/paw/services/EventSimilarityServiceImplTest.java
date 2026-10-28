package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static ar.edu.itba.paw.services.SimilarityFixtures.ARTE;
import static ar.edu.itba.paw.services.SimilarityFixtures.DEPORTES;
import static ar.edu.itba.paw.services.SimilarityFixtures.MUSICA;
import static ar.edu.itba.paw.services.SimilarityFixtures.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del ordenamiento RECOMENDADOS y de "Te puede interesar". EventDao y
 * RegistrationDao son @Mock; la cache de precios es la de verdad (con un dao que no devuelve
 * ningun precio, asi que todo precio pago queda en el percentil 0,5).
 *
 * Los eventos se arman para que S(a, b) quede claramente de un lado u otro del umbral (ver
 * SimilarityFixtures): lo que se prueba es el ranking, no la aritmetica de S (eso es
 * EventSimilarityTest).
 */
@RunWith(MockitoJUnitRunner.class)
public class EventSimilarityServiceImplTest {

    private static final long VIEWER_ID = 5;
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final Date NOW_DATE = Date.from(NOW);

    @Mock
    private EventDao eventDao;
    @Mock
    private RegistrationDao registrationDao;

    private EventSimilarityServiceImpl service;

    @Before
    public void setUp() {
        lenient().when(eventDao.findPublicPaidPrices()).thenReturn(Collections.emptyList());
        service = serviceWith(SimilarityParameters.defaults());
    }

    private EventSimilarityServiceImpl serviceWith(final SimilarityParameters parameters) {
        final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new EventSimilarityServiceImpl(eventDao, registrationDao,
                new PriceDistributionCache(eventDao, parameters, clock), parameters, clock);
    }

    // ---- helpers ----

    private static EventSearchCriteria criteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setViewerId(VIEWER_ID);
        return criteria;
    }

    // El viernes pasado a la noche, en Palermo, gratis, de musica, del organizador 1.
    private static SimilarityFixtures.Builder musicFriday(final long id) {
        return event(id).categories(MUSICA).price("0").barrio(Barrio.PALERMO)
                .at("2026-10-16T21:00").creator(1);
    }

    // Todo lo opuesto a musicFriday: deportes de un martes a la manana, lejos, pago, otro organizador.
    private static SimilarityFixtures.Builder sportsTuesday(final long id) {
        return event(id).categories(DEPORTES).price("5000").barrio(Barrio.VILLA_LUGANO)
                .at("2026-10-20T09:00").creator(9);
    }

    private void attended(final Event... events) {
        final List<Long> ids = new ArrayList<>();
        for (final Event event : events) {
            ids.add(event.getId());
        }
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 30)).thenReturn(ids);
        when(eventDao.findByIds(ids)).thenReturn(Arrays.asList(events));
    }

    private void candidates(final EventSearchCriteria criteria, final Event... events) {
        when(eventDao.findAllMatching(criteria)).thenReturn(Arrays.asList(events));
    }

    private static List<Long> ids(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    // ---- searchRecommended() ----

    @Test
    public void testSearchRecommendedRequiresAViewer() {
        assertThrows(NullPointerException.class, () -> service.searchRecommended(new EventSearchCriteria()));
    }

    @Test
    public void testWithoutHistoryTheOrderIsTheOneTheDaoGaveByDate() {
        final EventSearchCriteria criteria = criteria();
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 30)).thenReturn(Collections.emptyList());
        candidates(criteria, sportsTuesday(1).build(), musicFriday(2).at("2026-10-24T21:00").build());

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(1L, 2L), ids(result.getContent()));
        assertEquals(2, result.getTotalElements());
    }

    @Test
    public void testEventsSimilarToWhatWasAttendedComeFirst() {
        final EventSearchCriteria criteria = criteria();
        attended(musicFriday(100).build());
        // El deporte ocurre antes, pero el recital del sabado se parece mucho al que fue.
        candidates(criteria, sportsTuesday(1).build(), musicFriday(2).at("2026-10-24T21:00").build());

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(2L, 1L), ids(result.getContent()));
    }

    @Test
    public void testCandidatesAreRankedBySumOfTheirSimilarityToTheAttendedEvents() {
        final EventSearchCriteria criteria = criteria();
        attended(musicFriday(100).build(), musicFriday(101).build());
        // Los dos se parecen mucho a lo asistido, pero el segundo (mismo organizador) mas que el
        // primero (S = 1 contra S = 0,85 en cada comparacion). El primero ocurre antes.
        final Event otherOrganizer = musicFriday(1).at("2026-10-23T21:00").creator(2).build();
        final Event sameOrganizer = musicFriday(2).at("2026-10-30T21:00").build();
        candidates(criteria, otherOrganizer, sameOrganizer);

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(2L, 1L), ids(result.getContent()));
    }

    @Test
    public void testEventsAddPointsEvenWhenTheyAreNotVerySimilar() {
        final EventSearchCriteria criteria = criteria();
        attended(musicFriday(100).build());
        // Ninguno de los dos se parece "mucho" (S = 0,5 y S = 0,08): no hay un umbral que los deje
        // en cero, asi que el de S = 0,5 va primero aunque ocurra despues.
        final Event veryDifferent = sportsTuesday(1).at("2026-10-20T09:00").build();
        final Event halfSimilar = event(2).categories(DEPORTES).price("0").barrio(Barrio.PALERMO)
                .at("2026-10-30T21:00").creator(9).build();
        candidates(criteria, veryDifferent, halfSimilar);

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(2L, 1L), ids(result.getContent()));
    }

    @Test
    public void testACandidateSimilarToMoreAttendedEventsOutranksOneSimilarToFewer() {
        final EventSearchCriteria criteria = criteria();
        // Tres asistidos: uno de musica y dos de deportes, todos el mismo viernes a la noche en Palermo.
        final Event music = musicFriday(100).build();
        final Event sports1 = event(101).categories(DEPORTES).price("0").barrio(Barrio.PALERMO)
                .at("2026-10-16T21:00").creator(9).build();
        final Event sports2 = event(102).categories(DEPORTES).price("0").barrio(Barrio.PALERMO)
                .at("2026-10-09T21:00").creator(9).build();
        attended(music, sports1, sports2);
        // El de musica es identico al recital asistido (S = 1) y se parece a medias a los de deportes
        // (S = 0,5); el de deportes es lo contrario. Suman 2,0 (1 + 0,5 + 0,5) contra 2,5 (0,5 + 1 + 1),
        // aunque el de musica sea el unico "muy parecido" a algo.
        final Event likeTheMusic = musicFriday(1).at("2026-10-23T21:00").build();
        final Event likeTheSports = event(2).categories(DEPORTES).price("0").barrio(Barrio.PALERMO)
                .at("2026-10-30T21:00").creator(9).build();
        candidates(criteria, likeTheMusic, likeTheSports);

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(2L, 1L), ids(result.getContent()));
    }

    @Test
    public void testHowLongAgoAnEventWasAttendedDoesNotMatter() {
        final EventSearchCriteria criteria = criteria();
        // Dos asistidos muy distintos entre si: uno de hace mas de un anio, otro de hace 10 dias.
        final Event old = event(100).categories(ARTE).price("9000").barrio(Barrio.NUNEZ)
                .at("2025-09-15T10:00").creator(11).build();
        final Event recent = event(101).categories(DEPORTES).price("0").barrio(Barrio.VILLA_LUGANO)
                .at("2026-10-09T23:00").creator(12).build();
        attended(old, recent);
        // Cada candidato es un clon de uno de los dos (mismo dia de la semana y hora). Empatan en
        // puntos: el que se parece al viejo vale lo mismo, asi que gana el que ocurre antes.
        final Event likeOld = event(1).categories(ARTE).price("9000").barrio(Barrio.NUNEZ)
                .at("2026-10-19T10:00").creator(11).build();
        final Event likeRecent = event(2).categories(DEPORTES).price("0").barrio(Barrio.VILLA_LUGANO)
                .at("2026-10-30T23:00").creator(12).build();
        candidates(criteria, likeOld, likeRecent);

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(1L, 2L), ids(result.getContent()));
    }

    @Test
    public void testTiesAreBrokenByDateAndThenById() {
        final EventSearchCriteria criteria = criteria();
        attended(musicFriday(100).build());   // ninguno de los candidatos se le parece
        candidates(criteria,
                sportsTuesday(1).at("2026-10-22T09:00").build(),
                sportsTuesday(3).at("2026-10-21T09:00").build(),
                sportsTuesday(2).at("2026-10-21T09:00").build());

        // El mismo orden que se obtendria sin historial: fecha ascendente, y a igual fecha, id.
        // (La lista del dao viene por fecha; el service la reordena por puntos y desempata igual.)
        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(2L, 3L, 1L), ids(result.getContent()));
    }

    @Test
    public void testOnlyTheConfiguredNumberOfAttendedEventsIsRead() {
        final EventSearchCriteria criteria = criteria();
        // El historial solo existe si se pide con limite 7 (con cualquier otro el dao devuelve
        // vacio): si el service pidiera otra cantidad, no se enteraria de lo que asistio.
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 7)).thenReturn(Collections.singletonList(100L));
        when(eventDao.findByIds(Collections.singletonList(100L))).thenReturn(Collections.singletonList(musicFriday(100).build()));
        candidates(criteria, sportsTuesday(1).build(), musicFriday(2).at("2026-10-24T21:00").build());

        final PageResult<Event> result = serviceWith(SimilarityParameters.builder().historySize(7).build())
                .searchRecommended(criteria);

        // Con historial, el recital (parecido a lo que asistio) pasa adelante del deporte.
        assertEquals(Arrays.asList(2L, 1L), ids(result.getContent()));
    }

    @Test
    public void testResultsArePaginatedAfterRanking() {
        final EventSearchCriteria criteria = criteria();
        criteria.setPageSize(2);
        criteria.setPage(2);
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 30)).thenReturn(Collections.emptyList());
        candidates(criteria, sportsTuesday(1).build(), sportsTuesday(2).build(), sportsTuesday(3).build(),
                sportsTuesday(4).build(), sportsTuesday(5).build());

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Arrays.asList(3L, 4L), ids(result.getContent()));
        assertEquals(5, result.getTotalElements());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testAPageBeyondTheLastFallsBackToTheLastPage() {
        final EventSearchCriteria criteria = criteria();
        criteria.setPageSize(2);
        criteria.setPage(99);
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 30)).thenReturn(Collections.emptyList());
        candidates(criteria, sportsTuesday(1).build(), sportsTuesday(2).build(), sportsTuesday(3).build());

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(2, result.getPage());
        assertEquals(Collections.singletonList(3L), ids(result.getContent()));
    }

    @Test
    public void testThePageIsRankedAcrossTheWholeResultAndNotWithinEachPage() {
        final EventSearchCriteria criteria = criteria();
        criteria.setPageSize(1);
        attended(musicFriday(100).build());
        // El mejor candidato esta ultimo en la lista por fecha, pero tiene que ser la pagina 1.
        candidates(criteria, sportsTuesday(1).build(), musicFriday(2).at("2026-10-24T21:00").build());

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertEquals(Collections.singletonList(2L), ids(result.getContent()));
    }

    @Test
    public void testNoCandidatesGivesAnEmptyPage() {
        final EventSearchCriteria criteria = criteria();
        attended(musicFriday(100).build());
        candidates(criteria);

        final PageResult<Event> result = service.searchRecommended(criteria);

        assertTrue(result.getContent().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    // ---- findSimilar() ----

    private static SimilarityFixtures.Builder subject() {
        return musicFriday(1);
    }

    private void similarTo(final Event subject, final Event... candidates) {
        when(eventDao.findById(subject.getId())).thenReturn(Optional.of(subject));
        when(eventDao.findAllMatching(any(EventSearchCriteria.class))).thenReturn(Arrays.asList(candidates));
    }

    @Test
    public void testFindSimilarOrdersByDescendingSimilarity() {
        final Event subject = subject().build();
        final Event identical = musicFriday(2).build();                        // S = 1
        final Event otherOrganizer = musicFriday(3).creator(2).build();        // S = 0,85
        similarTo(subject, otherOrganizer, identical);

        assertEquals(Arrays.asList(2L, 3L), ids(service.findSimilar(1, null)));
    }

    @Test
    public void testFindSimilarLeavesOutTheEventItself() {
        final Event subject = subject().build();
        similarTo(subject, subject, musicFriday(2).build());

        assertEquals(Collections.singletonList(2L), ids(service.findSimilar(1, null)));
    }

    @Test
    public void testFindSimilarKeepsOnlyEventsAboveTheMinimumScore() {
        final Event subject = subject().build();
        similarTo(subject, musicFriday(2).build(), sportsTuesday(3).build());

        assertEquals(Collections.singletonList(2L), ids(service.findSimilar(1, null)));
    }

    @Test
    public void testFindSimilarIsEmptyWhenNothingReachesTheMinimumScore() {
        final Event subject = subject().build();
        similarTo(subject, sportsTuesday(2).build());

        assertTrue(service.findSimilar(1, null).isEmpty());
    }

    @Test
    public void testFindSimilarReturnsAtMostTheConfiguredNumberOfEvents() {
        final Event subject = subject().build();
        similarTo(subject, musicFriday(2).build(), musicFriday(3).build(), musicFriday(4).build());
        final EventSimilarityServiceImpl two = serviceWith(SimilarityParameters.builder().relatedLimit(2).build());

        assertEquals(2, two.findSimilar(1, null).size());
    }

    @Test
    public void testFindSimilarReturnsAtMostFiveEventsByDefault() {
        final Event subject = subject().build();
        final Event[] many = new Event[8];
        for (int i = 0; i < many.length; i++) {
            many[i] = musicFriday(10 + i).build();
        }
        similarTo(subject, many);

        assertEquals(5, service.findSimilar(1, null).size());
    }

    @Test
    public void testFindSimilarBreaksTiesByDateAndThenById() {
        final Event subject = subject().build();
        similarTo(subject,
                musicFriday(4).at("2026-10-30T21:00").build(),
                musicFriday(3).at("2026-10-23T21:00").build(),
                musicFriday(2).at("2026-10-23T21:00").build());

        // Los tres son igual de parecidos (misma hora, mismo dia de la semana): fecha y despues id.
        assertEquals(Arrays.asList(2L, 3L, 4L), ids(service.findSimilar(1, null)));
    }

    @Test
    public void testFindSimilarAsksOnlyForEventsOtherThanTheViewersOwn() {
        // Solo responde si el criterio trae al espectador (para excluir sus propios eventos).
        when(eventDao.findById(1)).thenReturn(Optional.of(subject().build()));
        when(eventDao.findAllMatching(argThat(c -> c != null && Long.valueOf(VIEWER_ID).equals(c.getViewerId()))))
                .thenReturn(Arrays.asList(musicFriday(2).build()));

        assertEquals(Arrays.asList(2L), ids(service.findSimilar(1, VIEWER_ID)));
    }

    @Test
    public void testFindSimilarForAnAnonymousVisitorHasNoViewer() {
        // Solo responde si el criterio NO trae espectador.
        when(eventDao.findById(1)).thenReturn(Optional.of(subject().build()));
        when(eventDao.findAllMatching(argThat(c -> c != null && c.getViewerId() == null)))
                .thenReturn(Arrays.asList(musicFriday(2).build()));

        assertEquals(Arrays.asList(2L), ids(service.findSimilar(1, null)));
    }

    @Test
    public void testFindSimilarIsEmptyForAMissingEvent() {
        when(eventDao.findById(1)).thenReturn(Optional.empty());
        lenient().when(eventDao.findAllMatching(any(EventSearchCriteria.class)))
                .thenReturn(Arrays.asList(musicFriday(2).build()));

        // Si consultara candidatos igual, el resultado no estaria vacio.
        assertTrue(service.findSimilar(1, null).isEmpty());
    }

    @Test
    public void testFindSimilarIsEmptyForAPrivateEvent() {
        when(eventDao.findById(1)).thenReturn(Optional.of(subject().privateEvent().build()));
        lenient().when(eventDao.findAllMatching(any(EventSearchCriteria.class)))
                .thenReturn(Arrays.asList(musicFriday(2).build()));

        assertTrue(service.findSimilar(1, null).isEmpty());
    }

    @Test
    public void testFindSimilarIsEmptyForAnEventNotYetApproved() {
        when(eventDao.findById(1)).thenReturn(Optional.of(
                subject().approval(EventApprovalStatus.PENDING).build()));
        lenient().when(eventDao.findAllMatching(any(EventSearchCriteria.class)))
                .thenReturn(Arrays.asList(musicFriday(2).build()));

        assertTrue(service.findSimilar(1, null).isEmpty());
    }

    // ---- hasAttendanceHistory() ----

    @Test
    public void testHasAttendanceHistoryWhenThereIsAnAttendedEvent() {
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 1)).thenReturn(Collections.singletonList(100L));

        assertTrue(service.hasAttendanceHistory(VIEWER_ID));
    }

    @Test
    public void testHasNoAttendanceHistoryWhenNothingWasAttended() {
        when(registrationDao.findAttendedEventIds(VIEWER_ID, NOW_DATE, 1)).thenReturn(Collections.emptyList());

        assertFalse(service.hasAttendanceHistory(VIEWER_ID));
    }
}
