package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
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
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
    private long creatorId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        categoryId = insertCategory(CATEGORY_NAME);
        creatorId = insertUser(CREATOR_EMAIL);
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

    private long insertImage() {
        jdbcTemplate.update(
                "INSERT INTO image (data, content_type) VALUES (?, ?)",
                new byte[] {1, 2, 3}, "image/png");
        return jdbcTemplate.queryForObject("SELECT id FROM image ORDER BY id DESC LIMIT 1", Long.class);
    }

    private static Event someEvent(final long categoryId) {
        return new Event(TITLE, "Una noche a pura guitarra", new Date(), "Av. Corrientes 1234",
                Barrio.PALERMO, null, new BigDecimal("1500"), 50, CREATOR_EMAIL, Collections.singletonList(categoryId));
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
        assertEquals(0, new BigDecimal("1500").compareTo(found.get().getPrice()));
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

    // ---- create()/update(): varias fotos ----

    @Test
    public void testCreateWithoutImagesLeavesTheGalleryEmpty() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Event found = eventDao.findById(created.getId()).get();
        assertTrue(found.getImageIds().isEmpty());
        assertNull(found.getImageId());
    }

    @Test
    public void testCreateAndFindByIdPersistsSeveralImagesInUploadOrder() {
        final long firstImageId = insertImage();
        final long secondImageId = insertImage();
        final Event event = someEvent(categoryId);
        event.setImageIds(Arrays.asList(firstImageId, secondImageId));

        final Event created = eventDao.create(event);

        final Event found = eventDao.findById(created.getId()).get();
        assertEquals(Arrays.asList(firstImageId, secondImageId), found.getImageIds());
    }

    @Test
    public void testGetImageIdReturnsTheFirstUploadedImage() {
        final long firstImageId = insertImage();
        final long secondImageId = insertImage();
        final Event event = someEvent(categoryId);
        event.setImageIds(Arrays.asList(firstImageId, secondImageId));

        final Event created = eventDao.create(event);

        assertEquals(Long.valueOf(firstImageId), eventDao.findById(created.getId()).get().getImageId());
    }

    @Test
    public void testUpdateReplacesTheWholeImageGallery() {
        final long originalImageId = insertImage();
        final Event event = someEvent(categoryId);
        event.setImageIds(Collections.singletonList(originalImageId));
        final Event created = eventDao.create(event);

        final long newImageId = insertImage();
        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setImageIds(Collections.singletonList(newImageId));
        eventDao.update(changes);

        final List<Long> found = eventDao.findById(created.getId()).get().getImageIds();
        assertEquals(Collections.singletonList(newImageId), found);
    }

    // ---- create()/update(): pin en el mapa ----

    @Test
    public void testCreateWithoutLocationPinLeavesLatitudeAndLongitudeNull() {
        final Event created = eventDao.create(someEvent(categoryId));

        final Event found = eventDao.findById(created.getId()).get();
        assertFalse(found.isLocationPinned());
        assertNull(found.getLatitude());
        assertNull(found.getLongitude());
    }

    @Test
    public void testCreateAndFindByIdPersistsTheLocationPin() {
        final Event event = someEvent(categoryId);
        event.setLatitude(-34.603722);
        event.setLongitude(-58.381592);

        final Event created = eventDao.create(event);

        final Event found = eventDao.findById(created.getId()).get();
        assertTrue(found.isLocationPinned());
        assertEquals(-34.603722, found.getLatitude(), 0.000001);
        assertEquals(-58.381592, found.getLongitude(), 0.000001);
    }

    @Test
    public void testUpdateChangesTheLocationPin() {
        final Event event = someEvent(categoryId);
        event.setLatitude(-34.603722);
        event.setLongitude(-58.381592);
        final Event created = eventDao.create(event);

        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setLatitude(-34.580000);
        changes.setLongitude(-58.420000);
        eventDao.update(changes);

        final Event found = eventDao.findById(created.getId()).get();
        assertEquals(-34.580000, found.getLatitude(), 0.000001);
        assertEquals(-58.420000, found.getLongitude(), 0.000001);
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
        changes.setPrice(new BigDecimal("2500"));
        changes.setMaxCapacity(30);
        changes.setRequiresApproval(true);
        changes.setVisibility(EventVisibility.PRIVATE);
        eventDao.update(changes);

        final Event found = eventDao.findById(created.getId()).orElseThrow(AssertionError::new);
        assertEquals("Titulo nuevo", found.getTitle());
        assertEquals("Direccion nueva 123", found.getAddress());
        assertEquals(0, new BigDecimal("2500").compareTo(found.getPrice()));
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
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion B",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
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

    // ---- findByCreatorId(): "Mis eventos" ----

    @Test
    public void testFindByCreatorIdReturnsOnlyEventsOfThatCreator() {
        final long otherCreatorId = insertUser("otro@mail.com");
        final Event own = new Event(TITLE, null, new Date(), "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        own.setCreatorId(creatorId);
        final Event other = new Event(TITLE, null, new Date(), "Direccion B",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, "otro@mail.com", Collections.singletonList(categoryId));
        other.setCreatorId(otherCreatorId);
        eventDao.create(own);
        eventDao.create(other);

        final List<Event> result = eventDao.findByCreatorId(creatorId);

        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testFindByCreatorIdOrdersMostRecentEventDateFirst() {
        final Date earlierDate = new Date(1_000_000_000_000L); // 2001-09-09
        final Date laterDate = new Date(2_000_000_000_000L);   // 2033-05-18
        final Event earlier = new Event(TITLE, null, earlierDate, "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        earlier.setCreatorId(creatorId);
        final Event later = new Event(TITLE, null, laterDate, "Direccion B",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        later.setCreatorId(creatorId);
        // A proposito insertados en orden "incorrecto".
        eventDao.create(earlier);
        eventDao.create(later);

        final List<Event> result = eventDao.findByCreatorId(creatorId);

        assertEquals(2, result.size());
        assertEquals("Direccion B", result.get(0).getAddress());
        assertEquals("Direccion A", result.get(1).getAddress());
    }

    @Test
    public void testFindByCreatorIdReturnsEmptyWhenNoEvents() {
        final List<Event> result = eventDao.findByCreatorId(creatorId);

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

    // Invitacion por mail a un evento privado. Reserva un lugar hasta que esa
    // persona se anota (ahi pasa a ser su inscripcion y deja de contarse aparte).
    private void insertEventInvitation(final long eventId, final String email) {
        jdbcTemplate.update(
                "INSERT INTO event_invitation (event_id, email, token, invited_by) VALUES (?, ?, ?, ?)",
                eventId, email, "tok-" + eventId + "-" + email, CREATOR_EMAIL);
    }

    /** Un evento futuro con ese cupo y esa cantidad de inscriptos confirmados. */
    private long eventWith(final Integer capacity, final int confirmed, final String address) {
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);
        final long id = eventDao.create(new Event(TITLE, null, future, address,
                Barrio.PALERMO, null, BigDecimal.ZERO, capacity, CREATOR_EMAIL, Collections.singletonList(categoryId))).getId();
        for (int i = 0; i < confirmed; i++) {
            insertRegistration(id, "conf" + i + "-" + id + "@mail.com", "CONFIRMED");
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
            insertRegistration(eventId, "wl" + i + "@mail.com", "WAITLISTED");
        }

        // 7 libres: los de la lista de espera no ocupan cupo, asi que NO entra.
        assertTrue(eventDao.search(fewSpotsCriteria()).isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftCountsSpotsHeldByUnusedInvitations() {
        final long eventId = eventWith(10, 3, "Con invitaciones");
        for (int i = 0; i < 4; i++) {
            insertEventInvitation(eventId, "inv" + i + "@mail.com");
        }

        // 10 - 3 confirmados - 4 invitaciones sin usar = 3 libres -> ahora SI entra.
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

    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountPendingApprovalRequests() {
        // 10 - 3 confirmados = 7 libres: un pedido pendiente NO ocupa lugar (el
        // organizador los mira a todos y acepta hasta que se llene el cupo), asi
        // que no entra en "pocos cupos". Mismo numero que availableSpots().
        final long eventId = eventWith(10, 3, "Con pendientes");
        for (int i = 0; i < 4; i++) {
            insertRegistration(eventId, "pend" + i + "@mail.com", "PENDING");
        }

        assertTrue(eventDao.search(fewSpotsCriteria()).isEmpty());
    }

    // Una invitacion que ya se uso (la persona se anoto) no se cuenta dos veces:
    // ahora es una inscripcion, y si esta confirmada ya se cuenta como tal.
    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountAnInvitationTwiceOnceThePersonRegistered() {
        final long eventId = eventWith(10, 8, "Invitacion usada");
        insertRegistration(eventId, "usada@mail.com", "CONFIRMED");
        insertEventInvitation(eventId, "usada@mail.com");

        // 10 - 9 confirmados = 1 libre (la invitacion usada no suma otro).
        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));
        assertTrue(ids.contains(eventId));
        assertEquals(1, (int) eventDao.countSearch(fewSpotsCriteria()));
    }

    // ---- search(): ordenar ----

    private static EventSearchCriteria sortedBy(final EventSort sort) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setSort(sort);
        criteria.setPageSize(20);
        return criteria;
    }

    private long eventOn(final long daysFromNow, final BigDecimal price, final String address) {
        final Date date = new Date(System.currentTimeMillis() + daysFromNow * 86_400_000L);
        return eventDao.create(new Event(TITLE, null, date, address, Barrio.PALERMO, null, price, null,
                CREATOR_EMAIL, Collections.singletonList(categoryId))).getId();
    }

    @Test
    public void testSearchSortsByDateBothWays() {
        final long enTres = eventOn(3, BigDecimal.ZERO, "En tres dias");
        final long enUno = eventOn(1, BigDecimal.ZERO, "Manana");
        final long enDos = eventOn(2, BigDecimal.ZERO, "En dos dias");

        assertEquals(Arrays.asList(enUno, enDos, enTres), idsOf(eventDao.search(sortedBy(EventSort.FECHA_ASC))));
        assertEquals(Arrays.asList(enTres, enDos, enUno), idsOf(eventDao.search(sortedBy(EventSort.FECHA_DESC))));
    }

    @Test
    public void testSearchSortsByPriceBothWays() {
        final long caro = eventOn(1, new BigDecimal("5000"), "Caro");
        final long gratis = eventOn(2, BigDecimal.ZERO, "Gratis");
        final long medio = eventOn(3, new BigDecimal("1500.50"), "Medio");

        assertEquals(Arrays.asList(gratis, medio, caro), idsOf(eventDao.search(sortedBy(EventSort.PRECIO_ASC))));
        assertEquals(Arrays.asList(caro, medio, gratis), idsOf(eventDao.search(sortedBy(EventSort.PRECIO_DESC))));
    }

    @Test
    public void testSearchSortsBySpotsLeftWithUnlimitedEventsAsInfinite() {
        final long unLibre = eventWith(10, 9, "Un libre");
        final long sinLimite = eventWith(null, 3, "Sin limite");
        final long ochoLibres = eventWith(10, 2, "Ocho libres");

        // Sin limite = lugares "infinitos": ultimo de menos a mas, primero de mas a menos.
        assertEquals(Arrays.asList(unLibre, ochoLibres, sinLimite), idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC))));
        assertEquals(Arrays.asList(sinLimite, ochoLibres, unLibre), idsOf(eventDao.search(sortedBy(EventSort.CUPOS_DESC))));
    }

    @Test
    public void testSearchSortsBySpotsLeftCountingOnlyConfirmedAndUnusedInvitations() {
        // Los dos tienen 5 confirmados, pero en "reservado" quedan 3 libres:
        // 2 invitaciones sin usar. Los pendientes de aprobacion y la lista de
        // espera NO ocupan lugar.
        final long reservado = eventWith(10, 5, "Con reservas");
        insertEventInvitation(reservado, "inv1@mail.com");
        insertEventInvitation(reservado, "inv2@mail.com");
        insertRegistration(reservado, "pend1@mail.com", "PENDING");
        insertRegistration(reservado, "pend2@mail.com", "PENDING");
        insertRegistration(reservado, "wl1@mail.com", "WAITLISTED");
        insertRegistration(reservado, "wl2@mail.com", "WAITLISTED");
        final long cincoLibres = eventWith(10, 5, "Cinco libres");

        assertEquals(Arrays.asList(reservado, cincoLibres), idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC))));
    }

    @Test
    public void testSortBySpotsCombinesWithFiltersWithoutMixingParameters() {
        // Filtro (con sus "?") + orden por cupos (con los suyos): si los parametros
        // se mezclaran de orden, la query fallaria o traeria cualquier cosa.
        final long unLibre = eventWith(10, 9, "Un libre");
        final long tresLibres = eventWith(10, 7, "Tres libres");
        eventWith(10, 1, "Nueve libres");
        final EventSearchCriteria criteria = fewSpotsCriteria();
        criteria.setSort(EventSort.CUPOS_DESC);

        assertEquals(Arrays.asList(tresLibres, unLibre), idsOf(eventDao.search(criteria)));
        assertEquals(2, eventDao.countSearch(criteria));
    }

    @Test
    public void testSearchSortedBySpotsLeavesOutSoldOutEvents() {
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");
        final long sinLimite = eventWith(null, 3, "Sin limite");

        // Los agotados no aparecen en ninguno de los dos sentidos, y el total coincide.
        assertEquals(Arrays.asList(unLibre, sinLimite), idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC))));
        assertEquals(Arrays.asList(sinLimite, unLibre), idsOf(eventDao.search(sortedBy(EventSort.CUPOS_DESC))));
        assertEquals(2, eventDao.countSearch(sortedBy(EventSort.CUPOS_ASC)));
    }

    @Test
    public void testSearchSortedByDateStillShowsSoldOutEvents() {
        eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");

        // Solo el orden por cupos los saca: ordenando por fecha siguen apareciendo.
        assertEquals(2, eventDao.search(sortedBy(EventSort.FECHA_ASC)).size());
    }

    private static EventSearchCriteria soldOutCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setOnlySoldOut(true);
        criteria.setPageSize(20);
        return criteria;
    }

    @Test
    public void testSearchWithSoldOutKeepsOnlyEventsWithoutSpotsLeft() {
        final long lleno = eventWith(10, 10, "Lleno");
        final long llenoConReservas = eventWith(10, 8, "Lleno con reservas");
        insertEventInvitation(llenoConReservas, "inv1@mail.com");
        insertEventInvitation(llenoConReservas, "inv2@mail.com");
        eventWith(10, 9, "Un libre");
        eventWith(null, 50, "Sin limite");

        final List<Long> ids = idsOf(eventDao.search(soldOutCriteria()));

        assertEquals(2, ids.size());
        assertTrue(ids.contains(lleno));
        assertTrue(ids.contains(llenoConReservas));
        assertEquals(2, eventDao.countSearch(soldOutCriteria()));
    }

    @Test
    public void testSearchWithFewSpotsAndSoldOutBringsBoth() {
        final long lleno = eventWith(10, 10, "Lleno");
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 1, "Nueve libres");
        final EventSearchCriteria criteria = soldOutCriteria();
        criteria.setOnlyFewSpotsLeft(true);

        final List<Long> ids = idsOf(eventDao.search(criteria));

        assertEquals(2, ids.size());
        assertTrue(ids.contains(lleno));
        assertTrue(ids.contains(unLibre));
    }

    @Test
    public void testSortBySpotsKeepsSoldOutEventsWhenTheSoldOutFilterIsOn() {
        final long lleno = eventWith(10, 10, "Lleno");
        final EventSearchCriteria criteria = soldOutCriteria();
        criteria.setSort(EventSort.CUPOS_ASC);

        // Pidio ver los agotados: el orden por cupos no se los tiene que sacar.
        assertEquals(Collections.singletonList(lleno), idsOf(eventDao.search(criteria)));
    }

    // ---- search(): filtro por categoria ----

    @Test
    public void testSearchByCategoryReturnsEventThatHasOneOfSeveralCategories() {
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event event = new Event(TITLE, null, new Date(System.currentTimeMillis() + 86_400_000L), "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, null);
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
        final long highRatedCreatorId = insertUser(highRatedCreator);
        final long lowRatedCreatorId = insertUser(lowRatedCreator);
        final long reviewerId = insertUser("reviewer@mail.com");
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);

        final Event highRatedEvent = new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, highRatedCreator, Collections.singletonList(categoryId));
        highRatedEvent.setCreatorId(highRatedCreatorId);
        final Event lowRatedEvent = new Event(TITLE, null, future, "Direccion B",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, lowRatedCreator, Collections.singletonList(categoryId));
        lowRatedEvent.setCreatorId(lowRatedCreatorId);
        final long highRatedEventId = eventDao.create(highRatedEvent).getId();
        final long lowRatedEventId = eventDao.create(lowRatedEvent).getId();
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
        final long highRatedCreatorId = insertUser(highRatedCreator);
        final long reviewerId = insertUser("reviewer@mail.com");
        final Date future = new Date(System.currentTimeMillis() + 86_400_000L);

        final Event event = new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, highRatedCreator, Collections.singletonList(categoryId));
        event.setCreatorId(highRatedCreatorId);
        final long eventId = eventDao.create(event).getId();
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
        final Event event = new Event(TITLE, null, future, "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId));
        event.setCreatorId(creatorId);
        final long eventId = eventDao.create(event).getId();
        insertReview(eventId, reviewerId, 3);

        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        assertTrue(eventDao.search(criteria).isEmpty());
    }

    @Test
    public void testSearchWithoutMinimumOrganizerRatingIgnoresTheFilter() {
        eventDao.create(new Event(TITLE, null, new Date(System.currentTimeMillis() + 86_400_000L), "Direccion A",
                Barrio.PALERMO, null, BigDecimal.ZERO, null, CREATOR_EMAIL, Collections.singletonList(categoryId)));

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

    // findAll/findByCreatorId SI tienen que devolver privados: "Mis eventos"
    // (findByCreatorId) es justamente donde el creador los ve y los gestiona.
    @Test
    public void testFindByCreatorIdIncludesPrivateEvents() {
        final Event privateEvent = someEvent(categoryId);
        privateEvent.setCreatorId(creatorId);
        privateEvent.setVisibility(EventVisibility.PRIVATE);
        eventDao.create(privateEvent);

        assertEquals(1, eventDao.findByCreatorId(creatorId).size());
    }

    // ---- search()/findById()/findByCreatorId(): eventos cancelados ----

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

    // findById/findByCreatorId SI tienen que devolver un evento cancelado:
    // quien ya estaba anotado tiene que poder seguir viendolo (detalle, "Mis
    // inscripciones"), y el creador tiene que seguir viendolo en "Mis eventos".
    @Test
    public void testFindByIdReturnsCancelledEvents() {
        final Event created = eventDao.create(someEvent(categoryId));
        eventDao.cancel(created.getId());

        assertTrue(eventDao.findById(created.getId()).isPresent());
    }

    @Test
    public void testFindByCreatorIdIncludesCancelledEvents() {
        final Event event = someEvent(categoryId);
        event.setCreatorId(creatorId);
        final Event created = eventDao.create(event);
        eventDao.cancel(created.getId());

        assertEquals(1, eventDao.findByCreatorId(creatorId).size());
    }

    // ---- recordatorio de 24 hs ----

    private static final long HORA = 60L * 60 * 1000;

    private long eventInHours(final long hours, final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis() + hours * HORA), "Corrientes 1234", CREATOR_EMAIL);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    @Test
    public void testFindIdsNeedingReminderOnlyReturnsEventsInTheWindowNotCancelledNorReminded() {
        final long enDiezHoras = eventInHours(10, "En diez horas");
        eventInHours(30, "En treinta horas");
        eventInHours(-2, "Ya paso");
        final long cancelado = eventInHours(5, "Cancelado");
        eventDao.cancel(cancelado);
        final long yaAvisado = eventInHours(8, "Ya avisado");
        eventDao.markReminderSent(yaAvisado, new Date());

        final Date now = new Date();
        final List<Long> ids = eventDao.findIdsNeedingReminder(now, new Date(now.getTime() + 24 * HORA));

        assertEquals(Collections.singletonList(enDiezHoras), ids);
    }

    @Test
    public void testMarkReminderSentOnlyWorksTheFirstTime() {
        final long id = eventInHours(10, "Una vez");

        assertTrue(eventDao.markReminderSent(id, new Date()));
        assertFalse(eventDao.markReminderSent(id, new Date()));
    }

    private Timestamp reminderSentAt(final long id) {
        return jdbcTemplate.queryForObject("SELECT reminder_sent_at FROM event WHERE id = ?", Timestamp.class, id);
    }

    @Test
    public void testUpdateKeepsTheReminderIfTheDateDidNotChange() {
        final Event created = eventDao.create(someEvent(categoryId));
        eventDao.markReminderSent(created.getId(), new Date());

        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setEventDate(created.getEventDate());
        changes.setTitle("Solo cambia el titulo");
        eventDao.update(changes);

        assertNotNull(reminderSentAt(created.getId()));
    }

    @Test
    public void testUpdateResetsTheReminderIfTheDateChanged() {
        final Event created = eventDao.create(someEvent(categoryId));
        eventDao.markReminderSent(created.getId(), new Date());

        final Event changes = someEvent(categoryId);
        changes.setId(created.getId());
        changes.setEventDate(new Date(created.getEventDate().getTime() + 7 * 24 * HORA));
        eventDao.update(changes);

        assertNull(reminderSentAt(created.getId()));
    }
}
