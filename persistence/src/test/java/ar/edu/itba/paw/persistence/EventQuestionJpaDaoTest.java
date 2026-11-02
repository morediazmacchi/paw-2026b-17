package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventQuestionJpaDaoTest {

    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private EventQuestionDao questionDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = insertUser("pregunta@mail.com", "Ana");
        eventId = insertEvent("Recital");
    }

    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertEvent(final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", CREATOR_EMAIL);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    private long insertQuestion(final long eventId, final long userId, final String text) {
        jdbcTemplate.update(
                "INSERT INTO event_question (event_id, user_id, question_text) VALUES (?, ?, ?)",
                eventId, userId, text);
        return jdbcTemplate.queryForObject("SELECT id FROM event_question ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertQuestionAt(final long eventId, final long userId, final String text, final Timestamp createdAt) {
        jdbcTemplate.update(
                "INSERT INTO event_question (event_id, user_id, question_text, created_at) VALUES (?, ?, ?, ?)",
                eventId, userId, text, createdAt);
        return jdbcTemplate.queryForObject("SELECT id FROM event_question ORDER BY id DESC LIMIT 1", Long.class);
    }

    private long insertAnsweredQuestion(final long eventId, final long userId, final String text, final String answer) {
        jdbcTemplate.update(
                "INSERT INTO event_question (event_id, user_id, question_text, answer_text, answered_at) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventId, userId, text, answer, new Timestamp(System.currentTimeMillis()));
        return jdbcTemplate.queryForObject("SELECT id FROM event_question ORDER BY id DESC LIMIT 1", Long.class);
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheQuestionRow() {
        // Arrange
        final EventQuestion toCreate = new EventQuestion(em.getReference(Event.class, eventId), em.getReference(User.class, userId), "Hay estacionamiento?");

        // Act
        final EventQuestion created = questionDao.create(toCreate);

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, user_id, question_text FROM event_question WHERE id = ?", created.getId());
        assertEquals(eventId, ((Number) row.get("event_id")).longValue());
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals("Hay estacionamiento?", row.get("question_text"));
    }

    @Test
    public void testCreateLeavesTheQuestionUnanswered() {
        // Arrange
        final EventQuestion toCreate = new EventQuestion(em.getReference(Event.class, eventId), em.getReference(User.class, userId), "Hay estacionamiento?");

        // Act
        final EventQuestion created = questionDao.create(toCreate);

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "event_question", "id = " + created.getId() + " AND answer_text IS NULL AND answered_at IS NULL"));
    }

    @Test
    public void testCreateSetsCreatedAtAutomatically() {
        // Arrange
        final EventQuestion toCreate = new EventQuestion(em.getReference(Event.class, eventId), em.getReference(User.class, userId), "Hay estacionamiento?");

        // Act
        final EventQuestion created = questionDao.create(toCreate);

        // Assert
        final Timestamp createdAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM event_question WHERE id = ?", Timestamp.class, created.getId());
        assertTrue(createdAt != null);
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsTheQuestionWithTheAskerJoinedFromUsers() {
        // Arrange
        final long id = insertQuestion(eventId, userId, "Hay estacionamiento?");

        // Act
        final Optional<EventQuestion> found = questionDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals("Hay estacionamiento?", found.get().getText());
        assertEquals("Ana", found.get().getAskerName());
        assertEquals("pregunta@mail.com", found.get().getAskerEmail());
        assertFalse(found.get().isAnswered());
        assertNull(found.get().getAnswer());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheQuestionDoesNotExist() {
        // Arrange: no se inserta ninguna pregunta.

        // Act
        final Optional<EventQuestion> found = questionDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventOrdersNewestFirst() {
        // Arrange
        final Timestamp t1 = new Timestamp(System.currentTimeMillis() - 2000);
        final Timestamp t2 = new Timestamp(System.currentTimeMillis() - 1000);
        final Timestamp t3 = new Timestamp(System.currentTimeMillis());
        final long first = insertQuestionAt(eventId, userId, "Primera", t1);
        final long second = insertQuestionAt(eventId, userId, "Segunda", t2);
        final long third = insertQuestionAt(eventId, userId, "Tercera", t3);

        // Act
        final List<EventQuestion> found = questionDao.findByEvent(eventId, 1, 20);

        // Assert
        assertEquals(Arrays.asList(third, second, first),
                Arrays.asList(found.get(0).getId(), found.get(1).getId(), found.get(2).getId()));
    }

    @Test
    public void testFindByEventFirstPageHasAsManyQuestionsAsThePageSize() {
        // Arrange
        insertQuestionAt(eventId, userId, "Primera", new Timestamp(System.currentTimeMillis() - 2000));
        insertQuestionAt(eventId, userId, "Segunda", new Timestamp(System.currentTimeMillis() - 1000));
        insertQuestionAt(eventId, userId, "Tercera", new Timestamp(System.currentTimeMillis()));

        // Act
        final List<EventQuestion> page1 = questionDao.findByEvent(eventId, 1, 2);

        // Assert
        assertEquals(2, page1.size());
    }

    @Test
    public void testFindByEventSecondPageHasTheRemainingOldestQuestion() {
        // Arrange
        final long first = insertQuestionAt(eventId, userId, "Primera", new Timestamp(System.currentTimeMillis() - 2000));
        insertQuestionAt(eventId, userId, "Segunda", new Timestamp(System.currentTimeMillis() - 1000));
        insertQuestionAt(eventId, userId, "Tercera", new Timestamp(System.currentTimeMillis()));

        // Act
        final List<EventQuestion> page2 = questionDao.findByEvent(eventId, 2, 2);

        // Assert
        assertEquals(Collections.singletonList(first), Collections.singletonList(page2.get(0).getId()));
        assertEquals(1, page2.size());
    }

    @Test
    public void testFindByEventOnlyReturnsThatEventsQuestions() {
        // Arrange
        insertQuestion(eventId, userId, "De este evento");
        final long otherEventId = insertEvent("Otro evento");
        insertQuestion(otherEventId, userId, "De otro evento");

        // Act
        final List<EventQuestion> found = questionDao.findByEvent(eventId, 1, 20);

        // Assert
        assertEquals(1, found.size());
        assertEquals("De este evento", found.get(0).getText());
    }

    // ---- countByEvent() ----

    @Test
    public void testCreateWithAnAskerBuiltFromTheSessionStoresItsId() {
        // Arrange
        final User fromSession = new User("pregunta@mail.com", "Ana");
        fromSession.setId(userId);
        final EventQuestion toCreate = new EventQuestion(em.getReference(Event.class, eventId), fromSession, "Hay lugar?");

        // Act
        final EventQuestion created = questionDao.create(toCreate);

        // Assert
        final Long storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM event_question WHERE id = ?", Long.class, created.getId());
        assertEquals(userId, storedUserId.longValue());
    }

    @Test
    public void testCountByEventCountsOnlyThatEventsQuestions() {
        // Arrange
        insertQuestion(eventId, userId, "Primera");
        insertQuestion(eventId, userId, "Segunda");
        insertQuestion(eventId, userId, "Tercera");
        final long otherEventId = insertEvent("Otro evento");
        insertQuestion(otherEventId, userId, "De otro evento");

        // Act
        final long count = questionDao.countByEvent(eventId);

        // Assert
        assertEquals(3, count);
    }

    // ---- findEventCreatorId() ----

    private long insertEventCreatedBy(final String title, final long creatorId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, creator_id) VALUES (?, ?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", CREATOR_EMAIL, creatorId);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    @Test
    public void testFindEventCreatorIdReturnsTheCreatorOfTheQuestionsEvent() {
        // Arrange
        final long creatorId = insertUser("organizador@mail.com", "Ola");
        final long otherCreatorId = insertUser("otro@mail.com", "Beto");
        final long questionId = insertQuestion(insertEventCreatedBy("Del organizador", creatorId), userId, "Hay lugar?");
        insertQuestion(insertEventCreatedBy("De otro", otherCreatorId), userId, "Señuelo");

        // Act
        final Optional<Long> result = questionDao.findEventCreatorId(questionId);

        // Assert
        assertEquals(Optional.of(creatorId), result);
    }

    // creator_id puede ser NULL (evento legacy sin organizador): sin el filtro de nulos, findFirst() tiraba
    // NullPointerException con ese elemento nulo y la regla de acceso terminaba en un 500.
    @Test
    public void testFindEventCreatorIdIsEmptyForAnEventWithoutCreator() {
        // Arrange: insertEvent() deja creator_id en NULL; se pregunta sobre ese evento
        final long questionId = insertQuestion(insertEvent("Evento sin organizador"), userId, "Hay lugar?");

        // Act
        final Optional<Long> result = questionDao.findEventCreatorId(questionId);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindEventCreatorIdIsEmptyWhenTheQuestionDoesNotExist() {
        // Act
        final Optional<Long> result = questionDao.findEventCreatorId(987654L);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- answer() ----

    @Test
    public void testAnswerDoesNotLoseAnsweredAtWhenTheAlreadyLoadedQuestionIsModifiedAfterwards() {
        // Arrange: la pregunta ya cargada en la sesion de Hibernate, como la tiene el service.
        // Se carga con em.find (JPA directo), no con otro metodo del DAO bajo prueba.
        final long id = insertQuestion(eventId, userId, "Se puede ir con perros?");
        final EventQuestion loaded = em.find(EventQuestion.class, id);

        // Act
        final boolean result = questionDao.answer(id, "Si", new Date());
        // Lo que hace el service despues: actualiza la entidad en memoria para armar el mail.
        loaded.setAnswer("Si");
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question",
                "id = " + id + " AND answer_text = 'Si' AND answered_at IS NOT NULL"));
    }

    @Test
    public void testAnswerSetsTheAnswerAndReturnsTrueTheFirstTime() {
        // Arrange
        final long id = insertQuestion(eventId, userId, "Se puede ir con perros?");

        // Act
        final boolean result = questionDao.answer(id, "Si", new Date());

        // Assert
        assertTrue(result);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT answer_text, answered_at FROM event_question WHERE id = ?", id);
        assertEquals("Si", row.get("answer_text"));
        assertTrue(row.get("answered_at") != null);
    }

    @Test
    public void testAnswerReturnsFalseAndLeavesItUnchangedWhenAlreadyAnswered() {
        // Arrange
        final long id = insertAnsweredQuestion(eventId, userId, "Se puede ir con perros?", "Si");

        // Act
        final boolean result = questionDao.answer(id, "No", new Date());

        // Assert
        assertFalse(result);
        final String answer = jdbcTemplate.queryForObject(
                "SELECT answer_text FROM event_question WHERE id = ?", String.class, id);
        assertEquals("Si", answer);
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheQuestionAndReturnsTrue() {
        // Arrange
        final long id = insertQuestion(eventId, userId, "Se puede ir con perros?");

        // Act
        final boolean result = questionDao.delete(id);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question", "id = " + id));
    }

    @Test
    public void testDeleteAlsoRemovesAnAnsweredQuestion() {
        // Arrange
        final long id = insertAnsweredQuestion(eventId, userId, "Se puede ir con perros?", "Si");

        // Act
        questionDao.delete(id);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question", "id = " + id));
    }

    @Test
    public void testDeleteOnlyAffectsThatQuestion() {
        // Arrange
        final long toDelete = insertQuestion(eventId, userId, "Primera");
        final long toKeep = insertQuestion(eventId, userId, "Segunda");

        // Act
        questionDao.delete(toDelete);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question", "id = " + toDelete));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question", "id = " + toKeep));
    }

    @Test
    public void testDeleteOfAQuestionThatDoesNotExistDeletesNothingAndReturnsFalse() {
        // Arrange: otra pregunta, que no es la del Act. Tiene que quedar.
        final long other = insertQuestion(eventId, userId, "Se puede ir con perros?");

        // Act
        final boolean result = questionDao.delete(999_999L);
        // El DAO escribe via JPA (dirty checking/remove): se manda a la base antes de leer por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "event_question", "id = " + other));
    }
}
