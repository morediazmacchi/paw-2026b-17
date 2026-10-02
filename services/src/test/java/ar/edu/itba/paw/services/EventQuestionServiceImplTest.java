package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: EventQuestionDao y EventService son @Mock y cada test
 * asierta sobre lo que devuelve el service (sin verify). Los avisos (inbox y mail) son
 * "disparar y olvidar" (void), asi que se siguen anotando con los fakes a mano
 * RecordingInboxService / RecordingNotificationService.
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

    private RecordingInboxService inbox;
    private RecordingNotificationService notifications;

    private EventQuestionServiceImpl questionService;

    @Before
    public void setUp() {
        inbox = new RecordingInboxService();
        notifications = new RecordingNotificationService();
        questionService = new EventQuestionServiceImpl(questionDao, eventService, inbox, notifications);
    }

    private static Event eventInHours(final long hours) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        return event;
    }

    private static User asker() {
        final User user = new User(ASKER_EMAIL, "Ana");
        user.setId(2);
        return user;
    }

    private static EventQuestion questionOf(final long eventId) {
        return questionWithId(QUESTION_ID, eventId);
    }

    private static EventQuestion questionWithId(final long id, final long eventId) {
        final EventQuestion question = new EventQuestion(eventId, 2, "Hay estacionamiento?");
        question.setId(id);
        question.setAskerEmail(ASKER_EMAIL);
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
    public void testAskSavesTheQuestionWithTheTextTrimmed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(argThat(question -> "Hay estacionamiento?".equals(question.getText()))))
                .thenReturn(created());

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "  Hay estacionamiento?  ");

        // Assert
        assertEquals(QuestionResult.CREATED, result);
    }

    @Test
    public void testAskSavesTheQuestionAgainstTheEventAndTheAsker() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(argThat(question -> question.getEventId() == EVENT_ID && question.getUserId() == 2L)))
                .thenReturn(created());

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hay estacionamiento?");

        // Assert
        assertEquals(QuestionResult.CREATED, result);
    }

    @Test
    public void testAskTellsTheOrganizerInTheInboxWhoAsked() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(any(EventQuestion.class))).thenReturn(created());

        // Act
        questionService.ask(EVENT_ID, asker(), "Hay estacionamiento?");

        // Assert
        final RecordingInboxService.Added message = inbox.to(CREATOR_EMAIL).get(0);
        assertEquals(InboxMessageType.NEW_QUESTION, message.type);
        assertEquals("Ana", message.actorName);
    }

    @Test
    public void testAskTellsTheOrganizerWithTheEmailWhenTheAskerHasNoName() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(questionDao.create(any(EventQuestion.class))).thenReturn(created());
        final User noName = new User(ASKER_EMAIL, "  ");
        noName.setId(2);

        // Act
        questionService.ask(EVENT_ID, noName, "Hay estacionamiento?");

        // Assert
        assertEquals(ASKER_EMAIL, inbox.to(CREATOR_EMAIL).get(0).actorName);
    }

    @Test
    public void testAskMailsTheOrganizerTheNewQuestionWithTheAskerData() {
        // Arrange
        final Event event = eventInHours(48);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(questionDao.create(any(EventQuestion.class))).thenReturn(created());

        // Act
        questionService.ask(EVENT_ID, asker(), "Hay estacionamiento?");

        // Assert
        final Object[] args = notifications.argsOf("notifyNewQuestion").get(0);
        assertSame(event, args[0]);
        assertEquals(ASKER_EMAIL, ((EventQuestion) args[1]).getAskerEmail());
        assertEquals("Ana", ((EventQuestion) args[1]).getAskerName());
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

    @Test
    public void testCannotAskOnAnEventThatDoesNotExist() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert
        assertEquals(QuestionResult.NOT_ALLOWED, result);
    }

    @Test
    public void testAQuestionThatIsNotAllowedIsNotNotified() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));

        // Act
        questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert
        assertTrue(inbox.all().isEmpty());
        assertTrue(notifications.methods().isEmpty());
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
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, " Si, en la esquina ");

        // Assert
        assertEquals(AnswerResult.ANSWERED, result);
    }

    @Test
    public void testAnswerTellsTheAskerInTheInbox() {
        // Arrange
        answerableQuestion("Si", true);

        // Act
        questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(InboxMessageType.QUESTION_ANSWERED, inbox.to(ASKER_EMAIL).get(0).type);
    }

    @Test
    public void testAnswerMailsTheAskerTheAnsweredQuestion() {
        // Arrange
        final Event event = eventInHours(48);
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(questionDao.answer(eq(QUESTION_ID), eq("Si"), any(Date.class))).thenReturn(true);

        // Act
        questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        final Object[] args = notifications.argsOf("notifyQuestionAnswered").get(0);
        assertSame(event, args[0]);
        assertEquals("Si", ((EventQuestion) args[1]).getAnswer());
    }

    @Test
    public void testSomeoneElseCannotAnswer() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, OTHER_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
    }

    @Test
    public void testAnAnsweredQuestionIsNotAnsweredAgain() {
        // Arrange
        answerableQuestion("Si", false);

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.ALREADY_ANSWERED, result);
    }

    @Test
    public void testWhenTheQuestionWasAlreadyAnsweredNobodyIsNotifiedAgain() {
        // Arrange
        answerableQuestion("Si", false);

        // Act
        questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertTrue(inbox.all().isEmpty());
        assertTrue(notifications.methods().isEmpty());
    }

    /** Una pregunta de otro evento no se responde desde la URL de este. */
    @Test
    public void testAQuestionOfAnotherEventCannotBeAnsweredFromThisOne() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(99)));

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
    }

    @Test
    public void testAQuestionThatDoesNotExistCannotBeAnswered() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.empty());

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
    }

    @Test
    public void testAQuestionOfAnEventThatDoesNotExistCannotBeAnswered() {
        // Arrange
        when(questionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
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
    public void testDeleteByAdminReturnsTrueWhenTheQuestionWasRemoved() {
        // Arrange
        when(questionDao.delete(QUESTION_ID)).thenReturn(true);

        // Act
        final boolean result = questionService.deleteByAdmin(QUESTION_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testDeleteByAdminReturnsFalseWhenThereWasNoSuchQuestion() {
        // Arrange
        when(questionDao.delete(QUESTION_ID)).thenReturn(false);

        // Act
        final boolean result = questionService.deleteByAdmin(QUESTION_ID);

        // Assert
        assertFalse(result);
    }
}
