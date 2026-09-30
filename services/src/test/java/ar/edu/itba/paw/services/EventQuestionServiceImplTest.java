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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        final Event event = eventInHours(48);
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockQuestionDao.create(any(EventQuestion.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals(QuestionResult.CREATED, questionService.ask(EVENT_ID, asker(), "  Hay estacionamiento?  "));

        verify(mockQuestionDao).create(any(EventQuestion.class));
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.NEW_QUESTION, EVENT_ID, "Ana", null);
        verify(mockNotificationService).notifyNewQuestion(eq(event), any(EventQuestion.class));
    }

    @Test
    public void testTheOrganizerCannotAskOnTheirOwnEvent() {
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        final User organizer = new User(CREATOR_EMAIL, "Org");
        organizer.setId(CREATOR_ID);

        assertEquals(QuestionResult.OWN_EVENT, questionService.ask(EVENT_ID, organizer, "Hola?"));

        verify(mockQuestionDao, never()).create(any());
        verify(mockNotificationService, never()).notifyNewQuestion(any(), any());
    }

    @Test
    public void testCannotAskOnAPastEvent() {
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));

        assertEquals(QuestionResult.CLOSED, questionService.ask(EVENT_ID, asker(), "Hola?"));

        verify(mockQuestionDao, never()).create(any());
    }

    @Test
    public void testCannotAskOnACancelledEvent() {
        final Event event = eventInHours(48);
        event.setCancelledAt(new Date());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertEquals(QuestionResult.CLOSED, questionService.ask(EVENT_ID, asker(), "Hola?"));

        verify(mockQuestionDao, never()).create(any());
    }

    // ---- answer() ----

    @Test
    public void testTheOrganizerAnswersAndTheAskerIsNotified() {
        final Event event = eventInHours(48);
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockQuestionDao.answer(eq(QUESTION_ID), eq("Si, en la esquina"), any(Date.class))).thenReturn(true);

        assertEquals(AnswerResult.ANSWERED,
                questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, " Si, en la esquina "));

        verify(mockInboxService).add(ASKER_EMAIL, InboxMessageType.QUESTION_ANSWERED, EVENT_ID, null, null);
        verify(mockNotificationService).notifyQuestionAnswered(eq(event), any(EventQuestion.class));
    }

    @Test
    public void testSomeoneElseCannotAnswer() {
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        assertEquals(AnswerResult.NOT_ALLOWED,
                questionService.answer(EVENT_ID, QUESTION_ID, OTHER_ID, "Si"));

        verify(mockQuestionDao, never()).answer(anyLong(), anyString(), any());
    }

    @Test
    public void testAnAnsweredQuestionIsNotAnsweredAgain() {
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(EVENT_ID)));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(mockQuestionDao.answer(eq(QUESTION_ID), anyString(), any(Date.class))).thenReturn(false);

        assertEquals(AnswerResult.ALREADY_ANSWERED,
                questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si"));

        verify(mockNotificationService, never()).notifyQuestionAnswered(any(), any());
    }

    /** Una pregunta de otro evento no se responde desde la URL de este. */
    @Test
    public void testAQuestionOfAnotherEventCannotBeAnsweredFromThisOne() {
        when(mockQuestionDao.findById(QUESTION_ID)).thenReturn(Optional.of(questionOf(99)));

        assertEquals(AnswerResult.NOT_ALLOWED,
                questionService.answer(EVENT_ID, QUESTION_ID, CREATOR_ID, "Si"));

        verify(mockQuestionDao, never()).answer(anyLong(), anyString(), any());
    }

    // ---- deleteByAdmin() (moderacion) ----

    @Test
    public void testDeleteByAdminDelegatesToTheDao() {
        questionService.deleteByAdmin(QUESTION_ID);

        verify(mockQuestionDao).delete(QUESTION_ID);
    }
}
