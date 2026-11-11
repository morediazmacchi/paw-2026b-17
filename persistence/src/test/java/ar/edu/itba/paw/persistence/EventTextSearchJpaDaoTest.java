package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * Tests de la barra de busqueda de /eventos (EventDao.search()/countSearch()/findRecommendedPage() con texto): todo es
 * una consulta SQL, asi que se prueba contra la base (HSQLDB en memoria con el esquema real). Arrange y Assert son SQL
 * directo; el DAO solo se toca en el Act.
 *
 * Cada evento nuevo ocurre un dia despues que el anterior: sin otro dato que lo cambie, el orden por fecha es el orden
 * en que se crearon.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventTextSearchJpaDaoTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private DataSource ds;

    @Autowired
    private EventDao eventDao;

    private JdbcTemplate jdbcTemplate;
    private long organizerId;
    private int nextDay;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", "organizador@mail.com", "Persona");
        organizerId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "organizador@mail.com");
        nextDay = 0;
    }

    // ---- arrange (SQL directo) ----

    private static Timestamp future(final int dayOffset) {
        return Timestamp.from(LocalDateTime.of(2099, 1, 1, 21, 0).plusDays(dayOffset).atZone(BUENOS_AIRES).toInstant());
    }

    private static Timestamp past() {
        return Timestamp.from(LocalDateTime.of(2020, 1, 1, 21, 0).atZone(BUENOS_AIRES).toInstant());
    }

    private static Map<String, Object> cols(final Object... kv) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // Un evento futuro, publico y aprobado (defaults), pisables. Cada uno un dia despues que el anterior.
    private long insertEvent(final Map<String, Object> overrides) {
        final Map<String, Object> row = cols(
                "title", "Evento",
                "event_date", future(nextDay++),
                "address", "Av. Corrientes 1234",
                "creator_email", "organizador@mail.com",
                "creator_id", organizerId,
                "visibility", EventVisibility.PUBLIC.name());
        row.putAll(overrides);
        jdbcTemplate.update("INSERT INTO event (" + String.join(", ", row.keySet()) + ") VALUES ("
                + row.keySet().stream().map(k -> "?").collect(Collectors.joining(", ")) + ")", row.values().toArray());
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertEvent(final String title) {
        return insertEvent(cols("title", title));
    }

    private long insertEvent(final String title, final String description) {
        return insertEvent(cols("title", title, "description", description));
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private void categorize(final long eventId, final long categoryId) {
        jdbcTemplate.update("INSERT INTO event_category (event_id, category_id) VALUES (?, ?)", eventId, categoryId);
    }

    // ---- act ----

    private static EventSearchCriteria searching(final String text, final EventSort sort) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setText(text);
        criteria.setSort(sort);
        return criteria;
    }

    // Por fecha: el orden de creacion. Sirve para probar QUE eventos entran, sin mezclar el ranking.
    private List<Long> found(final String text) {
        return ids(eventDao.search(searching(text, EventSort.FECHA_ASC)));
    }

    // Por relevancia: el orden en que se devuelve es el ranking.
    private List<Long> ranked(final String text) {
        return ids(eventDao.search(searching(text, EventSort.RELEVANCIA)));
    }

    private static List<Long> ids(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    private static List<Long> list(final Long... ids) {
        return Arrays.asList(ids);
    }

    // ---- que palabras entran ----

    @Test
    public void testSearchFindsAWordOfTheTitleIgnoringCase() {
        final long rock = insertEvent("Recital de Rock");
        insertEvent("Clase de yoga");

        assertEquals(list(rock), found("ROCK"));
    }

    @Test
    public void testSearchIgnoresAccentsOfTheEvent() {
        final long fair = insertEvent("Feria de Música");
        insertEvent("Clase de yoga");

        assertEquals(list(fair), found("musica"));
    }

    @Test
    public void testSearchIgnoresAccentsOfTheText() {
        final long party = insertEvent("Fiesta de ano nuevo");
        insertEvent("Clase de yoga");

        assertEquals(list(party), found("AÑO"));
    }

    @Test
    public void testSearchTurnsAnAccentedCapitalLetterOfTheEventIntoItsPlainLetter() {
        final long opera = insertEvent("ÓPERA Y ÑANDÚ");
        insertEvent("Clase de yoga");

        assertEquals(list(opera), found("opera nandu"));
    }

    @Test
    public void testSearchTreatsPunctuationOfTheEventAsSpaces() {
        final long rock = insertEvent("Rock&Roll, en vivo!");
        insertEvent("Clase de yoga");

        assertEquals(list(rock), found("roll vivo"));
    }

    @Test
    public void testSearchFindsAWordOfTheDescription() {
        final long night = insertEvent("Noche de sabado", "Bandas locales con musica en vivo");
        insertEvent("Clase de yoga");

        assertEquals(list(night), found("bandas"));
    }

    @Test
    public void testSearchFindsAWordInsideAnotherWord() {
        final long league = insertEvent("Rocket League");
        insertEvent("Clase de yoga");

        assertEquals(list(league), found("ocke"));
    }

    @Test
    public void testSearchFindsTheSingularWhenTheTextIsInThePlural() {
        final long band = insertEvent("Banda en vivo");
        insertEvent("Clase de yoga");

        assertEquals(list(band), found("bandas"));
    }

    @Test
    public void testSearchFindsThePluralWhenTheTextIsInTheSingular() {
        final long recitals = insertEvent("Recitales de poesia");
        insertEvent("Clase de yoga");

        assertEquals(list(recitals), found("recital"));
    }

    @Test
    public void testSearchFindsTheSingularTitleOfAnEsPluralThroughTheTypoTolerance() {
        // "recitales" pierde la s y queda "recitale": a una letra de "recital".
        final long recital = insertEvent("Recital de rock");
        insertEvent("Clase de yoga");

        assertEquals(list(recital), found("recitales"));
    }

    @Test
    public void testSearchRequiresEveryWord() {
        final long palermo = insertEvent("Rock en Palermo");
        insertEvent("Rock en Recoleta");

        assertEquals(list(palermo), found("rock palermo"));
    }

    @Test
    public void testSearchRequiresEveryWordEvenWhenTheyAreSplitBetweenTitleAndDescription() {
        final long night = insertEvent("Noche de rock", "En el corazon de Palermo");
        insertEvent("Rock en Recoleta");

        assertEquals(list(night), found("rock palermo"));
    }

    @Test
    public void testSearchOfAnEventWithoutDescriptionStillFindsItByTitle() {
        final long rock = insertEvent("Recital de Rock");

        assertEquals(list(rock), found("recital"));
    }

    @Test
    public void testSearchWithoutAnyMatchFindsNothing() {
        insertEvent("Recital de Rock");

        assertEquals(Collections.emptyList(), found("jazz"));
    }

    // ---- errores de tipeo (solo palabras de 4 letras o mas, solo en el titulo) ----

    @Test
    public void testSearchToleratesADifferentLetterInTheTitle() {
        final long rock = insertEvent("Noche de Rock");
        insertEvent("Clase de yoga");

        assertEquals(list(rock), found("rack"));
    }

    @Test
    public void testSearchToleratesAnExtraLetterInTheText() {
        final long recital = insertEvent("Recital");
        insertEvent("Clase de yoga");

        assertEquals(list(recital), found("recitall"));
    }

    @Test
    public void testSearchToleratesAMissingLetterInTheText() {
        final long blues = insertEvent("Noche de blues");
        insertEvent("Clase de yoga");

        assertEquals(list(blues), found("blus"));
    }

    @Test
    public void testSearchToleratesTwoAdjacentLettersSwapped() {
        final long rock = insertEvent("Noche de Rock");
        insertEvent("Clase de yoga");

        assertEquals(list(rock), found("rcok"));
    }

    @Test
    public void testSearchToleratesATypoIgnoringAccentsToo() {
        final long fair = insertEvent("Feria gastronómica");
        insertEvent("Clase de yoga");

        assertEquals(list(fair), found("gastronomca"));
    }

    @Test
    public void testSearchDoesNotTolerateTwoErrorsInTheSameWord() {
        insertEvent("Noche de Rock");

        assertEquals(Collections.emptyList(), found("rxcx"));
    }

    @Test
    public void testSearchRequiresAnExactMatchForAWordOfThreeLetters() {
        insertEvent("Noche de Rock");

        assertEquals(Collections.emptyList(), found("rok"));
    }

    @Test
    public void testSearchDoesNotTolerateTyposInTheDescription() {
        insertEvent("Noche de sabado", "Bandas de rock");

        assertEquals(Collections.emptyList(), found("rack"));
    }

    // ---- ranking ----

    @Test
    public void testRelevanceRanksAWordThatStartsTheTitleAboveATitleThatOnlyContainsIt() {
        final long contains = insertEvent("Corock");
        final long starts = insertEvent("Rock en vivo");

        assertEquals(list(starts, contains), ranked("rock"));
    }

    @Test
    public void testRelevanceRanksATitleThatContainsTheWordAboveATitleWithATypo() {
        final long typo = insertEvent("Rack");
        final long contains = insertEvent("Corock");

        assertEquals(list(contains, typo), ranked("rock"));
    }

    @Test
    public void testRelevanceRanksATitleWithATypoAboveAMatchInTheDescription() {
        final long description = insertEvent("Noche", "Bandas de rock");
        final long typo = insertEvent("Rack");

        assertEquals(list(typo, description), ranked("rock"));
    }

    @Test
    public void testRelevanceAddsUpTheScoreOfEveryWord() {
        // "rock vivo": en el titulo las dos = 10; una en el titulo y otra en la descripcion = 7; las dos en la descripcion = 4.
        final long both = insertEvent("Noche", "rock en vivo");
        final long mixed = insertEvent("Rock", "vivo");
        final long title = insertEvent("Rock en vivo");

        assertEquals(list(title, mixed, both), ranked("rock vivo"));
    }

    @Test
    public void testRelevanceBreaksATieWithTheEarlierDate() {
        final long later = insertEvent(cols("title", "Rock", "event_date", future(50)));
        final long earlier = insertEvent(cols("title", "Rock", "event_date", future(10)));

        assertEquals(list(earlier, later), ranked("rock"));
    }

    @Test
    public void testRelevanceBreaksATieOfSameDateWithTheLowerId() {
        final long first = insertEvent(cols("title", "Rock", "event_date", future(10)));
        final long second = insertEvent(cols("title", "Rock", "event_date", future(10)));

        assertEquals(list(first, second), ranked("rock"));
    }

    @Test
    public void testSearchByRelevanceWithoutTextOrdersByDate() {
        final long first = insertEvent("Zeta");
        final long second = insertEvent("Alfa");

        assertEquals(list(first, second), ids(eventDao.search(searching(null, EventSort.RELEVANCIA))));
    }

    // ---- junto con el resto del listado ----

    @Test
    public void testSearchOfOnlySignsDoesNotFilterAnything() {
        final long first = insertEvent("Zeta");
        final long second = insertEvent("Alfa");

        assertEquals(list(first, second), found("¿?%%--"));
    }

    @Test
    public void testSearchKeepsOnlyThePublicApprovedFutureEventsThatAreNotCancelled() {
        final long visible = insertEvent("Rock visible");
        insertEvent(cols("title", "Rock privado", "visibility", EventVisibility.PRIVATE.name()));
        insertEvent(cols("title", "Rock cancelado", "cancelled_at", past()));
        insertEvent(cols("title", "Rock pasado", "event_date", past()));
        insertEvent(cols("title", "Rock pendiente", "approval_status", "PENDING"));

        assertEquals(list(visible), found("rock"));
    }

    @Test
    public void testSearchCombinesTheTextWithTheOtherFilters() {
        final long music = insertCategory("Musica");
        final long sports = insertCategory("Deportes");
        final long rockMusic = insertEvent("Rock en vivo");
        categorize(rockMusic, music);
        final long rockSports = insertEvent("Rock y pelota");
        categorize(rockSports, sports);
        final EventSearchCriteria criteria = searching("rock", EventSort.FECHA_ASC);
        criteria.setCategoryIds(Collections.singletonList(sports));

        assertEquals(list(rockSports), ids(eventDao.search(criteria)));
    }

    @Test
    public void testSearchWithAnExplicitSortKeepsTheFilterAndUsesThatSort() {
        final long expensive = insertEvent(cols("title", "Rock caro", "price", new BigDecimal("900")));
        final long cheap = insertEvent(cols("title", "Rock barato", "price", new BigDecimal("100")));
        insertEvent(cols("title", "Clase de yoga", "price", new BigDecimal("1")));

        assertEquals(list(cheap, expensive), ids(eventDao.search(searching("rock", EventSort.PRECIO_ASC))));
    }

    @Test
    public void testCountSearchCountsTheEventsThatMatchTheText() {
        insertEvent("Rock en vivo");
        insertEvent("Rack");
        insertEvent("Clase de yoga");

        assertEquals(2L, eventDao.countSearch(searching("rock", EventSort.RELEVANCIA)));
    }

    // Puntajes 5, 4, 3, 2 y 5 en el orden de creacion: el ranking es [a, e, b, c, d]. Devuelve b y c.
    private long[] insertFiveRockEvents() {
        insertEvent("Rock uno");
        final long b = insertEvent("Corock");
        final long c = insertEvent("Rack");
        insertEvent("Noche", "rock");
        insertEvent("Rock dos");
        return new long[] {b, c};
    }

    private EventSearchCriteria secondPageOfTwoSearchingRock() {
        final EventSearchCriteria criteria = searching("rock", EventSort.RELEVANCIA);
        criteria.setPageSize(2);
        criteria.setPage(2);
        return criteria;
    }

    @Test
    public void testSearchReturnsTheRequestedPageOfTheRankedResults() {
        // Arrange
        final long[] expected = insertFiveRockEvents();

        // Act
        final List<Long> result = ids(eventDao.search(secondPageOfTwoSearchingRock()));

        // Assert
        assertEquals(list(expected[0], expected[1]), result);
    }

    @Test
    public void testCountSearchCountsEveryResultAndNotOnlyTheRequestedPage() {
        // Arrange
        insertFiveRockEvents();

        // Act
        final long count = eventDao.countSearch(secondPageOfTwoSearchingRock());

        // Assert
        assertEquals(5L, count);
    }

    // ---- recomendados ----

    @Test
    public void testRecommendedPageOnlyHasTheEventsThatMatchTheText() {
        final long viewer = insertViewerWithAnAttendedEvent();
        final long rock = insertEvent("Rock en vivo");
        insertEvent("Clase de yoga");
        final EventSearchCriteria criteria = searching("rock", EventSort.RECOMENDADOS);
        criteria.setViewerId(viewer);

        assertEquals(list(rock), ids(eventDao.findRecommendedPage(criteria, SimilarityParameters.defaults())));
    }

    // Una persona que asistio a un evento ya ocurrido (para que los recomendados tengan con que comparar).
    private long insertViewerWithAnAttendedEvent() {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", "viewer@mail.com", "Persona");
        final long viewer = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "viewer@mail.com");
        final long attended = insertEvent(cols("title", "Evento pasado", "event_date", past()));
        jdbcTemplate.update("INSERT INTO registration (event_id, email, user_id, status) VALUES (?, ?, ?, ?)",
                attended, "viewer@mail.com", viewer, "CONFIRMED");
        return viewer;
    }
}
