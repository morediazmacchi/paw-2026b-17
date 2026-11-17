package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
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
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));
        when(chatDao.addMember(REGISTRATION_ID, EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, USER_ID);

        // Assert
        assertEquals(ChatJoinResult.JOINED, result);
    }

    @Test
    public void testJoinWhenAlreadyAMemberReturnsAlreadyMember() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, USER_ID);

        // Assert
        assertEquals(ChatJoinResult.ALREADY_MEMBER, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testJoinWithoutRegistrationThrowsEventNotFoundException() {
        // Arrange: el permiso lo decide @PreAuthorize; si la inscripcion ya no esta (se dio de baja
        // justo entre medio), no hay id con que agregar al miembro.
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        chatService.join(EVENT_ID, USER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testJoinForAMissingEventThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        chatService.join(EVENT_ID, USER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testJoinAFinishedEventIsClosed() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventInHours(-2)));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, USER_ID);

        // Assert
        assertEquals(ChatJoinResult.CLOSED, result);
    }

    @Test
    public void testJoinACancelledEventIsClosed() {
        // Arrange
        final Event cancelled = eventInHours(48);
        cancelled.setCancelledAt(new Date());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));
        when(registrationService.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(registration(RegistrationStatus.CONFIRMED)));

        // Act
        final ChatJoinResult result = chatService.join(EVENT_ID, USER_ID);

        // Assert
        assertEquals(ChatJoinResult.CLOSED, result);
    }

    // ---- leave() / isMember() ----

    @Test
    public void testLeaveIsTrueWhenThePersonWasInTheGroup() {
        // Arrange
        when(chatDao.removeMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act + Assert
        assertTrue(chatService.leave(EVENT_ID, USER_ID));
    }

    @Test
    public void testIsMemberIsTrueForAnActiveMemberOfTheGroup() {
        // Arrange
        when(chatDao.isMember(EVENT_ID, USER_ID)).thenReturn(true);

        // Act + Assert
        assertTrue(chatService.isMember(EVENT_ID, USER_ID));
    }

    // ---- findMessages() ----

    @Test
    public void testFindMessagesWithoutCursorReturnsTheLatest() {
        // Arrange
        final List<ChatMessage> latest = Arrays.asList(new ChatMessage(ModelFixtures.event(EVENT_ID), ModelFixtures.user(5), "hola"));
        when(chatDao.findLatest(EVENT_ID, EventChatServiceImpl.INITIAL_MESSAGES)).thenReturn(latest);

        // Act
        final List<ChatMessage> result = chatService.findMessages(EVENT_ID, 0);

        // Assert
        assertEquals(latest, result);
    }

    @Test
    public void testFindMessagesWithCursorReturnsOnlyNewerOnes() {
        // Arrange
        final List<ChatMessage> newer = Arrays.asList(new ChatMessage(ModelFixtures.event(EVENT_ID), ModelFixtures.user(5), "nuevo"));
        when(chatDao.findAfter(EVENT_ID, 40, EventChatServiceImpl.MAX_NEW_MESSAGES)).thenReturn(newer);

        // Act
        final List<ChatMessage> result = chatService.findMessages(EVENT_ID, 40);

        // Assert
        assertEquals(newer, result);
    }

    // ---- openRoom() ----

    // El id lo pone la base: para un mensaje "ya guardado" se devuelve ese id desde su getId() publico.
    private static ChatMessage messageWithId(final long id) {
        return new ChatMessage(ModelFixtures.event(EVENT_ID), ModelFixtures.user(5), "mensaje " + id) {
            @Override
            public Long getId() {
                return id;
            }
        };
    }

    @Test
    public void testOpenRoomHoldsTheEventAndTheLatestMessages() {
        // Arrange
        final Event event = eventInHours(48);
        final List<ChatMessage> latest = Arrays.asList(new ChatMessage(ModelFixtures.event(EVENT_ID), ModelFixtures.user(5), "hola"));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(chatDao.findLatest(EVENT_ID, EventChatServiceImpl.INITIAL_MESSAGES)).thenReturn(latest);

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertSame(event, room.getEvent());
        assertEquals(latest, room.getMessages());
    }

    @Test
    public void testOpenRoomOfAnUpcomingEventIsOpen() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertFalse(room.isClosed());
    }

    @Test
    public void testOpenRoomOfAnEventThatAlreadyHappenedIsClosed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-48)));

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertTrue(room.isClosed());
    }

    @Test
    public void testOpenRoomOfACancelledEventIsClosed() {
        // Arrange
        final Event event = eventInHours(48);
        event.setCancelledAt(new Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertTrue(room.isClosed());
    }

    @Test
    public void testOpenRoomLastIdIsTheIdOfTheLastMessage() {
        // Arrange
        final ChatMessage first = messageWithId(30L);
        final ChatMessage last = messageWithId(31L);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));
        when(chatDao.findLatest(EVENT_ID, EventChatServiceImpl.INITIAL_MESSAGES)).thenReturn(Arrays.asList(first, last));

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertEquals(31L, room.getLastId());
    }

    @Test
    public void testOpenRoomWithoutMessagesHasLastIdZero() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final ChatRoom room = chatService.openRoom(EVENT_ID).get();

        // Assert
        assertEquals(0L, room.getLastId());
    }

    @Test
    public void testOpenRoomOfAMissingEventIsEmpty() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<ChatRoom> result = chatService.openRoom(EVENT_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- send() ----

    @Test
    public void testSendByAMemberOfAnOpenChatReturnsSent() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, USER_ID, "  hola grupo  ");

        // Assert
        assertEquals(ChatSendResult.SENT, result);
    }

    @Test
    public void testSendInAFinishedEventIsClosed() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(-1)));

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, USER_ID, "hola");

        // Assert
        assertEquals(ChatSendResult.CLOSED, result);
    }

    @Test
    public void testSendInACancelledEventIsClosed() {
        // Arrange
        final Event cancelled = eventInHours(48);
        cancelled.setCancelledAt(new Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, USER_ID, "hola");

        // Assert
        assertEquals(ChatSendResult.CLOSED, result);
    }

    @Test
    public void testSendABlankMessageIsInvalid() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, USER_ID, "   ");

        // Assert
        assertEquals(ChatSendResult.INVALID, result);
    }

    @Test
    public void testSendATooLongMessageIsInvalid() {
        // Arrange
        final char[] chars = new char[EventChatService.MAX_MESSAGE_LENGTH + 1];
        Arrays.fill(chars, 'a');
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventInHours(48)));

        // Act
        final ChatSendResult result = chatService.send(EVENT_ID, USER_ID, new String(chars));

        // Assert
        assertEquals(ChatSendResult.INVALID, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testSendForAMissingEventThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        chatService.send(EVENT_ID, USER_ID, "hola");
    }

    // ---- findGroupsPage() ----

    private static ChatGroup group() {
        return new ChatGroup(EVENT_ID, "Recital", null, 0, null);
    }

    @Test
    public void testFindGroupsPageReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 grupos, de a EventChatServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<ChatGroup> pageTwo = Collections.singletonList(group());
        when(chatDao.countGroupsByUser(USER_ID)).thenReturn(25L);
        when(chatDao.findGroupsByUser(USER_ID, 2, EventChatServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<ChatGroup> result = chatService.findGroupsPage(USER_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindGroupsPageClampsAPageBeyondTheLastToTheLastOne() {
        // Arrange: 25 grupos -> la ultima pagina es la 3
        final List<ChatGroup> lastPage = Collections.singletonList(group());
        when(chatDao.countGroupsByUser(USER_ID)).thenReturn(25L);
        when(chatDao.findGroupsByUser(USER_ID, 3, EventChatServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<ChatGroup> result = chatService.findGroupsPage(USER_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindGroupsPageClampsAPageBelowOneToTheFirstOne() {
        // Arrange
        final List<ChatGroup> firstPage = Collections.singletonList(group());
        when(chatDao.countGroupsByUser(USER_ID)).thenReturn(25L);
        when(chatDao.findGroupsByUser(USER_ID, 1, EventChatServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<ChatGroup> result = chatService.findGroupsPage(USER_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    @Test
    public void testFindGroupsPageWithNoGroupsIsAnEmptyFirstPage() {
        // Arrange: el mock devuelve 0 grupos y una lista vacia

        // Act
        final PageResult<ChatGroup> result = chatService.findGroupsPage(USER_ID, 5);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }
}
