package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventQuestionDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventQuestionServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long QUESTION_ID = 7;
    private static final String CREATOR_EMAIL = "org@mail.com";
    private static final long CREATOR_ID = 1;
    private static final long OTHER_ID = 999;
    private static final String ASKER_EMAIL = "ana@mail.com";

    @Mock
    private EventQuestionDao mockQuestionDao;
    @Mock
    private EventService mockEventService;
    @Mock
    private InboxService mockInboxService;
    @Mock
    private NotificationService mockNotificationService;

    @InjectMocks
    private EventQuestionServiceImpl questionService;

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
        final EventQuestion question = new EventQuestion(eventId, 2, "Hay estacionamiento?");
        question.setId(QUESTION_ID);
        question.setAskerEmail(ASKER_EMAIL);
        return question;
    }

    // ---- ask() ----

    @Test
    public void testAskSavesAndNotifiesTheOrganizer() {
        // Arrange
        final Event event = eventInHours(48);
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockQuestionDao.create(any(EventQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
        // Los colaboradores void (bandeja/notificacion) se capturan con
        // doAnswer() sobre el stub, nunca con Mockito.verify().
        final String[] inboxRecipient = new String[1];
        doAnswer(invocation -> {
            inboxRecipient[0] = invocation.getArgument(0);
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());
        final boolean[] organizerNotified = {false};
        doAnswer(invocation -> {
            organizerNotified[0] = true;
            return null;
        }).when(mockNotificationService).notifyNewQuestion(eq(event), any(EventQuestion.class));

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "  Hay estacionamiento?  ");

        // Assert
        assertEquals(QuestionResult.CREATED, result);
        assertEquals(CREATOR_EMAIL, inboxRecipient[0]);
        assertTrue(organizerNotified[0]);
    }

    @Test
    public void testTheOrganizerCannotAskOnTheirOwnEvent() {
        // Arrange
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
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
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));

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
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final QuestionResult result = questionService.ask(EVENT_ID, asker(), "Hola?");

        // Assert
        assertEquals(QuestionResult.CLOSED, result);
    }

    // ---- answer() ----

    @Test
    public void testTheOrganizerAnswersAndTheAskerIsNotified() {
        // Arrange
        final Event event = eventInHours(48);
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockQuestionDao.answer(eq(QUESTION_ID), eq("Si, en la esquina"), any(Date.class))).thenReturn(true);
        final String[] inboxRecipient = new String[1];
        doAnswer(invocation -> {
            inboxRecipient[0] = invocation.getArgument(0);
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());
        final boolean[] askerNotified = {false};
        doAnswer(invocation -> {
            askerNotified[0] = true;
            return null;
        }).when(mockNotificationService).notifyQuestionAnswered(eq(event), any(EventQuestion.class));

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, " Si, en la esquina ");

        // Assert
        assertEquals(AnswerResult.ANSWERED, result);
        assertEquals(ASKER_EMAIL, inboxRecipient[0]);
        assertTrue(askerNotified[0]);
    }

    @Test
    public void testSomeoneElseCannotAnswer() {
        // Arrange
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, OTHER_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
    }

    @Test
    public void testAnAnsweredQuestionIsNotAnsweredAgain() {
        // Arrange
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(mockQuestionDao.answer(eq(QUESTION_ID), org.mockito.ArgumentMatchers.anyString(), any(Date.class)))
                .thenReturn(false);

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.ALREADY_ANSWERED, result);
    }

    /** Una pregunta de otro evento no se responde desde la URL de este. */
    @Test
    public void testAQuestionOfAnotherEventCannotBeAnsweredFromThisOne() {
        // Arrange
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(99)));

        // Act
        final AnswerResult result = questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si");

        // Assert
        assertEquals(AnswerResult.NOT_ALLOWED, result);
    }

    // ---- deleteByAdmin() (moderacion) ----

    @Test
    public void testDeleteByAdminDelegatesToTheDao() {
        // Arrange
        // delete() es void: se captura el id con el que se lo llamo (con
        // doAnswer(), no con Mockito.verify()).
        final long[] deletedId = new long[1];
        doAnswer(invocation -> {
            deletedId[0] = invocation.getArgument(0);
            return null;
        }).when(mockQuestionDao).delete(anyLong());

        // Act
        questionService.deleteByAdmin(QUESTION_ID);

        // Assert
        assertEquals(QUESTION_ID, deletedId[0]);
    }
}
