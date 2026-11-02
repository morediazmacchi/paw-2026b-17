package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.EventQuestionDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: todas las dependencias son @Mock y cada test
 * asierta sobre lo que devuelve el service (sin verify). Los avisos (inbox y
 * mail) son "disparar y olvidar" (void) y no se testean aca: verificar que se
 * llamaron seria testear la implementacion de EventQuestionServiceImpl, no el
 * contrato de EventQuestionService.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventQuestionServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long QUESTION_ID = 7;
    private static final String CREATOR_EMAIL = "org@mail.com";
    private static final long CREATOR_ID = 1;
    private static final long OTHER_ID = 999;
    private static final String ASKER_EMAIL = "ana@mail.com";

    @Mock
    private EventQuestionDao questionDao;
    @Mock
    private EventService eventService;
    @Mock
    private InboxService inboxService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private EventAccessService accessChecker;

    private EventQuestionServiceImpl questionService;

    @Before
    public void setUp() {
        questionService = new EventQuestionServiceImpl(
                questionDao, eventService, inboxService, notificationService);
        // Default para los tests que no son sobre el chequeo de acceso en si:
        // "puede verlo", para que la unica rama en juego sea la que cada uno testea.
    }

    private static Event eventInHours(final long hours) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        return event;
    }

    private static User asker() {
        final User user = new User(ASKER_EMAIL, "Ana");
        user.setId(2L);
        return user;
    }

    private static EventQuestion questionOf(final long eventId) {
        return questionWithId(QUESTION_ID, eventId);
    }

    private static EventQuestion questionWithId(final long id, final long eventId) {
        final EventQuestion question = new EventQuestion(ModelFixtures.event(eventId), ModelFixtures.user(2), "Hay estacionamiento?");
        question.setId(id);
        return question;
    }

    // La pregunta tal como la devuelve el dao al crearla (con id).
    private static EventQuestion created() {
        return questionWithId(1, EVENT_ID);
    }

    // ---- ask() ----

    @Test
    public void testAskReturnsCreated() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(any(EventQuestion.class))).thenReturn(created());

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hay estacionamiento?");

        // Assert
        assertEquals(QuestionResult.CREATED, result);
    }

    @Test
    public void testAskWithABlankTextReturnsInvalid() {
        // Arrange: el dao tiene un dato senuelo; si el service guardara la pregunta igual, el resultado seria CREATED
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        lenient().when(questionDao.create(any(EventQuestion.class))).thenReturn(created());

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "   ");

        // Assert
        assertEquals(QuestionResult.INVALID, result);
    }

    @Test
    public void testAskWithATextLongerThanTheMaximumReturnsInvalid() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        lenient().when(questionDao.create(any(EventQuestion.class))).thenReturn(created());
        final String tooLong = new String(new char[EventQuestionService.MAX_QUESTION_LENGTH + 1]).replace('\0', 'a');

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), tooLong);

        // Assert
        assertEquals(QuestionResult.INVALID, result);
    }

    @Test
    public void testAskWithATextOfExactlyTheMaximumLengthIsCreated() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(any(EventQuestion.class))).thenReturn(created());
        final String maximum = new String(new char[EventQuestionService.MAX_QUESTION_LENGTH]).replace('\0', 'a');

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), maximum);

        // Assert
        assertEquals(QuestionResult.CREATED, result);
    }

    @Test
    public void testTheOrganizerCannotAskOnTheirOwnEvent() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        final User organizer = new User(CREATOR_EMAIL, "Org");
        organizer.setId(CREATOR_ID);

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, organizer, "Hola?");

        // Assert
        assertEquals(QuestionResult.OWN_EVENT, result);
    }

    @Test
    public void testCannotAskOnAPastEvent() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert
        assertEquals(QuestionResult.CLOSED, result);
    }

    @Test
    public void testCannotAskOnACancelledEvent() {
        // Arrange
        final Event event = eventInHours(48);
        event.setCancelledAt(new Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert
        assertEquals(QuestionResult.CLOSED, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testAskOnAnEventThatDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- answer() ----

    // Pregunta del evento sin responder y evento del organizador: el caso feliz.
    private void answerableQuestion(final String expectedText, final boolean saved) {
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.answer(eq(QUESTION_ID), eq(expectedText), any(Date.class))).thenReturn(saved);
    }

    @Test
    public void testTheOrganizerAnswersAndGetsAnswered() {
        // Arrange
        answerableQuestion("Si, en la esquina", true);

        // Act
        final AnswerResult result = questionService.answer(QUESTION_ID, " Si, en la esquina ");

        // Assert
        assertEquals(AnswerResult.ANSWERED, result);
    }

    @Test
    public void testAnAnsweredQuestionIsNotAnsweredAgain() {
        // Arrange
        answerableQuestion("Si", false);

        // Act
        final AnswerResult result = questionService.answer(QUESTION_ID, "Si");

        // Assert
        assertEquals(AnswerResult.ALREADY_ANSWERED, result);
    }

    @Test
    public void testAQuestionThatDoesNotExistIsNotFound() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.empty());

        // Act
        final AnswerResult result = questionService.answer(QUESTION_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_FOUND, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testAQuestionOfAnEventThatDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        questionService.answer(QUESTION_ID, "Si");
    }

    // ---- findEventCreatorId() ----

    @Test
    public void testFindEventCreatorIdReturnsTheCreatorTheDaoFound() {
        // Arrange
        when(questionDao.findEventCreatorId(QUESTION_ID)).thenReturn(Optional.of(CREATOR_ID));

        // Act
        final Optional<Long> result = questionService.findEventCreatorId(QUESTION_ID);

        // Assert
        assertEquals(Optional.of(CREATOR_ID), result);
    }

    @Test
    public void testFindEventCreatorIdIsEmptyWhenTheQuestionDoesNotExist() {
        // Arrange
        when(questionDao.findEventCreatorId(QUESTION_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Long> result = questionService.findEventCreatorId(QUESTION_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findPage() ----

    @Test
    public void testFindPageReturnsTheRequestedPage() {
        // Arrange
        final List<EventQuestion> page = Arrays.asList(questionWithId(2, EVENT_ID), questionWithId(1, EVENT_ID));
        when(questionDao.findByEvent(EVENT_ID, 2, EventQuestionServiceImpl.PAGE_SIZE)).thenReturn(page);
        when(questionDao.countByEvent(EVENT_ID)).thenReturn(12L);

        // Act
        final PageResult<EventQuestion> result = questionService.findPage(EVENT_ID, 2);

        // Assert
        assertEquals(page, result.getContent());
        assertEquals(2, result.getPage());
    }

    @Test
    public void testFindPageReturnsTheTotalOfQuestionsOfThatEvent() {
        // Arrange
        when(questionDao.findByEvent(EVENT_ID, 1, EventQuestionServiceImpl.PAGE_SIZE))
                .thenReturn(Arrays.asList(questionWithId(1, EVENT_ID)));
        when(questionDao.countByEvent(EVENT_ID)).thenReturn(12L);

        // Act
        final PageResult<EventQuestion> result = questionService.findPage(EVENT_ID, 1);

        // Assert
        assertEquals(12L, result.getTotalElements());
        assertEquals(2, result.getTotalPages());
    }

    @Test
    public void testFindPageBeyondTheLastFallsBackToTheLastPage() {
        // Arrange: 12 preguntas, de a 10 por pagina -> la ultima pagina valida es la 2.
        final List<EventQuestion> lastPage = Arrays.asList(questionWithId(1, EVENT_ID), questionWithId(2, EVENT_ID));
        when(questionDao.findByEvent(EVENT_ID, 2, EventQuestionServiceImpl.PAGE_SIZE)).thenReturn(lastPage);
        when(questionDao.countByEvent(EVENT_ID)).thenReturn(12L);

        // Act
        final PageResult<EventQuestion> result = questionService.findPage(EVENT_ID, 99);

        // Assert
        assertEquals(2, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindPageWithAPageBelowOneReturnsTheFirstPage() {
        // Arrange
        final List<EventQuestion> firstPage = Arrays.asList(questionWithId(1, EVENT_ID));
        when(questionDao.findByEvent(EVENT_ID, 1, EventQuestionServiceImpl.PAGE_SIZE)).thenReturn(firstPage);
        when(questionDao.countByEvent(EVENT_ID)).thenReturn(1L);

        // Act
        final PageResult<EventQuestion> result = questionService.findPage(EVENT_ID, 0);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    // ---- deleteByAdmin() (moderacion) ----

    @Test
    public void testDeleteByAdminReturnsTheEventIdOfTheRemovedQuestion() {
        // Arrange
        final EventQuestion question = new EventQuestion(ModelFixtures.event(EVENT_ID), ModelFixtures.user(OTHER_ID),
                "Hay lugar?");
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(questionDao.delete(QUESTION_ID)).thenReturn(true);

        // Act
        final Optional<Long> result = questionService.deleteByAdmin(QUESTION_ID);

        // Assert
        assertEquals(Optional.of(EVENT_ID), result);
    }

    @Test
    public void testDeleteByAdminReturnsEmptyWhenThereWasNoSuchQuestion() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Long> result = questionService.deleteByAdmin(QUESTION_ID);

        // Assert
        assertFalse(result.isPresent());
    }
}
