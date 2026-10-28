package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventChatDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventChatServiceImplTest {

    private static final long EVENT_ID = 10;
    private static final long USER_ID = 2;
    private static final long REGISTRATION_ID = 77;

    @Mock
    private EventChatDao chatDao;
    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;

    private EventChatServiceImpl chatService;

    @Before
    public void setUp() {
        chatService = new EventChatServiceImpl(chatDao, eventService, registrationService);
    }

    private static Event eventInHours(final long hours) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        return event;
    }

    private static User user() {
        final User user = new User("ana@mail.com", "Ana");
        user.setId(USER_ID);
        return user;
    }

    private static Registration registration(final RegistrationStatus status) {
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), user(), "ana@mail.com", "Ana", status);
        registration.setId(REGISTRATION_ID);
        return registration;
    }

    // ---- join() ----

    @Test
    public void testJoinReturnsJoinedForAConfirmedRegistration() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));
        when(chatDao.addMember(REGISTRATION_ID, EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.JOINED, result);
    }

    @Test
    public void testJoinWhenAlreadyAMemberReturnsAlreadyMember() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.ALREADY_MEMBER, result);
    }

    @Test
    public void testJoinWithoutRegistrationIsNotAllowed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.NOT_ALLOWED, result);
    }

    @Test
    public void testJoinWithAPendingRegistrationIsNotAllowed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.PENDING)));

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.NOT_ALLOWED, result);
    }

    @Test
    public void testJoinForAMissingEventIsNotAllowed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.NOT_ALLOWED, result);
    }

    @Test
    public void testJoinAFinishedEventIsClosed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.CLOSED, result);
    }

    @Test
    public void testJoinACancelledEventIsClosed() {
        // Arrange
        final Event cancelled = eventInHours(48);
        cancelled.setCancelledAt(new Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, user());

        // Assert
        assertEquals(ChatJoinResult.CLOSED, result);
    }

    // ---- leave() / isMember() ----

    @Test
    public void testLeaveDelegatesToTheDao() {
        // Arrange
        when(chatDao.removeMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act + Assert
        assertTrue(chatService.leave(EVENT_ID, USER_ID));
    }

    @Test
    public void testIsMemberDelegatesToTheDao() {
        // Arrange
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act + Assert
        assertTrue(chatService.isMember(EVENT_ID, USER_ID));
    }

    // ---- findMessages() ----

    @Test
    public void testFindMessagesForANonMemberIsEmpty() {
        // Arrange
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(false);

        // Act
        final Optional<List<ChatMessage>> result = chatService.findMessages(EVENT_ID, USER_ID, 0);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindMessagesWithoutCursorReturnsTheLatest() {
        // Arrange
        final List<ChatMessage> latest = Arrays.asList(new ChatMessage(EVENT_ID, 5, "hola"));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);
        when(chatDao.findLatest(EVENT_ID, EventChatServiceImpl.INITIAL_MESSAGES)).thenReturn(latest);

        // Act
        final Optional<List<ChatMessage>> result = chatService.findMessages(EVENT_ID, USER_ID, 0);

        // Assert
        assertEquals(latest, result.get());
    }

    @Test
    public void testFindMessagesWithCursorReturnsOnlyNewerOnes() {
        // Arrange
        final List<ChatMessage> newer = Arrays.asList(new ChatMessage(EVENT_ID, 5, "nuevo"));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);
        when(chatDao.findAfter(EVENT_ID, 40, EventChatServiceImpl.MAX_NEW_MESSAGES)).thenReturn(newer);

        // Act
        final Optional<List<ChatMessage>> result = chatService.findMessages(EVENT_ID, USER_ID, 40);

        // Assert
        assertEquals(newer, result.get());
    }

    // ---- send() ----

    @Test
    public void testSendByAMemberOfAnOpenChatReturnsSent() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "  hola grupo  ");

        // Assert
        assertEquals(ChatSendResult.SENT, result);
    }

    @Test
    public void testSendByANonMemberIsNotAllowed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(false);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "hola");

        // Assert
        assertEquals(ChatSendResult.NOT_ALLOWED, result);
    }

    @Test
    public void testSendInAFinishedEventIsClosed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-1)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "hola");

        // Assert
        assertEquals(ChatSendResult.CLOSED, result);
    }

    @Test
    public void testSendInACancelledEventIsClosed() {
        // Arrange
        final Event cancelled = eventInHours(48);
        cancelled.setCancelledAt(new Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "hola");

        // Assert
        assertEquals(ChatSendResult.CLOSED, result);
    }

    @Test
    public void testSendABlankMessageIsInvalid() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "   ");

        // Assert
        assertEquals(ChatSendResult.INVALID, result);
    }

    @Test
    public void testSendATooLongMessageIsInvalid() {
        // Arrange
        final char[] chars = new char[EventChatService.MAX_MESSAGE_LENGTH + 1];
        Arrays.fill(chars, 'a');
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), new String(chars));

        // Assert
        assertEquals(ChatSendResult.INVALID, result);
    }

    @Test
    public void testSendForAMissingEventIsNotAllowed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, user(), "hola");

        // Assert
        assertEquals(ChatSendResult.NOT_ALLOWED, result);
    }
}
