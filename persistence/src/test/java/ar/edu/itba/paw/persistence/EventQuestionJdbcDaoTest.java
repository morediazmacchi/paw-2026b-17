package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.User;
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
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class EventQuestionJdbcDaoTest {

    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Autowired
    private DataSource ds;

    @Autowired
    private EventQuestionDao questionDao;

    @Autowired
    private UserDao userDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;
    private long eventId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = userDao.create(new User("pregunta@mail.com", "Ana")).getId();
        eventId = insertEvent("Recital");
    }

    private long insertEvent(final String title) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email) VALUES (?, ?, ?, ?)",
                title, new Timestamp(System.currentTimeMillis()), "Corrientes 1234", CREATOR_EMAIL);
        return jdbcTemplate.queryForObject("SELECT id FROM event WHERE title = ?", Long.class, title);
    }

    @Test
    public void testCreateAndFindByIdBringTheAskerFromUsers() {
        final EventQuestion created = questionDao.create(new EventQuestion(eventId, userId, "Hay estacionamiento?"));

        final Optional<EventQuestion> found = questionDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals("Hay estacionamiento?", found.get().getText());
        assertEquals("Ana", found.get().getAskerName());
        assertEquals("pregunta@mail.com", found.get().getAskerEmail());
        assertFalse(found.get().isAnswered());
        assertNull(found.get().getAnswer());
    }

    @Test
    public void testFindByEventPaginatesNewestFirstAndCountsOnlyThatEvent() {
        final long otherEventId = insertEvent("Otro evento");
        final long first = questionDao.create(new EventQuestion(eventId, userId, "Primera")).getId();
        final long second = questionDao.create(new EventQuestion(eventId, userId, "Segunda")).getId();
        final long third = questionDao.create(new EventQuestion(eventId, userId, "Tercera")).getId();
        questionDao.create(new EventQuestion(otherEventId, userId, "De otro evento"));

        final List<EventQuestion> page1 = questionDao.findByEvent(eventId, 1, 2);
        final List<EventQuestion> page2 = questionDao.findByEvent(eventId, 2, 2);

        assertEquals(2, page1.size());
        assertEquals(third, page1.get(0).getId());
        assertEquals(second, page1.get(1).getId());
        assertEquals(1, page2.size());
        assertEquals(first, page2.get(0).getId());
        assertEquals(3, questionDao.countByEvent(eventId));
    }

    @Test
    public void testAnswerOnlyWorksTheFirstTime() {
        final long id = questionDao.create(new EventQuestion(eventId, userId, "Se puede ir con perros?")).getId();

        assertTrue(questionDao.answer(id, "Si", new Date()));
        assertFalse(questionDao.answer(id, "No", new Date()));

        final EventQuestion found = questionDao.findById(id).orElseThrow(AssertionError::new);
        assertTrue(found.isAnswered());
        assertEquals("Si", found.getAnswer());
    }
}
