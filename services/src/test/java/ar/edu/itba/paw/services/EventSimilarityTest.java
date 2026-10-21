package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static ar.edu.itba.paw.services.SimilarityFixtures.ARTE;
import static ar.edu.itba.paw.services.SimilarityFixtures.DEPORTES;
import static ar.edu.itba.paw.services.SimilarityFixtures.MUSICA;
import static ar.edu.itba.paw.services.SimilarityFixtures.OTROS;
import static ar.edu.itba.paw.services.SimilarityFixtures.at;
import static ar.edu.itba.paw.services.SimilarityFixtures.event;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EventSimilarityTest {

    private static final double DELTA = 1e-9;

    // Distribucion de 4 precios: percentil 0,125 / 0,375 / 0,625 / 0,875.
    private static final PriceDistribution PRICES = PriceDistribution.of(Arrays.asList(
            new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("4000")));

    private final EventSimilarity similarity = new EventSimilarity(SimilarityParameters.defaults());

    // Un evento completo: todas las features presentes.
    private static SimilarityFixtures.Builder full(final long id) {
        return event(id).categories(MUSICA).price("2000").barrio(Barrio.PALERMO)
                .at("2026-10-23T21:00").creator(7);
    }

    private double s(final Event a, final Event b) {
        return similarity.similarity(a, b, PRICES);
    }

    // ---- S completo ----

    @Test
    public void testIdenticalEventsScoreOne() {
        assertEquals(1.0, s(full(1).build(), full(2).build()), DELTA);
    }

    @Test
    public void testSimilarityIsSymmetric() {
        final Event a = full(1).categories(ARTE).price("4000").barrio(Barrio.RECOLETA).at("2026-10-24T18:30").build();
        final Event b = full(2).price("0").barrio(Barrio.LA_BOCA).at("2026-10-21T12:00").creator(9).build();

        assertEquals(s(a, b), s(b, a), DELTA);
    }

    @Test
    public void testScoreStaysBetweenZeroAndOne() {
        final Event a = event(1).categories(MUSICA).price("4000").barrio(Barrio.NUNEZ).at("2026-10-19T09:00").creator(1).build();
        final Event b = event(2).categories(DEPORTES).price("0").barrio(Barrio.VILLA_RIACHUELO).at("2026-10-22T21:00").creator(2).build();

        final double score = s(a, b);

        assertTrue(score >= 0 && score <= 1);
    }

    @Test
    public void testOnlyTheCategoryWeightMatters() {
        final EventSimilarity onlyCategory = new EventSimilarity(SimilarityParameters.builder()
                .weightCategory(1).weightPrice(0).weightNeighborhood(0).weightHour(0).weightWeekday(0)
                .weightOrganizer(0).build());
        final Event a = full(1).categories(ARTE).build();   // {Arte, Musica}
        final Event b = full(2).price("4000").barrio(Barrio.LA_BOCA).build();   // {Musica}

        assertEquals(0.5, onlyCategory.similarity(a, b, PRICES), DELTA);
    }

    @Test
    public void testWithAllWeightsZeroTheScoreIsZeroAndNotNaN() {
        final EventSimilarity none = new EventSimilarity(SimilarityParameters.builder()
                .weightCategory(0).weightPrice(0).weightNeighborhood(0).weightHour(0).weightWeekday(0)
                .weightOrganizer(0).build());

        assertEquals(0.0, none.similarity(full(1).build(), full(2).build(), PRICES), DELTA);
    }

    // ---- features que faltan: se sacan y se renormaliza ----

    @Test
    public void testAMissingNeighborhoodIsLeftOutInsteadOfCountedAsZero() {
        final Event a = full(1).build();
        final Event b = event(2).categories(MUSICA).price("2000").at("2026-10-23T21:00").creator(7).build();

        assertEquals(1.0, s(a, b), DELTA);
    }

    @Test
    public void testAMissingOrganizerIsLeftOut() {
        final Event a = full(1).build();
        final Event b = event(2).categories(MUSICA).price("2000").barrio(Barrio.PALERMO).at("2026-10-23T21:00").build();

        assertEquals(1.0, s(a, b), DELTA);
    }

    @Test
    public void testDifferentOrganizersLowerTheScoreByTheirWeight() {
        final Event a = full(1).build();
        final Event b = full(2).creator(8).build();

        // Todo igual salvo el organizador (peso 0,15 de un total de 1).
        assertEquals(0.85, s(a, b), DELTA);
    }

    @Test
    public void testTwoEventsWithOnlyTheIgnoredCategoryHaveNoCategoryFeature() {
        // Distinto: solo la hora (12 hs = 0). Sin categoria, el total de pesos es 0,65 y
        // lo que suma es 0,50 (precio + barrio + dia + organizador).
        final Event a = event(1).categories(OTROS).price("2000").barrio(Barrio.PALERMO)
                .at("2026-10-23T21:00").creator(7).build();
        final Event b = event(2).categories(OTROS).price("2000").barrio(Barrio.PALERMO)
                .at("2026-10-23T09:00").creator(7).build();

        assertEquals(0.50 / 0.65, s(a, b), DELTA);
    }

    @Test
    public void testTheIgnoredCategoryDoesNotCountWhenComparingSets() {
        final Event a = full(1).categories(OTROS).build();   // {Musica, Otros}
        final Event b = full(2).build();                     // {Musica}

        assertEquals(1.0, s(a, b), DELTA);
    }

    @Test
    public void testOnlyTheIgnoredCategoryAgainstARealOneHasZeroCategorySimilarity() {
        final Event a = event(1).categories(OTROS).price("2000").barrio(Barrio.PALERMO).at("2026-10-23T21:00").creator(7).build();
        final Event b = full(2).build();

        // Todo igual salvo la categoria (peso 0,35).
        assertEquals(0.65, s(a, b), DELTA);
    }

    // ---- categoryScore ----

    @Test
    public void testCategoryScoreIsTheJaccardIndex() {
        assertEquals(1.0 / 3, EventSimilarity.categoryScore(set(1, 2), set(2, 3)), DELTA);
    }

    @Test
    public void testCategoryScoreOfEqualSetsIsOne() {
        assertEquals(1.0, EventSimilarity.categoryScore(set(1, 2), set(1, 2)), DELTA);
    }

    @Test
    public void testCategoryScoreOfDisjointSetsIsZero() {
        assertEquals(0.0, EventSimilarity.categoryScore(set(1), set(2)), DELTA);
    }

    @Test
    public void testCategoryScoreOfASubsetIsTheRatioOfTheirSizes() {
        assertEquals(0.5, EventSimilarity.categoryScore(set(1), set(1, 2)), DELTA);
    }

    private static Set<Long> set(final long... ids) {
        final Set<Long> set = new HashSet<>();
        for (final long id : ids) {
            set.add(id);
        }
        return set;
    }

    // ---- precio ----

    @Test
    public void testTwoFreeEventsAreIdenticalInPrice() {
        assertEquals(1.0, similarity.priceScore(event(1).price("0").build(), event(2).price("0").build(), PRICES), DELTA);
    }

    @Test
    public void testTwoEventsWithTheSamePriceAreIdenticalInPrice() {
        assertEquals(1.0, similarity.priceScore(event(1).price("3000").build(), event(2).price("3000").build(), PRICES), DELTA);
    }

    @Test
    public void testPaidPricesAreComparedByPercentileNotByPesos() {
        // 1000 -> 0,125 y 4000 -> 0,875
        assertEquals(0.25, similarity.priceScore(event(1).price("1000").build(), event(2).price("4000").build(), PRICES), DELTA);
    }

    @Test
    public void testAFreeEventIsClosestToTheCheapestPaidEvent() {
        // factor 0,5 * (1 - 0,125)
        assertEquals(0.4375, similarity.priceScore(event(1).price("0").build(), event(2).price("1000").build(), PRICES), DELTA);
    }

    @Test
    public void testAFreeEventIsFurtherFromTheMostExpensivePaidEvent() {
        // factor 0,5 * (1 - 0,875)
        assertEquals(0.0625, similarity.priceScore(event(1).price("0").build(), event(2).price("4000").build(), PRICES), DELTA);
    }

    @Test
    public void testFreeVsPaidIsSymmetric() {
        final Event free = event(1).price("0").build();
        final Event paid = event(2).price("2000").build();

        assertEquals(similarity.priceScore(free, paid, PRICES), similarity.priceScore(paid, free, PRICES), DELTA);
    }

    @Test
    public void testTheFreeVsPaidFactorIsConfigurable() {
        final EventSimilarity strict = new EventSimilarity(SimilarityParameters.builder().freeVsPaidFactor(0).build());

        assertEquals(0.0, strict.priceScore(event(1).price("0").build(), event(2).price("1000").build(), PRICES), DELTA);
    }

    // ---- barrio ----

    @Test
    public void testSameNeighborhoodIsOne() {
        assertEquals(1.0, EventSimilarity.neighborhoodScore(Barrio.PALERMO, Barrio.PALERMO), DELTA);
    }

    @Test
    public void testTheFarthestPairOfNeighborhoodsIsZero() {
        assertEquals(0.0, EventSimilarity.neighborhoodScore(Barrio.NUNEZ, Barrio.VILLA_RIACHUELO), 1e-6);
    }

    @Test
    public void testTheClosestNeighborhoodsAreAlmostIdentical() {
        // Floresta y Velez Sarsfield: ~1 km de ~16,5 km.
        final double score = EventSimilarity.neighborhoodScore(Barrio.FLORESTA, Barrio.VELEZ_SARSFIELD);

        assertTrue("fue " + score, score > 0.93 && score < 0.96);
    }

    @Test
    public void testNeighborhoodScoreIsSymmetric() {
        assertEquals(EventSimilarity.neighborhoodScore(Barrio.PALERMO, Barrio.FLORES),
                EventSimilarity.neighborhoodScore(Barrio.FLORES, Barrio.PALERMO), DELTA);
    }

    @Test
    public void testANearerNeighborhoodScoresHigherThanAFartherOne() {
        assertTrue(EventSimilarity.neighborhoodScore(Barrio.PALERMO, Barrio.COLEGIALES)
                > EventSimilarity.neighborhoodScore(Barrio.PALERMO, Barrio.VILLA_LUGANO));
    }

    // ---- hora ----

    @Test
    public void testSameHourIsOne() {
        assertEquals(1.0, EventSimilarity.hourScore(at("2026-10-23T21:00"), at("2026-10-24T21:00")), DELTA);
    }

    @Test
    public void testTwelveHoursApartIsZero() {
        assertEquals(0.0, EventSimilarity.hourScore(at("2026-10-23T21:00"), at("2026-10-23T09:00")), DELTA);
    }

    @Test
    public void testHourDistanceIsLinear() {
        // 6 hs de diferencia: la mitad.
        assertEquals(0.5, EventSimilarity.hourScore(at("2026-10-23T21:00"), at("2026-10-23T15:00")), DELTA);
    }

    @Test
    public void testHourDistanceWrapsAroundMidnight() {
        // 23:00 y 01:00 estan a 2 hs, no a 22.
        assertEquals(1 - 120.0 / 720, EventSimilarity.hourScore(at("2026-10-23T23:00"), at("2026-10-24T01:00")), DELTA);
    }

    @Test
    public void testMinutesCount() {
        assertEquals(1 - 30.0 / 720, EventSimilarity.hourScore(at("2026-10-23T21:00"), at("2026-10-23T21:30")), DELTA);
    }

    @Test
    public void testHoursAreReadInBuenosAiresTime() {
        // Las 00:00 UTC son las 21:00 del dia anterior en Buenos Aires (UTC-3).
        final Date utcMidnight = Date.from(Instant.parse("2026-10-24T00:00:00Z"));

        assertEquals(1.0, EventSimilarity.hourScore(utcMidnight, at("2026-10-23T21:00")), DELTA);
    }

    // ---- dia de la semana ----

    // 2026-10-19 es lunes.
    @Test
    public void testSameWeekdayIsOne() {
        assertEquals(1.0, EventSimilarity.weekdayScore(at("2026-10-19T10:00"), at("2026-10-26T22:00")), DELTA);
    }

    @Test
    public void testTwoWeekdaysOfTheWorkWeekAreHalf() {
        // lunes y jueves
        assertEquals(0.5, EventSimilarity.weekdayScore(at("2026-10-19T10:00"), at("2026-10-22T10:00")), DELTA);
    }

    @Test
    public void testFridayAndSundayAreBothWeekend() {
        assertEquals(0.5, EventSimilarity.weekdayScore(at("2026-10-23T10:00"), at("2026-10-25T10:00")), DELTA);
    }

    @Test
    public void testThursdayAndFridayAreDifferentGroups() {
        assertEquals(0.0, EventSimilarity.weekdayScore(at("2026-10-22T10:00"), at("2026-10-23T10:00")), DELTA);
    }

    @Test
    public void testSundayAndMondayAreDifferentGroups() {
        assertEquals(0.0, EventSimilarity.weekdayScore(at("2026-10-25T10:00"), at("2026-10-26T10:00")), DELTA);
    }

    @Test
    public void testTheWeekdayIsReadInBuenosAiresTime() {
        // El viernes 23:30 en Buenos Aires ya es sabado 02:30 UTC: tiene que seguir siendo viernes.
        final Date fridayNight = at("2026-10-23T23:30");

        assertEquals(1.0, EventSimilarity.weekdayScore(fridayNight, at("2026-10-23T10:00")), DELTA);
    }

    // ---- el maximo de Barrio ----

    @Test
    public void testTheMaxDistanceBetweenNeighborhoodsIsAboutSixteenAndAHalfKm() {
        assertTrue("fue " + Barrio.MAX_DISTANCE_KM, Barrio.MAX_DISTANCE_KM > 16 && Barrio.MAX_DISTANCE_KM < 17);
    }

    @Test
    public void testNoPairOfNeighborhoodsIsFartherThanTheMax() {
        for (final Barrio a : Barrio.values()) {
            for (final Barrio b : Barrio.values()) {
                assertTrue(a.distanceKmTo(b) <= Barrio.MAX_DISTANCE_KM + 1e-9);
            }
        }
    }

    @Test
    public void testNoCategoriesAtAllIsNotAnException() {
        // Dos eventos sin ninguna categoria: la feature se descarta, no rompe.
        assertEquals(1.0, s(event(1).price("2000").barrio(Barrio.PALERMO).creator(7).build(),
                event(2).price("2000").barrio(Barrio.PALERMO).creator(7).build()), DELTA);
    }
}
