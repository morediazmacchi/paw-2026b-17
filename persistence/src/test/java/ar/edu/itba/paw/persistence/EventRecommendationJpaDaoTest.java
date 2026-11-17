package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.SimilarityParameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests de las recomendaciones (EventDao.findRecommendedPage() y findSimilarTo()): la cuenta entera es una
 * consulta SQL, asi que se prueba contra la base (HSQLDB en memoria con el esquema real). Arrange y Assert son
 * SQL directo; el DAO solo se toca en el Act.
 *
 * Para probar cada parcial de S por separado se usan parametros con UN solo peso distinto de cero: entonces S es
 * justo ese parcial y se puede fijar el piso exacto ("entra con 0,5 pero no con 0,25").
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventRecommendationJpaDaoTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private DataSource ds;

    @Autowired
    private EventDao eventDao;

    private JdbcTemplate jdbcTemplate;
    private long viewerId;
    private long organizerId;
    private long musica;
    private long deportes;
    private long arte;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        viewerId = insertUser("viewer@mail.com");
        organizerId = insertUser("organizador@mail.com");
        musica = insertCategory("Musica");
        deportes = insertCategory("Deportes");
        arte = insertCategory("Arte");
    }

    // ---- fechas: siempre en hora de Buenos Aires, lejos en el futuro (o en el pasado) ----

    // Un dia de la semana concreto de 2099 (siempre futuro), a esa hora local.
    private static Timestamp future(final DayOfWeek day, final int hour, final int minute) {
        final LocalDate date = LocalDate.of(2099, 1, 1).with(TemporalAdjusters.nextOrSame(day));
        return Timestamp.from(LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(BUENOS_AIRES).toInstant());
    }

    private static Timestamp future(final int dayOffset) {
        return Timestamp.from(LocalDateTime.of(2099, 1, 1, 21, 0).plusDays(dayOffset).atZone(BUENOS_AIRES).toInstant());
    }

    private static Timestamp past(final int daysAgo) {
        return Timestamp.from(LocalDateTime.of(2020, 1, 1, 21, 0).plusDays(daysAgo).atZone(BUENOS_AIRES).toInstant());
    }

    // ---- parametros: un solo peso encendido ----

    private static SimilarityParameters.Builder noWeights() {
        return SimilarityParameters.builder().weightCategory(0).weightPrice(0).weightNeighborhood(0)
                .weightHour(0).weightWeekday(0).weightOrganizer(0);
    }

    private static SimilarityParameters onlyCategory() {
        return noWeights().weightCategory(1).relatedMinScore(0.5).build();
    }

    // ---- arrange (SQL directo) ----

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Persona");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private static Map<String, Object> cols(final Object... kv) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // Un evento futuro, publico y aprobado (defaults), pisables.
    private long insertEvent(final Map<String, Object> overrides, final long... categories) {
        final Map<String, Object> row = cols(
                "title", "Evento",
                "event_date", future(0),
                "address", "Av. Corrientes 1234",
                "creator_email", "organizador@mail.com",
                "creator_id", organizerId,
                "visibility", EventVisibility.PUBLIC.name());
        row.putAll(overrides);
        jdbcTemplate.update("INSERT INTO event (" + String.join(", ", row.keySet()) + ") VALUES ("
                + row.keySet().stream().map(k -> "?").collect(Collectors.joining(", ")) + ")", row.values().toArray());
        final long id = jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
        for (final long category : categories) {
            jdbcTemplate.update("INSERT INTO event_category (event_id, category_id) VALUES (?, ?)", id, category);
        }
        return id;
    }

    private long insertEvent(final long... categories) {
        return insertEvent(cols(), categories);
    }

    // Un evento YA OCURRIDO al que el viewer asistio (inscripcion confirmada).
    private long attended(final Map<String, Object> overrides, final long... categories) {
        final Map<String, Object> row = cols("event_date", past(0));
        row.putAll(overrides);
        final long id = insertEvent(row, categories);
        register(id, viewerId, "viewer@mail.com", "CONFIRMED");
        return id;
    }

    private void register(final long eventId, final long userId, final String email, final String status) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, status) VALUES (?, ?, ?, ?)",
                eventId, email, userId, status);
    }

    // ---- act ----

    private EventSearchCriteria listing() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setViewerId(viewerId);
        return criteria;
    }

    private List<Long> recommended(final EventSearchCriteria criteria, final SimilarityParameters parameters) {
        return ids(eventDao.findRecommendedPage(criteria, parameters));
    }

    private List<Long> recommended(final SimilarityParameters parameters) {
        return recommended(listing(), parameters);
    }

    private List<Long> similarTo(final long eventId, final SimilarityParameters parameters) {
        return ids(eventDao.findSimilarTo(eventId, null, parameters));
    }

    private static List<Long> ids(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    private static List<Long> list(final Long... ids) {
        return Arrays.asList(ids);
    }

    // =====================================================================================================
    // findRecommendedPage()
    // =====================================================================================================

    @Test
    public void testWithoutAttendedEventsTheOrderIsByDateAndThenById() {
        // Arrange
        final long later = insertEvent(cols("event_date", future(5)), musica);
        final long sooner = insertEvent(cols("event_date", future(1)), deportes);
        final long tieA = insertEvent(cols("event_date", future(3)), arte);
        final long tieB = insertEvent(cols("event_date", future(3)), arte);

        // Act
        final List<Long> result = recommended(SimilarityParameters.defaults());

        // Assert
        assertEquals(list(sooner, tieA, tieB, later), result);
    }

    @Test
    public void testEventsSimilarToWhatWasAttendedComeFirst() {
        // Arrange: asistio a musica; el de deportes ocurre antes pero se parece menos
        attended(cols(), musica);
        final long sports = insertEvent(cols("event_date", future(1)), deportes);
        final long music = insertEvent(cols("event_date", future(9)), musica);

        // Act
        final List<Long> result = recommended(onlyCategory());

        // Assert
        assertEquals(list(music, sports), result);
    }

    @Test
    public void testEveryAttendedEventAddsItsSimilarityAndMoreOverlapOutranksLess() {
        // Arrange: dos asistidos de musica y uno de deportes. El candidato de musica suma 1 + 1; el de deportes, 1
        attended(cols("event_date", past(0)), musica);
        attended(cols("event_date", past(1)), musica);
        attended(cols("event_date", past(2)), deportes);
        final long sports = insertEvent(cols("event_date", future(1)), deportes);
        final long music = insertEvent(cols("event_date", future(9)), musica);

        // Act + Assert
        assertEquals(list(music, sports), recommended(onlyCategory()));
    }

    @Test
    public void testAnEventPartlySimilarStillAddsPointsWithoutAnyThreshold() {
        // Arrange: Jaccard {musica} vs {musica, arte} = 0,5. Suma 0,5 y le gana a uno sin nada en comun
        attended(cols(), musica);
        final long nothing = insertEvent(cols("event_date", future(1)), deportes);
        final long some = insertEvent(cols("event_date", future(9)), musica, arte);

        // Act + Assert
        assertEquals(list(some, nothing), recommended(onlyCategory()));
    }

    @Test
    public void testHowLongAgoTheEventWasAttendedDoesNotMatter() {
        // Arrange: el asistido de deportes es MUCHO mas viejo, pero pesa igual que el de musica
        attended(cols("event_date", past(2000)), deportes);
        attended(cols("event_date", past(1)), musica);
        final long music = insertEvent(cols("event_date", future(1)), musica);
        final long sports = insertEvent(cols("event_date", future(2)), deportes);

        // Act + Assert: empatan en 1 punto: gana el que ocurre antes
        assertEquals(list(music, sports), recommended(onlyCategory()));
    }

    @Test
    public void testTiesInPointsAreBrokenByDateAndThenById() {
        // Arrange
        attended(cols(), musica);
        final long later = insertEvent(cols("event_date", future(4)), musica);
        final long tieA = insertEvent(cols("event_date", future(2)), musica);
        final long tieB = insertEvent(cols("event_date", future(2)), musica);

        // Act + Assert
        assertEquals(list(tieA, tieB, later), recommended(onlyCategory()));
    }

    @Test
    public void testOnlyTheMostRecentAttendedEventsUpToTheHistorySizeAreRead() {
        // Arrange: historySize 1 -> solo cuenta el mas reciente (deportes); el viejo de musica no suma
        attended(cols("event_date", past(0)), musica);
        attended(cols("event_date", past(10)), deportes);
        final long music = insertEvent(cols("event_date", future(1)), musica);
        final long sports = insertEvent(cols("event_date", future(9)), deportes);

        // Act + Assert
        assertEquals(list(sports, music), recommended(noWeights().weightCategory(1).historySize(1).build()));
    }

    @Test
    public void testOnlyConfirmedRegistrationsToFinishedActiveEventsCountAsAttended() {
        // Arrange: nada de esto es "asistio": pendiente, lista de espera, evento futuro y evento cancelado
        final long pending = insertEvent(cols("event_date", past(0)), deportes);
        register(pending, viewerId, "viewer@mail.com", "PENDING");
        final long waitlisted = insertEvent(cols("event_date", past(1)), deportes);
        register(waitlisted, viewerId, "viewer@mail.com", "WAITLISTED");
        final long upcoming = insertEvent(cols("event_date", future(1)), deportes);
        register(upcoming, viewerId, "viewer@mail.com", "CONFIRMED");
        final long cancelled = insertEvent(cols("event_date", past(2), "cancelled_at", past(1)), deportes);
        register(cancelled, viewerId, "viewer@mail.com", "CONFIRMED");
        // ... y la unica asistencia real es de musica
        attended(cols("event_date", past(5)), musica);
        final long sports = insertEvent(cols("event_date", future(2)), deportes);
        final long music = insertEvent(cols("event_date", future(9)), musica);

        // Act: si algo de lo anterior contara, deportes empataria con musica y ganaria por fecha
        assertEquals(list(music, sports), recommended(onlyCategory()).stream()
                .filter(id -> id == music || id == sports).collect(Collectors.toList()));
    }

    @Test
    public void testAnotherPersonsAttendanceDoesNotCount() {
        // Arrange: otra persona asistio a deportes; el viewer, a musica
        final long other = insertUser("otra@mail.com");
        final long otherSports = insertEvent(cols("event_date", past(0)), deportes);
        register(otherSports, other, "otra@mail.com", "CONFIRMED");
        attended(cols("event_date", past(3)), musica);
        final long sports = insertEvent(cols("event_date", future(1)), deportes);
        final long music = insertEvent(cols("event_date", future(9)), musica);

        // Act + Assert
        assertEquals(list(music, sports), recommended(onlyCategory()));
    }

    // 3 candidatos: el mejor (misma categoria que lo asistido) es el ULTIMO por fecha. Devuelve [a, b, best].
    private long[] arrangeABestCandidateThatIsLastByDate() {
        attended(cols(), musica);
        final long a = insertEvent(cols("event_date", future(1)), deportes);
        final long b = insertEvent(cols("event_date", future(2)), deportes);
        final long best = insertEvent(cols("event_date", future(9)), musica);
        return new long[] {a, b, best};
    }

    private EventSearchCriteria pageOfTwo(final int page) {
        final EventSearchCriteria criteria = listing();
        criteria.setPageSize(2);
        criteria.setPage(page);
        return criteria;
    }

    @Test
    public void testTheFirstPageHasTheBestCandidatesOfTheWholeResultNotJustTheNearestOnes() {
        // Arrange: el mejor candidato es el ULTIMO por fecha
        final long[] candidates = arrangeABestCandidateThatIsLastByDate();

        // Act
        final List<Long> firstPage = recommended(pageOfTwo(1), onlyCategory());

        // Assert
        assertEquals(list(candidates[2], candidates[0]), firstPage);
    }

    @Test
    public void testTheSecondPageHasWhatIsLeftAfterRankingTheWholeResult() {
        // Arrange
        final long[] candidates = arrangeABestCandidateThatIsLastByDate();

        // Act
        final List<Long> secondPage = recommended(pageOfTwo(2), onlyCategory());

        // Assert
        assertEquals(list(candidates[1]), secondPage);
    }

    @Test
    public void testAPageBeyondTheLastIsEmpty() {
        // Arrange
        attended(cols(), musica);
        insertEvent(cols(), musica);
        final EventSearchCriteria criteria = listing();
        criteria.setPageSize(5);
        criteria.setPage(3);

        // Act + Assert
        assertTrue(recommended(criteria, onlyCategory()).isEmpty());
    }

    @Test
    public void testTheCandidatesFollowTheSameRulesAsTheListing() {
        // Arrange
        attended(cols(), musica);
        final long visible = insertEvent(cols(), musica);
        insertEvent(cols("visibility", EventVisibility.PRIVATE.name()), musica);
        insertEvent(cols("cancelled_at", past(0)), musica);
        insertEvent(cols("event_date", past(3)), musica);
        insertEvent(cols("approval_status", EventApprovalStatus.PENDING.name()), musica);
        insertEvent(cols("approval_status", EventApprovalStatus.REJECTED.name()), musica);
        insertEvent(cols("creator_id", viewerId), musica);

        // Act + Assert
        assertEquals(list(visible), recommended(onlyCategory()));
    }

    @Test
    public void testTheListingFiltersApplyToTheRecommendedPage() {
        // Arrange
        attended(cols(), musica);
        final long palermo = insertEvent(cols("neighborhood", Barrio.PALERMO.name()), musica);
        insertEvent(cols("neighborhood", Barrio.LA_BOCA.name()), musica);
        final EventSearchCriteria criteria = listing();
        criteria.setBarrios(Collections.singletonList(Barrio.PALERMO));

        // Act + Assert
        assertEquals(list(palermo), recommended(criteria, onlyCategory()));
    }

    @Test
    public void testCategoriesIgnoredByConfigurationDoNotMakeEventsSimilar() {
        // Arrange: "Otros" esta ignorada: compartirla no suma. El candidato solo comparte "Otros", el otro, musica
        final long otros = insertCategory("Otros");
        attended(cols(), musica, otros);
        final long sharesOnlyOtros = insertEvent(cols("event_date", future(1)), otros, deportes);
        final long sharesMusica = insertEvent(cols("event_date", future(9)), musica);

        // Act + Assert
        assertEquals(list(sharesMusica, sharesOnlyOtros), recommended(onlyCategory()));
    }

    // ---- los parciales de S, uno por uno, via el orden ----

    @Test
    public void testAClosePriceRanksAheadOfAFarPriceWhenOnlyPriceCounts() {
        // Arrange: poblacion 1000/2000/3000/4000; el asistido vale 2000
        attended(cols("price", new BigDecimal("2000")));
        final long far = insertEvent(cols("event_date", future(1), "price", new BigDecimal("4000")));
        final long close = insertEvent(cols("event_date", future(9), "price", new BigDecimal("2000")));
        insertEvent(cols("event_date", future(20), "price", new BigDecimal("1000")));
        insertEvent(cols("event_date", future(21), "price", new BigDecimal("3000")));

        // Act
        final List<Long> result = recommended(noWeights().weightPrice(1).build());

        // Assert: 2000 es el mas parecido a 2000; 4000 el menos
        assertEquals(close, (long) result.get(0));
        assertEquals(far, (long) result.get(result.size() - 1));
    }

    @Test
    public void testAFreeEventRanksAheadOfAPaidOneWhenTheAttendedWasFree() {
        // Arrange
        attended(cols("price", BigDecimal.ZERO));
        final long paid = insertEvent(cols("event_date", future(1), "price", new BigDecimal("1000")));
        final long free = insertEvent(cols("event_date", future(9), "price", BigDecimal.ZERO));

        // Act + Assert
        assertEquals(list(free, paid), recommended(noWeights().weightPrice(1).build()));
    }

    @Test
    public void testACloserNeighborhoodRanksAheadWhenOnlyNeighborhoodCounts() {
        // Arrange
        attended(cols("neighborhood", Barrio.PALERMO.name()));
        final long far = insertEvent(cols("event_date", future(1), "neighborhood", Barrio.VILLA_RIACHUELO.name()));
        final long near = insertEvent(cols("event_date", future(8), "neighborhood", Barrio.COLEGIALES.name()));
        final long same = insertEvent(cols("event_date", future(9), "neighborhood", Barrio.PALERMO.name()));

        // Act + Assert
        assertEquals(list(same, near, far), recommended(noWeights().weightNeighborhood(1).build()));
    }

    @Test
    public void testTheHourIsMeasuredInBuenosAiresTimeAndCircularly() {
        // Arrange: el asistido fue a las 23:00 locales (02:00 UTC). 01:00 local esta a 2 hs; 11:00 local, a 12.
        attended(cols("event_date", Timestamp.from(LocalDateTime.of(2020, 3, 3, 23, 0).atZone(BUENOS_AIRES).toInstant())));
        final long opposite = insertEvent(cols("event_date", future(DayOfWeek.TUESDAY, 11, 0)));
        final long circularClose = insertEvent(cols("event_date", future(DayOfWeek.WEDNESDAY, 1, 0)));
        final long same = insertEvent(cols("event_date", future(DayOfWeek.THURSDAY, 23, 0)));

        // Act + Assert
        assertEquals(list(same, circularClose, opposite), recommended(noWeights().weightHour(1).build()));
    }

    @Test
    public void testTheWeekdayGroupsWeekdaysFromWeekendDays() {
        // Arrange: el asistido fue un viernes. Viernes (mismo dia) > domingo (mismo grupo) > jueves (otro grupo)
        attended(cols("event_date", Timestamp.from(LocalDateTime.of(2020, 3, 6, 21, 0).atZone(BUENOS_AIRES).toInstant())));
        final long thursday = insertEvent(cols("event_date", future(DayOfWeek.THURSDAY, 21, 0)));
        final long sunday = insertEvent(cols("event_date", future(DayOfWeek.SUNDAY, 21, 0)));
        final long friday = insertEvent(cols("event_date", future(DayOfWeek.FRIDAY, 21, 0)));

        // Act + Assert
        assertEquals(list(friday, sunday, thursday), recommended(noWeights().weightWeekday(1).build()));
    }

    @Test
    public void testTheSameOrganizerRanksAheadWhenOnlyTheOrganizerCounts() {
        // Arrange
        final long otherOrganizer = insertUser("otro-organizador@mail.com");
        attended(cols("creator_id", otherOrganizer));
        final long different = insertEvent(cols("event_date", future(1), "creator_id", organizerId));
        final long same = insertEvent(cols("event_date", future(9), "creator_id", otherOrganizer));

        // Act + Assert
        assertEquals(list(same, different), recommended(noWeights().weightOrganizer(1).build()));
    }

    // =====================================================================================================
    // findSimilarTo()
    // =====================================================================================================

    @Test
    public void testSimilarEventsComeFromMostToLeastSimilar() {
        // Arrange: Jaccard con {musica, arte}: {musica, arte} = 1, {musica} = 0,5
        final long target = insertEvent(cols("event_date", future(0)), musica, arte);
        final long half = insertEvent(cols("event_date", future(1)), musica);
        final long full = insertEvent(cols("event_date", future(9)), musica, arte);

        // Act + Assert
        assertEquals(list(full, half), similarTo(target, onlyCategory()));
    }

    @Test
    public void testSimilarLeavesOutTheEventItself() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        final long other = insertEvent(cols(), musica);

        // Act + Assert
        assertEquals(list(other), similarTo(target, onlyCategory()));
    }

    @Test
    public void testSimilarKeepsOnlyEventsAtOrAboveTheMinimumScore() {
        // Arrange: Jaccard con {musica}: {musica, arte} = 0,5 (justo en el piso: entra), {musica, arte, deportes} = 1/3
        final long target = insertEvent(cols(), musica);
        final long atTheFloor = insertEvent(cols(), musica, arte);
        insertEvent(cols(), musica, arte, deportes);

        // Act + Assert
        assertEquals(list(atTheFloor), similarTo(target, onlyCategory()));
    }

    @Test
    public void testSimilarIsEmptyWhenNothingReachesTheMinimumScore() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        insertEvent(cols(), deportes);

        // Act + Assert
        assertTrue(similarTo(target, onlyCategory()).isEmpty());
    }

    @Test
    public void testSimilarReturnsAtMostTheConfiguredNumberOfEvents() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        final long first = insertEvent(cols("event_date", future(1)), musica);
        final long second = insertEvent(cols("event_date", future(2)), musica);
        insertEvent(cols("event_date", future(3)), musica);

        // Act + Assert
        assertEquals(list(first, second), similarTo(target, noWeights().weightCategory(1).relatedLimit(2).build()));
    }

    @Test
    public void testSimilarBreaksTiesByDateAndThenById() {
        // Arrange
        final long target = insertEvent(cols("event_date", future(0)), musica);
        final long later = insertEvent(cols("event_date", future(4)), musica);
        final long tieA = insertEvent(cols("event_date", future(2)), musica);
        final long tieB = insertEvent(cols("event_date", future(2)), musica);

        // Act + Assert
        assertEquals(list(tieA, tieB, later), similarTo(target, onlyCategory()));
    }

    @Test
    public void testSimilarLeavesOutTheViewersOwnEvents() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        insertEvent(cols("creator_id", viewerId), musica);
        final long theirs = insertEvent(cols(), musica);

        // Act
        final List<Long> forViewer = ids(eventDao.findSimilarTo(target, viewerId, onlyCategory()));

        // Assert
        assertEquals(list(theirs), forViewer);
    }

    @Test
    public void testSimilarForAnAnonymousVisitorKeepsEveryonesEvents() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        final long own = insertEvent(cols("creator_id", viewerId), musica);
        final long theirs = insertEvent(cols(), musica);

        // Act
        final List<Long> forAnonymous = ids(eventDao.findSimilarTo(target, null, onlyCategory()));

        // Assert
        assertEquals(list(own, theirs).stream().sorted().collect(Collectors.toList()),
                forAnonymous.stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void testSimilarCandidatesFollowTheSameRulesAsTheListing() {
        // Arrange
        final long target = insertEvent(cols(), musica);
        final long visible = insertEvent(cols(), musica);
        insertEvent(cols("visibility", EventVisibility.PRIVATE.name()), musica);
        insertEvent(cols("cancelled_at", past(0)), musica);
        insertEvent(cols("event_date", past(3)), musica);
        insertEvent(cols("approval_status", EventApprovalStatus.PENDING.name()), musica);

        // Act + Assert
        assertEquals(list(visible), similarTo(target, onlyCategory()));
    }

    @Test
    public void testSimilarIsEmptyForAMissingEvent() {
        // Arrange
        insertEvent(cols(), musica);

        // Act + Assert
        assertTrue(similarTo(999_999, onlyCategory()).isEmpty());
    }

    @Test
    public void testSimilarIsEmptyForAPrivateEvent() {
        // Arrange
        final long target = insertEvent(cols("visibility", EventVisibility.PRIVATE.name()), musica);
        insertEvent(cols(), musica);

        // Act + Assert
        assertTrue(similarTo(target, onlyCategory()).isEmpty());
    }

    @Test
    public void testSimilarIsEmptyForAnEventNotYetApproved() {
        // Arrange
        final long target = insertEvent(cols("approval_status", EventApprovalStatus.PENDING.name()), musica);
        insertEvent(cols(), musica);

        // Act + Assert
        assertTrue(similarTo(target, onlyCategory()).isEmpty());
    }

    // ---- la aritmetica de cada parcial, con el piso exacto ----

    @Test
    public void testAnEventWithNoComparableCategoriesDropsTheCategoryFeatureAndTheRestIsRenormalized() {
        // Arrange: pesos categoria y barrio. El target no tiene categorias, y el otro tampoco: S = solo barrio = 1
        final long target = insertEvent(cols("neighborhood", Barrio.PALERMO.name()));
        final long other = insertEvent(cols("neighborhood", Barrio.PALERMO.name()));

        // Act + Assert
        assertEquals(list(other), similarTo(target, noWeights().weightCategory(1).weightNeighborhood(1)
                .relatedMinScore(0.99).build()));
    }

    @Test
    public void testAMissingNeighborhoodDropsTheNeighborhoodFeatureInsteadOfCountingAsZero() {
        // Arrange: pesos categoria y barrio; misma categoria, el barrio falta en uno: S = 1 (no 0,5)
        final long target = insertEvent(cols(), musica);
        final long other = insertEvent(cols("neighborhood", Barrio.PALERMO.name()), musica);

        // Act + Assert
        assertEquals(list(other), similarTo(target, noWeights().weightCategory(1).weightNeighborhood(1)
                .relatedMinScore(0.99).build()));
    }

    @Test
    public void testPricePercentileGivesTheExactFloor() {
        // Arrange: poblacion 1000, 2000, 3000, 4000 -> percentiles 0,125 / 0,375 / 0,625 / 0,875.
        // S contra 1000: 2000 -> 0,75; 3000 -> 0,5 (justo en el piso); 4000 -> 0,25
        final long target = insertEvent(cols("price", new BigDecimal("1000")));
        insertEvent(cols("event_date", future(1), "price", new BigDecimal("4000")));
        final long p3000 = insertEvent(cols("event_date", future(2), "price", new BigDecimal("3000")));
        final long p2000 = insertEvent(cols("event_date", future(3), "price", new BigDecimal("2000")));

        // Act + Assert
        assertEquals(list(p2000, p3000), similarTo(target, noWeights().weightPrice(1).build()));
    }

    @Test
    public void testPricePercentileCountsHalfOfTheEventsWithTheSamePrice() {
        // Arrange: poblacion 1000, 1000, 1000, 5000 (con los pasados): 1000 queda en (0 + 3) / 2 / 4 = 0,375 y 5000 en
        // 0,875 -> S = 0,5. Si los iguales se contaran todos "por debajo" (o ninguno), S seria distinta.
        final long target = insertEvent(cols("price", new BigDecimal("1000")));
        insertEvent(cols("event_date", past(0), "price", new BigDecimal("1000")));
        insertEvent(cols("event_date", past(1), "price", new BigDecimal("1000")));
        final long expensive = insertEvent(cols("event_date", future(1), "price", new BigDecimal("5000")));

        // Act + Assert
        assertEquals(list(expensive), similarTo(target, noWeights().weightPrice(1).build()));
    }

    @Test
    public void testPricePercentileIsNotRoundedWithManyDistinctPrices() {
        // Arrange: poblacion de 7 precios distintos (1000..7000, el target incluido) -> percentiles (2i + 1) / 14.
        // Contra 3000 (0,357): 2000 y 4000 quedan a la misma distancia (S = 0,857), asi que desempata la fecha (4000
        // primero); lo mismo 1000 y 5000 (S = 0,714, 5000 primero). Si el percentil se redondeara a un decimal
        // (0,3 contra 0,2 y 0,5), 2000 pasaria a estar mas cerca que 4000 y el orden cambiaria.
        final long target = insertEvent(cols("price", new BigDecimal("3000")));
        final long p1000 = insertEvent(cols("event_date", future(4), "price", new BigDecimal("1000")));
        final long p2000 = insertEvent(cols("event_date", future(2), "price", new BigDecimal("2000")));
        final long p4000 = insertEvent(cols("event_date", future(1), "price", new BigDecimal("4000")));
        final long p5000 = insertEvent(cols("event_date", future(3), "price", new BigDecimal("5000")));
        final long p6000 = insertEvent(cols("event_date", future(5), "price", new BigDecimal("6000")));
        insertEvent(cols("event_date", future(6), "price", new BigDecimal("7000")));

        // Act + Assert: 7000 queda afuera (S = 0,429 < 0,5)
        assertEquals(list(p4000, p2000, p5000, p1000, p6000), similarTo(target, noWeights().weightPrice(1).build()));
    }

    @Test
    public void testFreeAgainstPaidIsCappedByTheFreeVsPaidFactor() {
        // Arrange: poblacion 1000..4000. Gratis contra 1000: 0,5 * (1 - 0,125) = 0,4375; contra 4000: 0,5 * 0,125 = 0,0625
        insertEvent(cols("event_date", future(11), "price", new BigDecimal("2000")));
        insertEvent(cols("event_date", future(12), "price", new BigDecimal("3000")));
        final long target = insertEvent(cols("price", BigDecimal.ZERO));
        final long cheap = insertEvent(cols("event_date", future(1), "price", new BigDecimal("1000")));
        insertEvent(cols("event_date", future(2), "price", new BigDecimal("4000")));
        final long free = insertEvent(cols("event_date", future(3), "price", BigDecimal.ZERO));

        // Act + Assert: piso 0,4: gratis (1) y el barato (0,4375); el caro (0,0625) no
        assertEquals(list(free, cheap), similarTo(target, noWeights().weightPrice(1).relatedMinScore(0.4).build()));
    }

    @Test
    public void testNeighborhoodSimilarityIsOneForTheSameAndZeroForTheFarthestPair() {
        // Arrange: Nunez y Villa Riachuelo son el par mas lejano (S = 0)
        final long target = insertEvent(cols("neighborhood", Barrio.NUNEZ.name()));
        final long same = insertEvent(cols("event_date", future(1), "neighborhood", Barrio.NUNEZ.name()));
        insertEvent(cols("event_date", future(2), "neighborhood", Barrio.VILLA_RIACHUELO.name()));

        // Act + Assert
        assertEquals(list(same), similarTo(target, noWeights().weightNeighborhood(1).relatedMinScore(0.01).build()));
    }

    @Test
    public void testHourSimilarityIsMeasuredCircularly() {
        // Arrange: 23:00 vs 01:00 (2 hs): 1 - 120/720 = 0,833; vs 11:00 (12 hs): 0
        final long target = insertEvent(cols("event_date", future(DayOfWeek.MONDAY, 23, 0)));
        final long circular = insertEvent(cols("event_date", future(DayOfWeek.TUESDAY, 1, 0)));
        insertEvent(cols("event_date", future(DayOfWeek.WEDNESDAY, 11, 0)));

        // Act + Assert
        assertEquals(list(circular), similarTo(target, noWeights().weightHour(1).relatedMinScore(0.8).build()));
    }

    @Test
    public void testAnEventAtMidnightUtcIsAtNineInTheEveningInBuenosAires() {
        // Arrange: 00:00 UTC = 21:00 en Buenos Aires. El target es a las 21:00 locales: la misma hora (S = 1)
        final long target = insertEvent(cols("event_date", future(DayOfWeek.MONDAY, 21, 0)));
        final long utcMidnight = insertEvent(cols("event_date",
                Timestamp.from(LocalDateTime.of(2099, 1, 9, 0, 0).atZone(ZoneId.of("UTC")).toInstant())));
        insertEvent(cols("event_date", future(DayOfWeek.MONDAY, 9, 0)));

        // Act + Assert
        assertEquals(list(utcMidnight), similarTo(target, noWeights().weightHour(1).relatedMinScore(0.99).build()));
    }

    @Test
    public void testWeekdaySimilarityIsOneTheSameDayAndHalfWithinTheSameGroup() {
        // Arrange: viernes. Viernes = 1; sabado y domingo = 0,5; jueves y lunes = 0
        final long target = insertEvent(cols("event_date", future(DayOfWeek.FRIDAY, 21, 0)));
        final long saturday = insertEvent(cols("event_date", future(DayOfWeek.SATURDAY, 21, 0)));
        final long friday = insertEvent(cols("event_date", future(DayOfWeek.FRIDAY, 22, 0)));
        insertEvent(cols("event_date", future(DayOfWeek.THURSDAY, 21, 0)));
        insertEvent(cols("event_date", future(DayOfWeek.MONDAY, 21, 0)));

        // Act
        final List<Long> result = similarTo(target, noWeights().weightWeekday(1).relatedMinScore(0.5).build());

        // Assert
        assertEquals(new ArrayList<>(Arrays.asList(saturday, friday)).stream().sorted().collect(Collectors.toList()),
                result.stream().sorted().collect(Collectors.toList()));
        assertEquals(friday, (long) result.get(0));
    }

    @Test
    public void testWeekdayIsRightForEveryDayOfTheWeekAcrossMonthAndYearBoundaries() {
        // Arrange: el mismo dia de la semana, en meses y anos distintos (enero y febrero entran a Zeller como
        // "13" y "14" del ano anterior; cualquier error de formula se ve aca), contra un martes de referencia
        final long target = insertEvent(cols("event_date", future(DayOfWeek.TUESDAY, 12, 0)));
        final List<Long> tuesdays = new ArrayList<>();
        for (final LocalDate date : Arrays.asList(LocalDate.of(2100, 1, 5), LocalDate.of(2100, 2, 2),
                LocalDate.of(2100, 3, 2), LocalDate.of(2100, 12, 28), LocalDate.of(2101, 1, 4),
                LocalDate.of(2104, 2, 29).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY)))) {
            assertEquals(DayOfWeek.TUESDAY, date.getDayOfWeek());
            tuesdays.add(insertEvent(cols("event_date", Timestamp.from(date.atTime(12, 0).atZone(BUENOS_AIRES).toInstant()))));
        }
        // Un domingo y un lunes de esas mismas fechas no pueden entrar (otro dia / otro grupo)
        insertEvent(cols("event_date", Timestamp.from(LocalDate.of(2100, 2, 7).atTime(12, 0).atZone(BUENOS_AIRES).toInstant())));
        insertEvent(cols("event_date", Timestamp.from(LocalDate.of(2100, 3, 1).atTime(12, 0).atZone(BUENOS_AIRES).toInstant())));

        // Act: piso 1 -> solo martes
        final List<Long> result = similarTo(target, noWeights().weightWeekday(1).relatedLimit(50).relatedMinScore(1.0).build());

        // Assert
        assertEquals(tuesdays.stream().sorted().collect(Collectors.toList()),
                result.stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void testTheSameOrganizerIsOneAndADifferentOneIsZero() {
        // Arrange
        final long other = insertUser("otro@mail.com");
        final long target = insertEvent(cols("creator_id", organizerId));
        final long same = insertEvent(cols("event_date", future(1), "creator_id", organizerId));
        insertEvent(cols("event_date", future(2), "creator_id", other));

        // Act + Assert
        assertEquals(list(same), similarTo(target, noWeights().weightOrganizer(1).relatedMinScore(0.5).build()));
    }

    @Test
    public void testWeightsCombineAsAWeightedAverage() {
        // Arrange: categoria (peso 3) y organizador (peso 1). Misma categoria, otro organizador: S = 3 / 4 = 0,75
        final long other = insertUser("otro@mail.com");
        final long target = insertEvent(cols("creator_id", organizerId), musica);
        final long sameCategory = insertEvent(cols("creator_id", other), musica);
        insertEvent(cols("creator_id", organizerId), deportes);
        final SimilarityParameters parameters = noWeights().weightCategory(3).weightOrganizer(1).relatedMinScore(0.75).build();

        // Act + Assert: el de otra categoria pero mismo organizador da 1 / 4 = 0,25 y no entra
        assertEquals(list(sameCategory), similarTo(target, parameters));
    }

    @Test
    public void testTheDefaultParametersRunAgainstTheRealSchema() {
        // Arrange: una corrida completa con los coeficientes por defecto (los seis pesos, "Otros" ignorada)
        insertCategory("Otros");
        attended(cols("price", new BigDecimal("1500"), "neighborhood", Barrio.PALERMO.name()), musica);
        final long music = insertEvent(cols("price", new BigDecimal("1500"), "neighborhood", Barrio.PALERMO.name()), musica);

        // Act + Assert
        assertEquals(list(music), recommended(SimilarityParameters.defaults()));
    }
}
