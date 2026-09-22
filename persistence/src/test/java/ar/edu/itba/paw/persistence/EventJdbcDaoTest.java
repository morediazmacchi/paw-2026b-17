package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventVisibility;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventJdbcDaoTest {

    private static final String CATEGORY_NAME = "Musica";
    private static final String OTHER_CATEGORY_NAME = "Deportes";
    private static final String TITLE = "Recital en el bajo";
    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventDao eventDao;

    private JdbcTemplate jdbcTemplate;
    private long categoryId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        categoryId = insertCategory(CATEGORY_NAME);
    }

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Reviewer");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertReview(final long eventId, final long userId, final int rating) {
        jdbcTemplate.update("INSERT INTO review (event_id, user_id, rating) VALUES (?, ?, ?)",
                eventId, userId, rating);
    }

    private static Event someEvent(final long categoryId) {
        return new Event(TITLE, "Una noche a pura guitarra", new Date(), "Av. Corrientes 1234",
                Barrio.PALERMO, null, 1500.0, 50, CREATOR_EMAIL, Collections.singletonList(categoryId));
    }

    // ---- create() + findById() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final Event created = eventDao.create(someEvent(categoryId));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByIdReturnTheSameEvent() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Optional<Event> found = eventDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(TITLE, found.get().getTitle());
        assertEquals(Barrio.PALERMO, found.get().getNeighborhood());
        assertEquals(1500.0, found.get().getPrice(), 0.001);
        assertEquals(1, found.get().getCategories().size());
        assertEquals(CATEGORY_NAME, found.get().getCategories().get(0).getCategoryName());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        final Optional<Event> found = eventDao.findById(-1);

        assertFalse(found.isPresent());
    }

    @Test
    public void testCreateDefaultsRequiresApprovalToFalse() {
        final Event created = eventDao.create(someEvent(categoryId));

        assertFalse(eventDao.findById(created.getId()).get().isRequiresApproval());
    }

    @Test
    public void testCreateAndFindByIdPersistRequiresApprovalWhenSetToTrue() {
        final Event event = someEvent(categoryId);
        event.setRequiresApproval(true);

        final Event created = eventDao.create(event);

        assertTrue(eventDao.findById(created.getId()).get().isRequiresApproval());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithNonexistentCategoryThrowsDataAccessException() {
        eventDao.create(someEvent(categoryId + 1000));
    }

    // ---- create()/update(): varias categorias ----

    @Test
    public void testCreateAndFindByIdPersistsSeveralCategories() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event event = someEvent(categoryId);
        event.setCategories(Arrays.asList(new EventCategoryInfo(categoryId), new EventCategoryInfo(otherCategoryId)));

        final Event created = eventDao.create(event);

        final List<String> categoryNames = eventDao.findById(created.getId()).get().getCategories().stream()
                .map(EventCategoryInfo::getCategoryName).collect(Collectors.toList());
        assertEquals(2, categoryNames.size());
        assertTrue(categoryNames.contains(CATEGORY_NAME));
        assertTrue(categoryNames.contains(OTHER_CATEGORY_NAME));
    }

    @Test
    public void testCreateAndFindByIdPersistsExtraInfoPerCategory() {
        final Event event = someEvent(categoryId);
        event.setCategories(Collections.singletonList(new EventCategoryInfo(categoryId, "Toca una banda tributo")));

        final Event created = eventDao.create(event);

        final List<EventCategoryInfo> found = eventDao.findById(created.getId()).get().getCategories();
        assertEquals(1, found.size());
        assertEquals("Toca una banda tributo", found.get(0).getExtraInfo());
    }

    @Test
    public void testUpdateReplacesTheCategorySet() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event created = eventDao.create(someEvent(categoryId));

        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setCategories(Collections.singletonList(new EventCategoryInfo(otherCategoryId, "Torneo 5 contra 5")));
        eventDao.update(changes);

        final List<EventCategoryInfo> found = eventDao.findById(created.getId()).get().getCategories();
        assertEquals(1, found.size());
        assertEquals(OTHER_CATEGORY_NAME, found.get(0).getCategoryName());
        assertEquals("Torneo 5 contra 5", found.get(0).getExtraInfo());
    }

    // ---- update() ----

    @Test
    public void testUpdateChangesTheStoredFields() {
        final Event created = eventDao.create(someEvent(categoryId));
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);

        final Event changes = someEvent(otherCategoryId);
        changes.setId(created.getId());
        changes.setTitle("Titulo nuevo");
        changes.setAddress("Direccion nueva 123");
        changes.setPrice(2500.0);
        changes.setMaxCapacity(30);
        changes.setRequiresApproval(true);
        changes.setVisibility(EventVisibility.PRIVATE);
        eventDao.update(changes);

        final Event found = eventDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals("Titulo nuevo", found.getTitle());
        assertEquals("Direccion nueva 123", found.getAddress());
        assertEquals(2500.0, found.getPrice(), 0.001);
        assertEquals(Integer.valueOf(30), found.getMaxCapacity());
        assertEquals(1, found.getCategories().size());
        assertEquals(OTHER_CATEGORY_NAME, found.getCategories().get(0).getCategoryName());
        assertTrue(found.isRequiresApproval());
        assertEquals(EventVisibility.PRIVATE, found.getVisibility());
    }

    @Test
    public void testUpdateDoesNotChangeTheCreatorEmail() {
        final Event created = eventDao.create(someEvent(categoryId));

        // A proposito le paso un creatorEmail distinto: update() no deberia
        // tocar esa columna aunque el objeto Event lo traiga seteado (el DAO
        // ni siquiera lo lee -- ver la query en EventJdbcDao.update()).
        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setCreatorEmail("otro@mail.com");
        eventDao.update(changes);

        final Event found = eventDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals(CREATOR_EMAIL, found.getCreatorEmail());
    }

    @Test(expected = DataAccessException.class)
    public void testUpdateWithNonexistentCategoryThrowsDataAccessException() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Event changes = someEvent(categoryId + 1000);
        changes.setId(created.getId());
        eventDao.update(changes);
    }

    // ---- cancel() ----

    @Test
    public void testCreateDefaultsCancelledAtToNull() {
        final Event created = eventDao.create(someEvent(categoryId));

        assertFalse(eventDao.findById(created.getId()).get().isCancelled());
    }

    @Test
    public void testCancelSetsCancelledAt() {
        final Event created = eventDao.create(someEvent(categoryId));

        eventDao.cancel(created.getId());

        final Event found = eventDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertTrue(found.isCancelled());
        assertTrue(found.getCancelledAt().getTime() <= System.currentTimeMillis());
    }

    @Test
    public void testCancelDoesNotChangeOtherFields() {
        final Event created = eventDao.create(someEvent(categoryId));

        eventDao.cancel(created.getId());

        final Event found = eventDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals(TITLE, found.getTitle());
        assertEquals(CREATOR_EMAIL, found.getCreatorEmail());
    }

    @Test
    public void testCreateDefaultsVisibilityToPublic() {
        final Event created = eventDao.create(someEvent(categoryId));

        assertEquals(EventVisibility.PUBLIC, eventDao.findById(created.getId()).get().getVisibility());
    }

    @Test
    public void testCreateAndFindByIdPersistVisibilityWhenSetToPrivate() {
        final Event event = someEvent(categoryId);
        event.setVisibility(EventVisibility.PRIVATE);

        final Event created = eventDao.create(event);

        assertEquals(EventVisibility.PRIVATE, eventDao.findById(created.getId()).get().getVisibility());
    }

    // ---- findByIdForUpdate(): el SELECT ... FOR UPDATE que usa el lock de cupo ----

    @Test
    public void testFindByIdForUpdateReturnsThePresentEvent() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Optional<Event> found = eventDao.findByIdForUpdate(created.getId());

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    public void testFindByIdForUpdateReturnsEmptyWhenTheEventDoesNotExist() {
        final Optional<Event> found = eventDao.findByIdForUpdate(-1);

        assertFalse(found.isPresent());
    }

    // ---- findAll() / findByCategory() ----

    @Test
    public void testFindAllExcludesCancelledEvents() {
        final Event cancelled = eventDao.create(someEvent(categoryId));
        eventDao.cancel(cancelled.getId());
        eventDao.create(someEvent(categoryId));

        assertEquals(1, eventDao.findAll().size());
    }

    @Test
    public void testFindByCategoryExcludesCancelledEvents() {
        final Event cancelled = eventDao.create(someEvent(categoryId));
        eventDao.cancel(cancelled.getId());
        eventDao.create(someEvent(categoryId));

        assertEquals(1, eventDao.findByCategory(categoryId).size());
    }

    @Test
    public void testFindAllReturnsEventsOrderedByDate() {
        final Date earlierDate = new Date(1_000_000_000_000L); // 2001-09-09
        final Date laterDate = new Date(2_000_000_000_000L);   // 2033-05-18
        final Event later = new Event(TITLE, null, laterDate, "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion B",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        // A proposito insertados en el orden "incorrecto": el que es mas tarde primero.
        eventDao.create(later);
        eventDao.create(earlier);

        final List<Event> all = eventDao.findAll();

        assertEquals(2, all.size());
        assertEquals("Direccion B", all.get(0).getAddress());
        assertEquals("Direccion A", all.get(1).getAddress());
    }

    @Test
    public void testFindByCategoryReturnsOnlyEventsOfThatCategory() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        eventDao.create(someEvent(categoryId));
        eventDao.create(someEvent(otherCategoryId));

        final List<Event> result = eventDao.findByCategory(categoryId);

        assertEquals(1, result.size());
        assertEquals(categoryId, result.get(0).getCategories().get(0).getCategoryId());
    }

    @Test
    public void testFindByCategoryReturnsAnEventThatHasThatCategoryAmongSeveral() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event event = someEvent(categoryId);
        event.setCategories(Arrays.asList(new EventCategoryInfo(categoryId), new EventCategoryInfo(otherCategoryId)));
        eventDao.create(event);

        assertEquals(1, eventDao.findByCategory(categoryId).size());
        assertEquals(1, eventDao.findByCategory(otherCategoryId).size());
    }

    // ---- findByCreatorEmail(): "Mis eventos" ----

    @Test
    public void testFindByCreatorEmailReturnsOnlyEventsOfThatCreator() {
        final String otherEmail = "otro@mail.com";
        final Event own = new Event(TITLE, null, new Date(), "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        final Event other = new Event(TITLE, null, new Date(), "Direccion B",
                Barrio.PALERMO, null, 0, null, otherEmail, Collections.singletonList(categoryId));
        eventDao.create(own);
        eventDao.create(other);

        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testFindByCreatorEmailOrdersMostRecentEventDateFirst() {
        final Date earlierDate = new Date(1_000_000_000_000L); // 2001-09-09
        final Date laterDate = new Date(2_000_000_000_000L);   // 2033-05-18
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        final Event later = new Event(TITLE, null, laterDate, "Direccion B",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        // A proposito insertados en orden "incorrecto".
        eventDao.create(earlier);
        eventDao.create(later);

        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(2, result.size());
        assertEquals("Direccion B", result.get(0).getAddress());
        assertEquals("Direccion A", result.get(1).getAddress());
    }

    @Test
    public void testFindByCreatorEmailReturnsEmptyWhenNoEvents() {
        final List<Event> result = eventDao.findByCreatorEmail(CREATOR_EMAIL);

        assertTrue(result.isEmpty());
    }

    // ---- search(): filtro "quedan pocos cupos" ----

    private void insertRegistration(final long eventId, final String email, final String status) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, status) VALUES (?, ?, ?)",
                eventId, email, status);
    }

    private long registrationId(final long eventId, final String email) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM registration WHERE event_id = ? AND email = ?", Long.class, eventId, email);
    }

    private void insertPendingInvitation(final long registrationId) {
        jdbcTemplate.update(
                "INSERT INTO waitlist_invitation (registration_id, token, expires_at) VALUES (?, ?, ?)",
                registrationId, "tok-" + registrationId,
                new Timestamp(System.currentTimeMillis() + 86_400_000L));
    }

    /** Un evento futuro con ese cupo y esa cantidad de inscriptos confirmados. */
    private long eventWith(final Integer capacity, final int confirmed, final String address) {
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);
        final long id = eventDao.create(new Event(TITLE, null, future, address,
                Barrio.PALERMO, null, 0, capacity, CREATOR_EMAIL, Collections.singletonList(categoryId))).getId();
        for (int i = 0; i < confirmed; i++) {
            insertRegistration(id, "conf" + i + "-" + id + "@mail.com", "confirmed");
        }
        return id;
    }

    private static EventSearchCriteria fewSpotsCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setOnlyFewSpotsLeft(true);
        criteria.setPageSize(20);
        return criteria;
    }

    private static List<Long> idsOf(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    @Test
    public void testSearchWithFewSpotsLeftKeepsOnlyEventsAboutToFillUp() {
        final long cuatroLibres = eventWith(10, 6, "Cuatro libres");
        eventWith(10, 2, "Ocho libres");

        final List<Event> result = eventDao.search(fewSpotsCriteria());

        assertEquals(1, result.size());
        assertEquals(cuatroLibres, result.get(0).getId());
    }

    @Test
    public void testSearchWithFewSpotsLeftIncludesBothEndsOfTheRange() {
        final long cincoLibres = eventWith(10, 5, "Cinco libres");
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 4, "Seis libres");
        eventWith(10, 10, "Lleno");

        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));

        // 5 y 1 entran (los extremos); 6 queda afuera por arriba y 0 por abajo.
        assertEquals(2, ids.size());
        assertTrue(ids.contains(cincoLibres));
        assertTrue(ids.contains(unLibre));
    }

    @Test
    public void testSearchWithFewSpotsLeftIgnoresEventsWithoutCapacityLimit() {
        eventWith(null, 8, "Sin limite");

        // Sin cupo maximo nunca "quedan pocos": no se le pueden acabar los lugares.
        assertTrue(eventDao.search(fewSpotsCriteria()).isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountPeopleOnTheWaitlist() {
        final long eventId = eventWith(10, 3, "Con lista de espera");
        for (int i = 0; i < 4; i++) {
            insertRegistration(eventId, "wl" + i + "@mail.com", "waitlisted");
        }

        // 7 libres: los de la lista de espera no ocupan cupo, asi que NO entra.
        assertTrue(eventDao.search(fewSpotsCriteria()).isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftCountsSpotsHeldByPendingInvitations() {
        final long eventId = eventWith(10, 3, "Con invitaciones");
        for (int i = 0; i < 4; i++) {
            final String email = "wl" + i + "@mail.com";
            insertRegistration(eventId, email, "waitlisted");
            insertPendingInvitation(registrationId(eventId, email));
        }

        // 10 - 3 confirmados - 4 reservados = 3 libres -> ahora SI entra.
        // Es el mismo numero que muestra availableSpots() en la pantalla: si el
        // filtro contara distinto, el evento apareceria aca y al entrar diria otra cosa.
        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));

        assertEquals(1, ids.size());
        assertTrue(ids.contains(eventId));
    }

    @Test
    public void testCountSearchMatchesSearchWhenFilteringByFewSpotsLeft() {
        eventWith(10, 6, "Cuatro libres");
        eventWith(10, 9, "Un libre");
        eventWith(10, 1, "Nueve libres");

        // Las dos queries comparten buildWhereClause: si se desincronizaran, el
        // "N eventos encontrados" no coincidiria con lo que se ve en la pagina.
        assertEquals(eventDao.search(fewSpotsCriteria()).size(), (int) eventDao.countSearch(fewSpotsCriteria()));
        assertEquals(2, eventDao.countSearch(fewSpotsCriteria()));
    }

    // ---- search(): filtro por categoria ----

    @Test
    public void testSearchByCategoryReturnsEventThatHasOneOfSeveralCategories() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event event = new Event(TITLE, null, new Date(System.currentTimeMillis() + 86_400_000L), "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, null);
        event.setCategories(Arrays.asList(new EventCategoryInfo(categoryId), new EventCategoryInfo(otherCategoryId)));
        eventDao.create(event);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(Collections.singletonList(otherCategoryId));
        criteria.setPageSize(10);

        // Aparece una sola vez, no una por cada categoria que matchea.
        assertEquals(1, eventDao.search(criteria).size());
        assertEquals(1, eventDao.countSearch(criteria));
    }

    // ---- search(): filtro por puntaje del organizador ----

    @Test
    public void testSearchFiltersByMinimumOrganizerRating() {
        final String highRatedCreator = "alto@mail.com";
        final String lowRatedCreator = "bajo@mail.com";
        final long reviewerId = insertUser("reviewer@mail.com");
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);

        final long highRatedEventId = eventDao.create(new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, 0, null, highRatedCreator, Collections.singletonList(categoryId))).getId();
        final long lowRatedEventId = eventDao.create(new Event(TITLE, null, future, "Direccion B",
                Barrio.PALERMO, null, 0, null, lowRatedCreator, Collections.singletonList(categoryId))).getId();
        insertReview(highRatedEventId, reviewerId, 5);
        insertReview(lowRatedEventId, reviewerId, 2);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        final List<Event> result = eventDao.search(criteria);

        assertEquals(1, result.size());
        assertEquals(highRatedCreator, result.get(0).getCreatorEmail());
    }

    @Test
    public void testCountSearchMatchesSearchWhenFilteringByOrganizerRating() {
        final String highRatedCreator = "alto@mail.com";
        final long reviewerId = insertUser("reviewer@mail.com");
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);

        final long eventId = eventDao.create(new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, 0, null, highRatedCreator, Collections.singletonList(categoryId))).getId();
        insertReview(eventId, reviewerId, 5);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        assertEquals(1, eventDao.countSearch(criteria));
    }

    @Test
    public void testSearchWithMinimumOrganizerRatingExcludesCreatorsBelowIt() {
        final long reviewerId = insertUser("reviewer@mail.com");
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);
        final long eventId = eventDao.create(new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId))).getId();
        insertReview(eventId, reviewerId, 3);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        assertTrue(eventDao.search(criteria).isEmpty());
    }

    @Test
    public void testSearchWithoutMinimumOrganizerRatingIgnoresTheFilter() {
        eventDao.create(new Event(TITLE, null, new Date(System.currentTimeMillis() + 86_400_000L), "Direccion A",
                Barrio.PALERMO, null, 0, null, CREATOR_EMAIL, Collections.singletonList(categoryId)));

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        assertEquals(1, eventDao.search(criteria).size());
    }

    // ---- search(): eventos privados ----

    /**
     * EL TEST QUE IMPIDE QUE UN EVENTO PRIVADO SE FILTRE POR EL LISTADO. Un
     * evento privado no tiene que aparecer aca pase lo que pase en el resto
     * de los filtros: si esto devolviera el evento, cualquiera lo encontraria
     * buscando, sin necesitar el link directo ni estar invitado.
     */
    @Test
    public void testSearchExcludesPrivateEvents() {
        final Event privateEvent = someEvent(categoryId);
        privateEvent.setEventDate(new Date(System.currentTimeMillis() + 86_400_000L));
        privateEvent.setVisibility(EventVisibility.PRIVATE);
        eventDao.create(privateEvent);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        assertTrue(eventDao.search(criteria).isEmpty());
        assertEquals(0, eventDao.countSearch(criteria));
    }

    @Test
    public void testSearchIncludesPublicEventsAlongsidePrivateOnes() {
        final Event privateEvent = someEvent(categoryId);
        privateEvent.setEventDate(new Date(System.currentTimeMillis() + 86_400_000L));
        privateEvent.setVisibility(EventVisibility.PRIVATE);
        eventDao.create(privateEvent);

        final Event publicEvent = someEvent(categoryId);
        publicEvent.setEventDate(new Date(System.currentTimeMillis() + 86_400_000L));
        eventDao.create(publicEvent);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        assertEquals(1, eventDao.search(criteria).size());
    }

    // findAll/findByCreatorEmail SI tienen que devolver privados: "Mis eventos"
    // (findByCreatorEmail) es justamente donde el creador los ve y los gestiona.
    @Test
    public void testFindByCreatorEmailIncludesPrivateEvents() {
        final Event privateEvent = someEvent(categoryId);
        privateEvent.setVisibility(EventVisibility.PRIVATE);
        eventDao.create(privateEvent);

        assertEquals(1, eventDao.findByCreatorEmail(CREATOR_EMAIL).size());
    }

    // ---- search()/findById()/findByCreatorEmail(): eventos cancelados ----

    /**
     * EL TEST QUE IMPIDE QUE UN EVENTO CANCELADO SE FILTRE POR EL LISTADO.
     * Igual que con los privados: si esto lo devolviera, alguien podria
     * anotarse a un evento que su organizador ya dio de baja.
     */
    @Test
    public void testSearchExcludesCancelledEvents() {
        final Event event = someEvent(categoryId);
        event.setEventDate(new Date(System.currentTimeMillis() + 86_400_000L));
        final Event created = eventDao.create(event);
        eventDao.cancel(created.getId());

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        assertTrue(eventDao.search(criteria).isEmpty());
        assertEquals(0, eventDao.countSearch(criteria));
    }

    // findById/findByCreatorEmail SI tienen que devolver un evento cancelado:
    // quien ya estaba anotado tiene que poder seguir viendolo (detalle, "Mis
    // inscripciones"), y el creador tiene que seguir viendolo en "Mis eventos".
    @Test
    public void testFindByIdReturnsCancelledEvents() {
        final Event created = eventDao.create(someEvent(categoryId));
        eventDao.cancel(created.getId());

        assertTrue(eventDao.findById(created.getId()).isPresent());
    }

    @Test
    public void testFindByCreatorEmailIncludesCancelledEvents() {
        final Event created = eventDao.create(someEvent(categoryId));
        eventDao.cancel(created.getId());

        assertEquals(1, eventDao.findByCreatorEmail(CREATOR_EMAIL).size());
    }
}
