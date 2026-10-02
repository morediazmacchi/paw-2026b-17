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
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Unidad 5 - Unit Testing: eventDao (el objeto bajo test) solo se toca en el
// Act. Arrange/Assert son siempre SQL directo (jdbcTemplate/JdbcTestUtils),
// nunca otro metodo de eventDao -- ni siquiera findById() para comprobar un
// create(): eso probaria DOS metodos en un solo test.
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventJdbcDaoTest {

    private static final String CATEGORY_NAME = "Musica";
    private static final String OTHER_CATEGORY_NAME = "Deportes";
    private static final String TITLE = "Recital en el bajo";
    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final long HORA = 60L * 60 * 1000;

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

    // ---- helpers de arrange/assert (SQL directo, nunca eventDao) ----

    private long insertCategory(final String name) {
        jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name);
        return jdbcTemplate.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Reviewer");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertImage() {
        jdbcTemplate.update("INSERT INTO image (data, content_type) VALUES (?, ?)", new byte[] {1, 2, 3}, "image/png");
        return jdbcTemplate.queryForObject("SELECT id FROM image ORDER BY id DESC LIMIT 1", Long.class);
    }

    private static Map<String, Object> cols(final Object... kv) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    // Insert de evento por SQL directo, con defaults razonables (futuro,
    // publico) pisables por "overrides". Asi cada Arrange solo escribe las
    // columnas que le importan a ESE test.
    private long insertEvent(final Map<String, Object> overrides) {
        final Map<String, Object> row = cols(
                "title", TITLE,
                "event_date", new Timestamp(System.currentTimeMillis() + 86_400_000L),
                "address", "Av. Corrientes 1234",
                "creator_email", CREATOR_EMAIL,
                "visibility", EventVisibility.PUBLIC.name());
        row.putAll(overrides);
        final String columnList = String.join(", ", row.keySet());
        final String placeholders = row.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        jdbcTemplate.update("INSERT INTO event (" + columnList + ") VALUES (" + placeholders + ")", row.values().toArray());
        return jdbcTemplate.queryForObject("SELECT id FROM event ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertEvent() {
        return insertEvent(cols());
    }

    private void insertEventCategory(final long eventId, final long categoryId, final String extraInfo) {
        jdbcTemplate.update("INSERT INTO event_category (event_id, category_id, extra_info) VALUES (?, ?, ?)",
                eventId, categoryId, extraInfo);
    }

    private void insertEventImage(final long eventId, final long imageId) {
        jdbcTemplate.update("INSERT INTO event_image (event_id, image_id) VALUES (?, ?)", eventId, imageId);
    }

    private void insertRegistration(final long eventId, final String email, final String status) {
        jdbcTemplate.update("INSERT INTO registration (event_id, email, status) VALUES (?, ?, ?)",
                eventId, email, status);
    }

    // Invitacion por mail a un evento privado. Reserva un lugar hasta que esa
    // persona se anota (ahi pasa a ser su inscripcion y deja de contarse aparte).
    private void insertEventInvitation(final long eventId, final String email) {
        jdbcTemplate.update(
                "INSERT INTO event_invitation (event_id, email, invited_by) VALUES (?, ?, ?)",
                eventId, email, CREATOR_EMAIL);
    }

    private void insertReview(final long eventId, final long userId, final int rating) {
        jdbcTemplate.update("INSERT INTO review (event_id, user_id, rating) VALUES (?, ?, ?)", eventId, userId, rating);
    }

    /** Un evento futuro con ese cupo y esa cantidad de inscriptos confirmados, insertado por SQL directo. */
    private long eventWith(final Integer capacity, final int confirmed, final String address) {
        final long id = insertEvent(cols("address", address, "max_capacity", capacity));
        for (int i = 0; i < confirmed; i++) {
            insertRegistration(id, "conf" + i + "-" + id + "@mail.com", "CONFIRMED");
        }
        return id;
    }

    // ---- objetos de entrada para create()/update() (el Act en si) ----

    private static Event someEvent(final long categoryId) {
        return new Event(TITLE, "Una noche a pura guitarra", new Date(), "Av. Corrientes 1234",
                Barrio.PALERMO, null, new BigDecimal("1500"), 50, CREATOR_EMAIL, Collections.singletonList(categoryId));
    }

    private static Event someFutureEvent(final long categoryId) {
        return new Event(TITLE, "desc", new Date(System.currentTimeMillis() + 86_400_000L), "Av. Corrientes 1234",
                Barrio.PALERMO, null, new BigDecimal("1500"), 50, CREATOR_EMAIL, Collections.singletonList(categoryId));
    }

    private static EventSearchCriteria fewSpotsCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setOnlyFewSpotsLeft(true);
        criteria.setPageSize(20);
        return criteria;
    }

    private static EventSearchCriteria soldOutCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setOnlySoldOut(true);
        criteria.setPageSize(20);
        return criteria;
    }

    private static EventSearchCriteria sortedBy(final EventSort sort) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setSort(sort);
        criteria.setPageSize(20);
        return criteria;
    }

    private static List<Long> idsOf(final List<Event> events) {
        return events.stream().map(Event::getId).collect(Collectors.toList());
    }

    // ==================================================================
    // create()
    // ==================================================================

    @Test
    public void testCreateAssignsAGeneratedId() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateInsertsTheBasicFields() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, neighborhood, price FROM event WHERE id = ?", created.getId());
        assertEquals(TITLE, row.get("title"));
        assertEquals("PALERMO", row.get("neighborhood"));
        assertEquals(0, new BigDecimal("1500").compareTo((BigDecimal) row.get("price")));
    }

    @Test
    public void testCreateInsertsTheGivenCategory() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_category", "event_id = " + created.getId() + " AND category_id = " + categoryId));
    }

    @Test
    public void testCreateDefaultsRequiresApprovalToFalse() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Boolean requiresApproval = jdbcTemplate.queryForObject(
                "SELECT requires_approval FROM event WHERE id = ?", Boolean.class, created.getId());
        assertFalse(requiresApproval);
    }

    @Test
    public void testCreatePersistsRequiresApprovalWhenSetToTrue() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setRequiresApproval(true);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Boolean requiresApproval = jdbcTemplate.queryForObject(
                "SELECT requires_approval FROM event WHERE id = ?", Boolean.class, created.getId());
        assertTrue(requiresApproval);
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithNonexistentCategoryThrowsDataAccessException() {
        // Arrange
        final Event toCreate = someEvent(categoryId + 1000);

        // Act
        eventDao.create(toCreate);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ==================================================================
    // create(): varias categorias
    // ==================================================================

    @Test
    public void testCreatePersistsSeveralCategories() {
        // Arrange
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final Event toCreate = someEvent(categoryId);
        toCreate.setCategories(Arrays.asList(new EventCategoryInfo(categoryId), new EventCategoryInfo(otherCategoryId)));

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final List<Long> categoryIds = jdbcTemplate.queryForList(
                "SELECT category_id FROM event_category WHERE event_id = ?", Long.class, created.getId());
        assertEquals(2, categoryIds.size());
        assertTrue(categoryIds.contains(categoryId));
        assertTrue(categoryIds.contains(otherCategoryId));
    }

    @Test
    public void testCreatePersistsExtraInfoPerCategory() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setCategories(Collections.singletonList(new EventCategoryInfo(categoryId, "Toca una banda tributo")));

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final String extraInfo = jdbcTemplate.queryForObject(
                "SELECT extra_info FROM event_category WHERE event_id = ? AND category_id = ?",
                String.class, created.getId(), categoryId);
        assertEquals("Toca una banda tributo", extraInfo);
    }

    // ==================================================================
    // update(): categorias
    // ==================================================================

    @Test
    public void testUpdateReplacesTheCategorySet() {
        // Arrange
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final long id = insertEvent();
        insertEventCategory(id, categoryId, null);
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setCategories(Collections.singletonList(new EventCategoryInfo(otherCategoryId, "Torneo 5 contra 5")));

        // Act
        eventDao.update(changes);

        // Assert
        final List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT category_id, extra_info FROM event_category WHERE event_id = ?", id);
        assertEquals(1, rows.size());
        assertEquals(otherCategoryId, ((Number) rows.get(0).get("category_id")).longValue());
        assertEquals("Torneo 5 contra 5", rows.get(0).get("extra_info"));
    }

    // ==================================================================
    // create()/update(): fotos
    // ==================================================================

    @Test
    public void testCreateWithoutImagesLeavesTheGalleryEmpty() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_image", "event_id = " + created.getId()));
    }

    @Test
    public void testCreatePersistsSeveralImagesInUploadOrder() {
        // Arrange
        final long firstImageId = insertImage();
        final long secondImageId = insertImage();
        final Event toCreate = someEvent(categoryId);
        toCreate.setImageIds(Arrays.asList(firstImageId, secondImageId));

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final List<Long> imageIds = jdbcTemplate.queryForList(
                "SELECT image_id FROM event_image WHERE event_id = ? ORDER BY id ASC", Long.class, created.getId());
        assertEquals(Arrays.asList(firstImageId, secondImageId), imageIds);
    }

    @Test
    public void testUpdateReplacesTheWholeImageGallery() {
        // Arrange
        final long originalImageId = insertImage();
        final long id = insertEvent();
        insertEventImage(id, originalImageId);
        final long newImageId = insertImage();
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setImageIds(Collections.singletonList(newImageId));

        // Act
        eventDao.update(changes);

        // Assert
        final List<Long> imageIds = jdbcTemplate.queryForList(
                "SELECT image_id FROM event_image WHERE event_id = ? ORDER BY id ASC", Long.class, id);
        assertEquals(Collections.singletonList(newImageId), imageIds);
    }

    // ==================================================================
    // create()/update(): pin en el mapa
    // ==================================================================

    @Test
    public void testCreateWithoutLocationPinLeavesLatitudeAndLongitudeNull() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT latitude, longitude FROM event WHERE id = ?", created.getId());
        assertNull(row.get("latitude"));
        assertNull(row.get("longitude"));
    }

    @Test
    public void testCreatePersistsTheLocationPin() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setLatitude(-34.603722);
        toCreate.setLongitude(-58.381592);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT latitude, longitude FROM event WHERE id = ?", created.getId());
        assertEquals(-34.603722, ((BigDecimal) row.get("latitude")).doubleValue(), 0.000001);
        assertEquals(-58.381592, ((BigDecimal) row.get("longitude")).doubleValue(), 0.000001);
    }

    @Test
    public void testUpdateChangesTheLocationPin() {
        // Arrange
        final long id = insertEvent(cols("latitude", new BigDecimal("-34.603722"), "longitude", new BigDecimal("-58.381592")));
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setLatitude(-34.580000);
        changes.setLongitude(-58.420000);

        // Act
        eventDao.update(changes);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT latitude, longitude FROM event WHERE id = ?", id);
        assertEquals(-34.580000, ((BigDecimal) row.get("latitude")).doubleValue(), 0.000001);
        assertEquals(-58.420000, ((BigDecimal) row.get("longitude")).doubleValue(), 0.000001);
    }

    // ==================================================================
    // create()/update(): detalle de direccion (piso/depto/referencias)
    // ==================================================================

    @Test
    public void testCreateWithoutAddressDetailsLeavesItNull() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final String addressDetails = jdbcTemplate.queryForObject(
                "SELECT address_details FROM event WHERE id = ?", String.class, created.getId());
        assertNull(addressDetails);
    }

    @Test
    public void testCreatePersistsTheAddressDetails() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setAddressDetails("Piso 3, depto B, tocar timbre 3B");

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final String addressDetails = jdbcTemplate.queryForObject(
                "SELECT address_details FROM event WHERE id = ?", String.class, created.getId());
        assertEquals("Piso 3, depto B, tocar timbre 3B", addressDetails);
    }

    @Test
    public void testUpdateChangesTheAddressDetails() {
        // Arrange
        final long id = insertEvent(cols("address_details", "Piso 3, depto B"));
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setAddressDetails("Entrada por el fondo");

        // Act
        eventDao.update(changes);

        // Assert
        final String addressDetails = jdbcTemplate.queryForObject(
                "SELECT address_details FROM event WHERE id = ?", String.class, id);
        assertEquals("Entrada por el fondo", addressDetails);
    }

    // ==================================================================
    // create()/update(): locationManual (pin a mano vs geocodificado)
    // ==================================================================

    @Test
    public void testCreateDefaultsLocationManualToTrue() {
        // Arrange
        // El modelo ya nace en true (Event.locationManual); esto confirma que
        // create() no lo pisa a false por accidente.
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Boolean locationManual = jdbcTemplate.queryForObject(
                "SELECT location_manual FROM event WHERE id = ?", Boolean.class, created.getId());
        assertTrue(locationManual);
    }

    @Test
    public void testCreatePersistsLocationManualFalse() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setLocationManual(false);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Boolean locationManual = jdbcTemplate.queryForObject(
                "SELECT location_manual FROM event WHERE id = ?", Boolean.class, created.getId());
        assertFalse(locationManual);
    }

    @Test
    public void testUpdateChangesLocationManual() {
        // Arrange
        final long id = insertEvent(cols("location_manual", false));
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setLocationManual(true);

        // Act
        eventDao.update(changes);

        // Assert
        final Boolean locationManual = jdbcTemplate.queryForObject(
                "SELECT location_manual FROM event WHERE id = ?", Boolean.class, id);
        assertTrue(locationManual);
    }

    // ==================================================================
    // update(): campos generales
    // ==================================================================

    @Test
    public void testUpdateChangesTheStoredFields() {
        // Arrange
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final long id = insertEvent();
        insertEventCategory(id, categoryId, null);
        final Event changes = someEvent(otherCategoryId);
        changes.setId(id);
        changes.setTitle("Titulo nuevo");
        changes.setAddress("Direccion nueva 123");
        changes.setPrice(new BigDecimal("2500"));
        changes.setMaxCapacity(30);
        changes.setRequiresApproval(true);
        changes.setVisibility(EventVisibility.PRIVATE);

        // Act
        eventDao.update(changes);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, address, price, max_capacity, requires_approval, visibility FROM event WHERE id = ?", id);
        assertEquals("Titulo nuevo", row.get("title"));
        assertEquals("Direccion nueva 123", row.get("address"));
        assertEquals(0, new BigDecimal("2500").compareTo((BigDecimal) row.get("price")));
        assertEquals(30, row.get("max_capacity"));
        assertTrue((Boolean) row.get("requires_approval"));
        assertEquals("PRIVATE", row.get("visibility"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_category", "event_id = " + id + " AND category_id = " + otherCategoryId));
    }

    @Test
    public void testUpdateDoesNotChangeTheCreatorEmail() {
        // Arrange
        final long id = insertEvent(cols("creator_email", CREATOR_EMAIL));
        // A proposito le paso un creatorEmail distinto: update() no deberia
        // tocar esa columna aunque el objeto Event lo traiga seteado (el DAO
        // ni siquiera lo lee -- ver la query en EventJdbcDao.update()).
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setCreatorEmail("otro@mail.com");

        // Act
        eventDao.update(changes);

        // Assert
        final String creatorEmail = jdbcTemplate.queryForObject(
                "SELECT creator_email FROM event WHERE id = ?", String.class, id);
        assertEquals(CREATOR_EMAIL, creatorEmail);
    }

    @Test(expected = DataAccessException.class)
    public void testUpdateWithNonexistentCategoryThrowsDataAccessException() {
        // Arrange
        final long id = insertEvent();
        final Event changes = someEvent(categoryId + 1000);
        changes.setId(id);

        // Act
        eventDao.update(changes);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ==================================================================
    // cancel() / removeByAdmin()
    // ==================================================================

    @Test
    public void testCreateDefaultsCancelledAtToNull() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final Timestamp cancelledAt = jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM event WHERE id = ?", Timestamp.class, created.getId());
        assertNull(cancelledAt);
    }

    @Test
    public void testCancelSetsCancelledAt() {
        // Arrange
        final long id = insertEvent();

        // Act
        eventDao.cancel(id);

        // Assert
        final Timestamp cancelledAt = jdbcTemplate.queryForObject(
                "SELECT cancelled_at FROM event WHERE id = ?", Timestamp.class, id);
        assertNotNull(cancelledAt);
        assertTrue(cancelledAt.getTime() <= System.currentTimeMillis());
    }

    @Test
    public void testCancelDoesNotChangeOtherFields() {
        // Arrange
        final long id = insertEvent(cols("title", TITLE, "creator_email", CREATOR_EMAIL));

        // Act
        eventDao.cancel(id);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT title, creator_email FROM event WHERE id = ?", id);
        assertEquals(TITLE, row.get("title"));
        assertEquals(CREATOR_EMAIL, row.get("creator_email"));
    }

    @Test
    public void testCancelDoesNotSetRemovedByAdmin() {
        // Arrange
        final long id = insertEvent();

        // Act
        eventDao.cancel(id);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT removed_by_admin, removed_reason FROM event WHERE id = ?", id);
        assertFalse((Boolean) row.get("removed_by_admin"));
        assertNull(row.get("removed_reason"));
    }

    @Test
    public void testRemoveByAdminSetsCancelledAtAndTheAdminFields() {
        // Arrange
        final long id = insertEvent();

        // Act
        eventDao.removeByAdmin(id, "Contenido inapropiado");

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT cancelled_at, removed_by_admin, removed_reason FROM event WHERE id = ?", id);
        assertNotNull(row.get("cancelled_at"));
        assertTrue((Boolean) row.get("removed_by_admin"));
        assertEquals("Contenido inapropiado", row.get("removed_reason"));
    }

    // ==================================================================
    // visibility
    // ==================================================================

    @Test
    public void testCreateDefaultsVisibilityToPublic() {
        // Arrange
        final Event toCreate = someEvent(categoryId);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final String visibility = jdbcTemplate.queryForObject(
                "SELECT visibility FROM event WHERE id = ?", String.class, created.getId());
        assertEquals("PUBLIC", visibility);
    }

    @Test
    public void testCreatePersistsVisibilityWhenSetToPrivate() {
        // Arrange
        final Event toCreate = someEvent(categoryId);
        toCreate.setVisibility(EventVisibility.PRIVATE);

        // Act
        final Event created = eventDao.create(toCreate);

        // Assert
        final String visibility = jdbcTemplate.queryForObject(
                "SELECT visibility FROM event WHERE id = ?", String.class, created.getId());
        assertEquals("PRIVATE", visibility);
    }

    // ==================================================================
    // findById()
    // ==================================================================

    @Test
    public void testFindByIdReturnsTheStoredEventWithItsCategory() {
        // Arrange
        final long id = insertEvent();
        insertEventCategory(id, categoryId, null);

        // Act
        final Optional<Event> found = eventDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(TITLE, found.get().getTitle());
        assertEquals(1, found.get().getCategories().size());
        assertEquals(CATEGORY_NAME, found.get().getCategories().get(0).getCategoryName());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange: no se inserta ningun evento.

        // Act
        final Optional<Event> found = eventDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    @Test
    public void testFindByIdReturnsCancelledEvents() {
        // Arrange
        // findById SI tiene que devolver un evento cancelado: quien ya estaba
        // anotado tiene que poder seguir viendolo.
        final long id = insertEvent(cols("cancelled_at", new Timestamp(System.currentTimeMillis())));

        // Act
        final Optional<Event> found = eventDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
    }

    // ==================================================================
    // findByIdForUpdate(): el SELECT ... FOR UPDATE que usa el lock de cupo
    // ==================================================================

    @Test
    public void testFindByIdForUpdateReturnsThePresentEvent() {
        // Arrange
        final long id = insertEvent();

        // Act
        final Optional<Event> found = eventDao.findByIdForUpdate(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    public void testFindByIdForUpdateReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange: no se inserta ningun evento.

        // Act
        final Optional<Event> found = eventDao.findByIdForUpdate(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    // ==================================================================
    // findByCreatorId(): "Mis eventos"
    // ==================================================================

    @Test
    public void testFindByCreatorIdReturnsOnlyEventsOfThatCreator() {
        // Arrange
        final long otherCreatorId = insertUser("otro@mail.com");
        insertEvent(cols("address", "Direccion A", "creator_email", CREATOR_EMAIL, "creator_id", creatorId));
        insertEvent(cols("address", "Direccion B", "creator_email", "otro@mail.com", "creator_id", otherCreatorId));

        // Act
        final List<Event> result = eventDao.findByCreatorId(creatorId);

        // Assert
        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testFindByCreatorIdOrdersMostRecentEventDateFirst() {
        // Arrange
        final Timestamp earlierDate = new Timestamp(1_000_000_000_000L);
        final Timestamp laterDate = new Timestamp(2_000_000_000_000L);
        // A proposito insertados en orden "incorrecto".
        insertEvent(cols("address", "Direccion A", "event_date", earlierDate, "creator_id", creatorId));
        insertEvent(cols("address", "Direccion B", "event_date", laterDate, "creator_id", creatorId));

        // Act
        final List<Event> result = eventDao.findByCreatorId(creatorId);

        // Assert
        assertEquals(2, result.size());
        assertEquals("Direccion B", result.get(0).getAddress());
        assertEquals("Direccion A", result.get(1).getAddress());
    }

    @Test
    public void testFindByCreatorIdReturnsEmptyWhenNoEvents() {
        // Arrange: no se inserta ningun evento de este creador.

        // Act
        final List<Event> result = eventDao.findByCreatorId(creatorId);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindByCreatorIdIncludesPrivateEvents() {
        // Arrange
        // findByCreatorId SI tiene que devolver privados: "Mis eventos" es
        // justamente donde el creador los ve y los gestiona.
        insertEvent(cols("creator_id", creatorId, "visibility", EventVisibility.PRIVATE.name()));

        // Act
        final List<Event> result = eventDao.findByCreatorId(creatorId);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    public void testFindByCreatorIdIncludesCancelledEvents() {
        // Arrange
        insertEvent(cols("creator_id", creatorId, "cancelled_at", new Timestamp(System.currentTimeMillis())));

        // Act
        final List<Event> result = eventDao.findByCreatorId(creatorId);

        // Assert
        assertEquals(1, result.size());
    }

    // ==================================================================
    // search(): filtro "quedan pocos cupos"
    // ==================================================================

    @Test
    public void testSearchWithFewSpotsLeftKeepsOnlyEventsAboutToFillUp() {
        // Arrange
        final long cuatroLibres = eventWith(10, 6, "Cuatro libres");
        eventWith(10, 2, "Ocho libres");

        // Act
        final List<Event> result = eventDao.search(fewSpotsCriteria());

        // Assert
        assertEquals(1, result.size());
        assertEquals(cuatroLibres, result.get(0).getId());
    }

    @Test
    public void testSearchWithFewSpotsLeftIncludesBothEndsOfTheRange() {
        // Arrange
        final long cincoLibres = eventWith(10, 5, "Cinco libres");
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 4, "Seis libres");
        eventWith(10, 10, "Lleno");

        // Act
        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));

        // Assert
        // 5 y 1 entran (los extremos); 6 queda afuera por arriba y 0 por abajo.
        assertEquals(2, ids.size());
        assertTrue(ids.contains(cincoLibres));
        assertTrue(ids.contains(unLibre));
    }

    @Test
    public void testSearchWithFewSpotsLeftIgnoresEventsWithoutCapacityLimit() {
        // Arrange
        eventWith(null, 8, "Sin limite");

        // Act
        final List<Event> result = eventDao.search(fewSpotsCriteria());

        // Assert
        // Sin cupo maximo nunca "quedan pocos": no se le pueden acabar los lugares.
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountPeopleOnTheWaitlist() {
        // Arrange
        final long eventId = eventWith(10, 3, "Con lista de espera");
        for (int i = 0; i < 4; i++) {
            insertRegistration(eventId, "wl" + i + "@mail.com", "WAITLISTED");
        }

        // Act
        final List<Event> result = eventDao.search(fewSpotsCriteria());

        // Assert
        // 7 libres: los de la lista de espera no ocupan cupo, asi que NO entra.
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftCountsSpotsHeldByUnusedInvitations() {
        // Arrange
        final long eventId = eventWith(10, 3, "Con invitaciones");
        for (int i = 0; i < 4; i++) {
            insertEventInvitation(eventId, "inv" + i + "@mail.com");
        }

        // Act
        // 10 - 3 confirmados - 4 invitaciones sin usar = 3 libres -> ahora SI entra.
        // Es el mismo numero que muestra availableSpots() en la pantalla: si el
        // filtro contara distinto, el evento apareceria aca y al entrar diria otra cosa.
        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));

        // Assert
        assertEquals(1, ids.size());
        assertTrue(ids.contains(eventId));
    }

    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountPendingApprovalRequests() {
        // Arrange
        // 10 - 3 confirmados = 7 libres: un pedido pendiente NO ocupa lugar (el
        // organizador los mira a todos y acepta hasta que se llene el cupo), asi
        // que no entra en "pocos cupos". Mismo numero que availableSpots().
        final long eventId = eventWith(10, 3, "Con pendientes");
        for (int i = 0; i < 4; i++) {
            insertRegistration(eventId, "pend" + i + "@mail.com", "PENDING");
        }

        // Act
        final List<Event> result = eventDao.search(fewSpotsCriteria());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSearchWithFewSpotsLeftDoesNotCountAnInvitationTwiceOnceThePersonRegistered() {
        // Arrange
        // Una invitacion que ya se uso (la persona se anoto) no se cuenta dos
        // veces: ahora es una inscripcion, y si esta confirmada ya se cuenta como tal.
        final long eventId = eventWith(10, 8, "Invitacion usada");
        insertRegistration(eventId, "usada@mail.com", "CONFIRMED");
        insertEventInvitation(eventId, "usada@mail.com");

        // Act
        // 10 - 9 confirmados = 1 libre (la invitacion usada no suma otro).
        final List<Long> ids = idsOf(eventDao.search(fewSpotsCriteria()));

        // Assert
        assertTrue(ids.contains(eventId));
    }

    @Test
    public void testCountSearchWithFewSpotsLeftDoesNotCountAnInvitationTwiceOnceThePersonRegistered() {
        // Arrange
        final long eventId = eventWith(10, 8, "Invitacion usada");
        insertRegistration(eventId, "usada@mail.com", "CONFIRMED");
        insertEventInvitation(eventId, "usada@mail.com");

        // Act
        final long count = eventDao.countSearch(fewSpotsCriteria());

        // Assert
        assertEquals(1, count);
    }

    // Los dos tests siguientes usan EXACTAMENTE los mismos datos y el mismo filtro,
    // uno para search() y otro para countSearch(): las dos queries comparten
    // buildWhereClause, y si se desincronizaran, el "N eventos encontrados" no
    // coincidiria con lo que se ve en la pagina (uno de los dos dejaria de dar 2).
    @Test
    public void testCountSearchCountsTheEventsWithFewSpotsLeft() {
        // Arrange
        eventWith(10, 6, "Cuatro libres");
        eventWith(10, 9, "Un libre");
        eventWith(10, 1, "Nueve libres");

        // Act
        final long count = eventDao.countSearch(fewSpotsCriteria());

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testSearchReturnsTheEventsWithFewSpotsLeft() {
        // Arrange
        eventWith(10, 6, "Cuatro libres");
        eventWith(10, 9, "Un libre");
        eventWith(10, 1, "Nueve libres");

        // Act
        final List<Event> result = eventDao.search(fewSpotsCriteria());

        // Assert
        assertEquals(2, result.size());
    }

    // ==================================================================
    // search(): ordenar
    // ==================================================================

    private long eventOn(final long daysFromNow, final BigDecimal price, final String address) {
        final Timestamp date = new Timestamp(System.currentTimeMillis() + daysFromNow * 86_400_000L);
        return insertEvent(cols("address", address, "event_date", date, "price", price));
    }

    @Test
    public void testSearchSortsByDateAscending() {
        // Arrange
        final long enTres = eventOn(3, BigDecimal.ZERO, "En tres dias");
        final long enUno = eventOn(1, BigDecimal.ZERO, "Manana");
        final long enDos = eventOn(2, BigDecimal.ZERO, "En dos dias");

        // Act
        final List<Long> ascending = idsOf(eventDao.search(sortedBy(EventSort.FECHA_ASC)));

        // Assert
        assertEquals(Arrays.asList(enUno, enDos, enTres), ascending);
    }

    @Test
    public void testSearchSortsByDateDescending() {
        // Arrange
        final long enTres = eventOn(3, BigDecimal.ZERO, "En tres dias");
        final long enUno = eventOn(1, BigDecimal.ZERO, "Manana");
        final long enDos = eventOn(2, BigDecimal.ZERO, "En dos dias");

        // Act
        final List<Long> descending = idsOf(eventDao.search(sortedBy(EventSort.FECHA_DESC)));

        // Assert
        assertEquals(Arrays.asList(enTres, enDos, enUno), descending);
    }

    @Test
    public void testSearchSortsByPriceAscending() {
        // Arrange
        final long caro = eventOn(1, new BigDecimal("5000"), "Caro");
        final long gratis = eventOn(2, BigDecimal.ZERO, "Gratis");
        final long medio = eventOn(3, new BigDecimal("1500.50"), "Medio");

        // Act
        final List<Long> ascending = idsOf(eventDao.search(sortedBy(EventSort.PRECIO_ASC)));

        // Assert
        assertEquals(Arrays.asList(gratis, medio, caro), ascending);
    }

    @Test
    public void testSearchSortsByPriceDescending() {
        // Arrange
        final long caro = eventOn(1, new BigDecimal("5000"), "Caro");
        final long gratis = eventOn(2, BigDecimal.ZERO, "Gratis");
        final long medio = eventOn(3, new BigDecimal("1500.50"), "Medio");

        // Act
        final List<Long> descending = idsOf(eventDao.search(sortedBy(EventSort.PRECIO_DESC)));

        // Assert
        assertEquals(Arrays.asList(caro, medio, gratis), descending);
    }

    // Sin limite = lugares "infinitos": ultimo de menos a mas, primero de mas a menos.
    @Test
    public void testSearchSortsBySpotsLeftAscendingWithUnlimitedEventsLast() {
        // Arrange
        final long unLibre = eventWith(10, 9, "Un libre");
        final long sinLimite = eventWith(null, 3, "Sin limite");
        final long ochoLibres = eventWith(10, 2, "Ocho libres");

        // Act
        final List<Long> ascending = idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC)));

        // Assert
        assertEquals(Arrays.asList(unLibre, ochoLibres, sinLimite), ascending);
    }

    @Test
    public void testSearchSortsBySpotsLeftDescendingWithUnlimitedEventsFirst() {
        // Arrange
        final long unLibre = eventWith(10, 9, "Un libre");
        final long sinLimite = eventWith(null, 3, "Sin limite");
        final long ochoLibres = eventWith(10, 2, "Ocho libres");

        // Act
        final List<Long> descending = idsOf(eventDao.search(sortedBy(EventSort.CUPOS_DESC)));

        // Assert
        assertEquals(Arrays.asList(sinLimite, ochoLibres, unLibre), descending);
    }

    @Test
    public void testSearchSortsBySpotsLeftCountingOnlyConfirmedAndUnusedInvitations() {
        // Arrange
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

        // Act
        final List<Long> ids = idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC)));

        // Assert
        assertEquals(Arrays.asList(reservado, cincoLibres), ids);
    }

    @Test
    public void testSortBySpotsCombinesWithFiltersWithoutMixingParameters() {
        // Arrange
        // Filtro (con sus "?") + orden por cupos (con los suyos): si los parametros
        // se mezclaran de orden, la query fallaria o traeria cualquier cosa.
        final long unLibre = eventWith(10, 9, "Un libre");
        final long tresLibres = eventWith(10, 7, "Tres libres");
        eventWith(10, 1, "Nueve libres");
        final EventSearchCriteria criteria = fewSpotsCriteria();
        criteria.setSort(EventSort.CUPOS_DESC);

        // Act
        final List<Long> ids = idsOf(eventDao.search(criteria));

        // Assert
        assertEquals(Arrays.asList(tresLibres, unLibre), ids);
    }

    @Test
    public void testCountSortBySpotsCombinesWithFiltersWithoutMixingParameters() {
        // Arrange
        eventWith(10, 9, "Un libre");
        eventWith(10, 7, "Tres libres");
        eventWith(10, 1, "Nueve libres");
        final EventSearchCriteria criteria = fewSpotsCriteria();
        criteria.setSort(EventSort.CUPOS_DESC);

        // Act
        final long count = eventDao.countSearch(criteria);

        // Assert
        assertEquals(2, count);
    }

    // Los agotados no aparecen en ninguno de los dos sentidos.
    @Test
    public void testSearchSortedBySpotsAscendingLeavesOutSoldOutEvents() {
        // Arrange
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");
        final long sinLimite = eventWith(null, 3, "Sin limite");

        // Act
        final List<Long> ascending = idsOf(eventDao.search(sortedBy(EventSort.CUPOS_ASC)));

        // Assert
        assertEquals(Arrays.asList(unLibre, sinLimite), ascending);
    }

    @Test
    public void testSearchSortedBySpotsDescendingLeavesOutSoldOutEvents() {
        // Arrange
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");
        final long sinLimite = eventWith(null, 3, "Sin limite");

        // Act
        final List<Long> descending = idsOf(eventDao.search(sortedBy(EventSort.CUPOS_DESC)));

        // Assert
        assertEquals(Arrays.asList(sinLimite, unLibre), descending);
    }

    @Test
    public void testCountSearchSortedBySpotsLeavesOutSoldOutEvents() {
        // Arrange
        eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");
        eventWith(null, 3, "Sin limite");

        // Act
        final long count = eventDao.countSearch(sortedBy(EventSort.CUPOS_ASC));

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testSearchSortedByDateStillShowsSoldOutEvents() {
        // Arrange
        // Solo el orden por cupos los saca: ordenando por fecha siguen apareciendo.
        eventWith(10, 9, "Un libre");
        eventWith(10, 10, "Lleno");

        // Act
        final List<Event> result = eventDao.search(sortedBy(EventSort.FECHA_ASC));

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    public void testSearchWithSoldOutKeepsOnlyEventsWithoutSpotsLeft() {
        // Arrange
        final long lleno = eventWith(10, 10, "Lleno");
        final long llenoConReservas = eventWith(10, 8, "Lleno con reservas");
        insertEventInvitation(llenoConReservas, "inv1@mail.com");
        insertEventInvitation(llenoConReservas, "inv2@mail.com");
        eventWith(10, 9, "Un libre");
        eventWith(null, 50, "Sin limite");

        // Act
        final List<Long> ids = idsOf(eventDao.search(soldOutCriteria()));

        // Assert
        assertEquals(2, ids.size());
        assertTrue(ids.contains(lleno));
        assertTrue(ids.contains(llenoConReservas));
    }

    @Test
    public void testCountSearchWithSoldOutKeepsOnlyEventsWithoutSpotsLeft() {
        // Arrange
        eventWith(10, 10, "Lleno");
        final long llenoConReservas = eventWith(10, 8, "Lleno con reservas");
        insertEventInvitation(llenoConReservas, "inv1@mail.com");
        insertEventInvitation(llenoConReservas, "inv2@mail.com");
        eventWith(10, 9, "Un libre");
        eventWith(null, 50, "Sin limite");

        // Act
        final long count = eventDao.countSearch(soldOutCriteria());

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testSearchWithFewSpotsAndSoldOutBringsBoth() {
        // Arrange
        final long lleno = eventWith(10, 10, "Lleno");
        final long unLibre = eventWith(10, 9, "Un libre");
        eventWith(10, 1, "Nueve libres");
        final EventSearchCriteria criteria = soldOutCriteria();
        criteria.setOnlyFewSpotsLeft(true);

        // Act
        final List<Long> ids = idsOf(eventDao.search(criteria));

        // Assert
        assertEquals(2, ids.size());
        assertTrue(ids.contains(lleno));
        assertTrue(ids.contains(unLibre));
    }

    @Test
    public void testSortBySpotsKeepsSoldOutEventsWhenTheSoldOutFilterIsOn() {
        // Arrange
        final long lleno = eventWith(10, 10, "Lleno");
        final EventSearchCriteria criteria = soldOutCriteria();
        criteria.setSort(EventSort.CUPOS_ASC);

        // Act
        // Pidio ver los agotados: el orden por cupos no se los tiene que sacar.
        final List<Long> ids = idsOf(eventDao.search(criteria));

        // Assert
        assertEquals(Collections.singletonList(lleno), ids);
    }

    // ==================================================================
    // search(): filtro por categoria
    // ==================================================================

    @Test
    public void testSearchByCategoryReturnsEventThatHasOneOfSeveralCategories() {
        // Arrange
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final long id = insertEvent(cols("address", "Direccion A"));
        insertEventCategory(id, categoryId, null);
        insertEventCategory(id, otherCategoryId, null);
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(Collections.singletonList(otherCategoryId));
        criteria.setPageSize(10);

        // Act
        // Aparece una sola vez, no una por cada categoria que matchea.
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertEquals(1, result.size());
    }

    @Test
    public void testCountSearchByCategoryReturnsEventThatHasOneOfSeveralCategories() {
        // Arrange
        final long otherCategoryId = insertCategory(OTHER_CATEGORY_NAME);
        final long id = insertEvent(cols("address", "Direccion A"));
        insertEventCategory(id, categoryId, null);
        insertEventCategory(id, otherCategoryId, null);
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(Collections.singletonList(otherCategoryId));
        criteria.setPageSize(10);

        // Act
        final long count = eventDao.countSearch(criteria);

        // Assert
        assertEquals(1, count);
    }

    // ==================================================================
    // search(): filtro por puntaje del organizador
    // ==================================================================

    @Test
    public void testSearchFiltersByMinimumOrganizerRating() {
        // Arrange
        final String highRatedCreator = "alto@mail.com";
        final String lowRatedCreator = "bajo@mail.com";
        final long highRatedCreatorId = insertUser(highRatedCreator);
        final long lowRatedCreatorId = insertUser(lowRatedCreator);
        final long reviewerId = insertUser("reviewer@mail.com");
        final long highRatedEventId = insertEvent(
                cols("address", "Direccion A", "creator_email", highRatedCreator, "creator_id", highRatedCreatorId));
        final long lowRatedEventId = insertEvent(
                cols("address", "Direccion B", "creator_email", lowRatedCreator, "creator_id", lowRatedCreatorId));
        insertReview(highRatedEventId, reviewerId, 5);
        insertReview(lowRatedEventId, reviewerId, 2);
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertEquals(1, result.size());
        assertEquals(highRatedCreator, result.get(0).getCreatorEmail());
    }

    @Test
    public void testCountSearchMatchesSearchWhenFilteringByOrganizerRating() {
        // Arrange
        final String highRatedCreator = "alto@mail.com";
        final long highRatedCreatorId = insertUser(highRatedCreator);
        final long reviewerId = insertUser("reviewer@mail.com");
        final long eventId = insertEvent(
                cols("address", "Direccion A", "creator_email", highRatedCreator, "creator_id", highRatedCreatorId));
        insertReview(eventId, reviewerId, 5);
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        // Act
        final long count = eventDao.countSearch(criteria);

        // Assert
        assertEquals(1, count);
    }

    @Test
    public void testSearchWithMinimumOrganizerRatingExcludesCreatorsBelowIt() {
        // Arrange
        final long reviewerId = insertUser("reviewer@mail.com");
        final long eventId = insertEvent(cols("creator_id", creatorId));
        insertReview(eventId, reviewerId, 3);
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setMinOrganizerRating(4);
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSearchWithoutMinimumOrganizerRatingIgnoresTheFilter() {
        // Arrange
        insertEvent();
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertEquals(1, result.size());
    }

    // ==================================================================
    // search(): eventos privados
    // ==================================================================

    /**
     * EL TEST QUE IMPIDE QUE UN EVENTO PRIVADO SE FILTRE POR EL LISTADO. Un
     * evento privado no tiene que aparecer aca pase lo que pase en el resto
     * de los filtros: si esto devolviera el evento, cualquiera lo encontraria
     * buscando, sin necesitar el link directo ni estar invitado.
     */
    @Test
    public void testSearchExcludesPrivateEvents() {
        // Arrange
        insertEvent(cols("visibility", EventVisibility.PRIVATE.name()));
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCountSearchExcludesPrivateEvents() {
        // Arrange
        insertEvent(cols("visibility", EventVisibility.PRIVATE.name()));
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final long count = eventDao.countSearch(criteria);

        // Assert
        assertEquals(0, count);
    }

    @Test
    public void testSearchIncludesPublicEventsAlongsidePrivateOnes() {
        // Arrange
        insertEvent(cols("address", "Privado", "visibility", EventVisibility.PRIVATE.name()));
        insertEvent(cols("address", "Publico"));
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertEquals(1, result.size());
    }

    // ==================================================================
    // search(): eventos cancelados
    // ==================================================================

    /**
     * EL TEST QUE IMPIDE QUE UN EVENTO CANCELADO SE FILTRE POR EL LISTADO.
     * Igual que con los privados: si esto lo devolviera, alguien podria
     * anotarse a un evento que su organizador ya dio de baja.
     */
    @Test
    public void testSearchExcludesCancelledEvents() {
        // Arrange
        insertEvent(cols("cancelled_at", new Timestamp(System.currentTimeMillis())));
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final List<Event> result = eventDao.search(criteria);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testCountSearchExcludesCancelledEvents() {
        // Arrange
        insertEvent(cols("cancelled_at", new Timestamp(System.currentTimeMillis())));
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setPageSize(10);

        // Act
        final long count = eventDao.countSearch(criteria);

        // Assert
        assertEquals(0, count);
    }

    // ==================================================================
    // findFeatured() / setFeatured()
    // ==================================================================

    @Test
    public void testFindFeaturedReturnsOnlyEventsMarkedAsFeatured() {
        // Arrange
        insertEvent(cols("address", "No destacado"));
        final long featuredId = insertEvent(cols("address", "Destacado", "featured", true,
                "featured_at", new Timestamp(System.currentTimeMillis())));

        // Act
        final List<Event> featured = eventDao.findFeatured(10);

        // Assert
        assertEquals(1, featured.size());
        assertEquals(featuredId, featured.get(0).getId());
        assertTrue(featured.get(0).isFeatured());
    }

    @Test
    public void testFindFeaturedOrdersByMostRecentlyFeaturedFirst() {
        // Arrange
        final Timestamp antes = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp despues = new Timestamp(System.currentTimeMillis() - 1000);
        final long firstFeaturedId = insertEvent(cols("address", "Primero", "featured", true, "featured_at", antes));
        final long secondFeaturedId = insertEvent(cols("address", "Segundo", "featured", true, "featured_at", despues));

        // Act
        final List<Event> featured = eventDao.findFeatured(10);

        // Assert
        assertEquals(2, featured.size());
        assertEquals(secondFeaturedId, featured.get(0).getId());
        assertEquals(firstFeaturedId, featured.get(1).getId());
    }

    @Test
    public void testFindFeaturedRespectsTheLimit() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            insertEvent(cols("address", "Destacado " + i, "featured", true,
                    "featured_at", new Timestamp(System.currentTimeMillis())));
        }

        // Act
        final List<Event> featured = eventDao.findFeatured(2);

        // Assert
        assertEquals(2, featured.size());
    }

    @Test
    public void testFindFeaturedExcludesPrivateCancelledAndPastEvents() {
        // Arrange
        insertEvent(cols("address", "Privado", "featured", true,
                "featured_at", new Timestamp(System.currentTimeMillis()), "visibility", EventVisibility.PRIVATE.name()));
        insertEvent(cols("address", "Cancelado", "featured", true,
                "featured_at", new Timestamp(System.currentTimeMillis()), "cancelled_at", new Timestamp(System.currentTimeMillis())));
        insertEvent(cols("address", "Pasado", "featured", true,
                "featured_at", new Timestamp(System.currentTimeMillis()),
                "event_date", new Timestamp(System.currentTimeMillis() - 86_400_000L)));

        // Act
        final List<Event> featured = eventDao.findFeatured(10);

        // Assert
        assertTrue(featured.isEmpty());
    }

    @Test
    public void testSetFeaturedTrueMarksTheEventFeaturedAndSetsFeaturedAt() {
        // Arrange
        final long id = insertEvent();

        // Act
        eventDao.setFeatured(id, true);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT featured, featured_at FROM event WHERE id = ?", id);
        assertTrue((Boolean) row.get("featured"));
        assertNotNull(row.get("featured_at"));
    }

    @Test
    public void testSetFeaturedFalseClearsFeaturedAt() {
        // Arrange
        final long id = insertEvent(cols("featured", true, "featured_at", new Timestamp(System.currentTimeMillis())));

        // Act
        eventDao.setFeatured(id, false);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT featured, featured_at FROM event WHERE id = ?", id);
        assertFalse((Boolean) row.get("featured"));
        assertNull(row.get("featured_at"));
    }

    // ==================================================================
    // recordatorio de 24 hs
    // ==================================================================

    private long eventInHours(final long hours, final String address) {
        return insertEvent(cols("address", address,
                "event_date", new Timestamp(System.currentTimeMillis() + hours * HORA)));
    }

    @Test
    public void testFindIdsNeedingReminderOnlyReturnsEventsInTheWindowNotCancelledNorReminded() {
        // Arrange
        final long enDiezHoras = eventInHours(10, "En diez horas");
        eventInHours(30, "En treinta horas");
        eventInHours(-2, "Ya paso");
        insertEvent(cols("address", "Cancelado",
                "event_date", new Timestamp(System.currentTimeMillis() + 5 * HORA),
                "cancelled_at", new Timestamp(System.currentTimeMillis())));
        insertEvent(cols("address", "Ya avisado",
                "event_date", new Timestamp(System.currentTimeMillis() + 8 * HORA),
                "reminder_sent_at", new Timestamp(System.currentTimeMillis())));
        final Date now = new Date();

        // Act
        final List<Long> ids = eventDao.findIdsNeedingReminder(now, new Date(now.getTime() + 24 * HORA));

        // Assert
        assertEquals(Collections.singletonList(enDiezHoras), ids);
    }

    @Test
    public void testMarkReminderSentReturnsTrueTheFirstTime() {
        // Arrange
        final long id = eventInHours(10, "Una vez");

        // Act
        final boolean result = eventDao.markReminderSent(id, new Date());

        // Assert
        assertTrue(result);
    }

    @Test
    public void testMarkReminderSentStoresTheMomentOfTheReminder() {
        // Arrange
        final long id = eventInHours(10, "Una vez");

        // Act
        eventDao.markReminderSent(id, new Date());

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event", "id = " + id + " AND reminder_sent_at IS NOT NULL"));
    }

    // Si ya estaba reclamado (el UPDATE tiene "reminder_sent_at IS NULL"), no lo
    // reclama de nuevo: es lo que garantiza que el recordatorio salga una sola vez.
    @Test
    public void testMarkReminderSentReturnsFalseWhenTheReminderWasAlreadySent() {
        // Arrange
        final long id = insertEvent(cols("address", "Ya avisado",
                "event_date", new Timestamp(System.currentTimeMillis() + 10 * HORA),
                "reminder_sent_at", new Timestamp(System.currentTimeMillis())));

        // Act
        final boolean result = eventDao.markReminderSent(id, new Date());

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUpdateKeepsTheReminderIfTheDateDidNotChange() {
        // Arrange
        final Timestamp eventDate = new Timestamp(System.currentTimeMillis() + 86_400_000L);
        final long id = insertEvent(cols("event_date", eventDate,
                "reminder_sent_at", new Timestamp(System.currentTimeMillis())));
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setEventDate(eventDate);
        changes.setTitle("Solo cambia el titulo");

        // Act
        eventDao.update(changes);

        // Assert
        final Timestamp reminderSentAt = jdbcTemplate.queryForObject(
                "SELECT reminder_sent_at FROM event WHERE id = ?", Timestamp.class, id);
        assertNotNull(reminderSentAt);
    }

    @Test
    public void testUpdateResetsTheReminderIfTheDateChanged() {
        // Arrange
        final Timestamp originalDate = new Timestamp(System.currentTimeMillis() + 86_400_000L);
        final long id = insertEvent(cols("event_date", originalDate,
                "reminder_sent_at", new Timestamp(System.currentTimeMillis())));
        final Event changes = someEvent(categoryId);
        changes.setId(id);
        changes.setEventDate(new Date(originalDate.getTime() + 7 * 24 * HORA));

        // Act
        eventDao.update(changes);

        // Assert
        final Timestamp reminderSentAt = jdbcTemplate.queryForObject(
                "SELECT reminder_sent_at FROM event WHERE id = ?", Timestamp.class, id);
        assertNull(reminderSentAt);
    }

    // ==================================================================
    // findByIds()
    // ==================================================================

    @Test
    public void testFindByIdsReturnsOnlyTheRequestedEvents() {
        // Arrange
        final long first = insertEvent();
        insertEvent();
        final long third = insertEvent();

        // Act
        final List<Event> result = eventDao.findByIds(Arrays.asList(first, third));

        // Assert
        assertEquals(Arrays.asList(first, third), idsOf(result).stream().sorted().collect(Collectors.toList()));
    }

    @Test
    public void testFindByIdsIgnoresIdsThatDoNotExist() {
        // Arrange
        final long id = insertEvent();

        // Act
        final List<Event> result = eventDao.findByIds(Arrays.asList(id, id + 999));

        // Assert
        assertEquals(Collections.singletonList(id), idsOf(result));
    }

    @Test
    public void testFindByIdsWithAnEmptyListReturnsAnEmptyList() {
        // Arrange
        insertEvent();

        // Act
        final List<Event> result = eventDao.findByIds(Collections.emptyList());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateReturnsTrueWhenTheEventExisted() {
        // Arrange
        final long id = insertEvent();
        insertEventCategory(id, categoryId, null);
        final Event changes = someEvent(categoryId);
        changes.setId(id);

        // Act
        final boolean result = eventDao.update(changes);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUpdateReturnsFalseWhenThereIsNoSuchEvent() {
        // Arrange
        // Sin categorias ni imagenes: lo unico que se intenta tocar es la fila del evento, que no existe.
        final Event changes = someEvent(categoryId);
        changes.setCategories(java.util.Collections.emptyList());
        changes.setId(999_999L);

        // Act
        final boolean result = eventDao.update(changes);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCancelReturnsTrueWhenTheEventExisted() {
        // Arrange
        final long id = insertEvent();

        // Act
        final boolean result = eventDao.cancel(id);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCancelReturnsFalseWhenThereIsNoSuchEvent() {
        // Arrange
        // (no hay ningun evento guardado)

        // Act
        final boolean result = eventDao.cancel(999_999L);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRemoveByAdminReturnsTrueWhenTheEventExisted() {
        // Arrange
        final long id = insertEvent();

        // Act
        final boolean result = eventDao.removeByAdmin(id, "Contenido inapropiado");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRemoveByAdminReturnsFalseWhenThereIsNoSuchEvent() {
        // Arrange
        // (no hay ningun evento guardado)

        // Act
        final boolean result = eventDao.removeByAdmin(999_999L, "Contenido inapropiado");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSetFeaturedReturnsTrueWhenTheEventExisted() {
        // Arrange
        final long id = insertEvent();

        // Act
        final boolean result = eventDao.setFeatured(id, true);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetFeaturedReturnsFalseWhenThereIsNoSuchEvent() {
        // Arrange
        // (no hay ningun evento guardado)

        // Act
        final boolean result = eventDao.setFeatured(999_999L, true);

        // Assert
        assertFalse(result);
    }
}
