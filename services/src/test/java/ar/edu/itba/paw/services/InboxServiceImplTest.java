package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: InboxMessageDao y UserService son @Mock. Los stubs con
 * argumentos especificos hacen de expectativa (si el service arma mal el mensaje, no
 * matchean y el test falla) y se asierta sobre lo que devuelve el service.
 */
@RunWith(MockitoJUnitRunner.class)
public class InboxServiceImplTest {

    private static final long USER_ID = 55;

    @Mock
    private InboxMessageDao inboxDao;
    @Mock
    private UserService userService;
    @Mock
    private EventDao eventDao;

    private InboxServiceImpl inboxService;

    @Before
    public void setUp() {
        inboxService = new InboxServiceImpl(inboxDao, userService, eventDao);
    }

    private static InboxMessage anyMessage() {
        return new InboxMessage("ana@mail.com", InboxMessageType.NEW_PENDING, ModelFixtures.event(7L), "Juan", null);
    }

    // ---- add() ----

    @Test
    public void testAddNormalizesTheEmail() {
        // Arrange
        final InboxMessage saved = anyMessage();
        when(eventDao.findById(7L)).thenReturn(Optional.of(ModelFixtures.event(7L)));
        when(userService.findByEmail("ana@mail.com")).thenReturn(Optional.empty());
        when(inboxDao.create(any(InboxMessage.class)))
                .thenReturn(saved);

        // Act
        final InboxMessage result = inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        // Assert
        assertSame(saved, result);
    }

    // ---- findPage() ----

    @Test
    public void testFindPageTreatsAnInvalidPageAsTheFirst() {
        // Arrange
        final List<InboxMessage> messages = Arrays.asList(anyMessage(), anyMessage(), anyMessage());
        when(inboxDao.findByRecipientUserId(USER_ID, 1, InboxServiceImpl.PAGE_SIZE)).thenReturn(messages);
        when(inboxDao.countByUserId(USER_ID)).thenReturn(3L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(messages, result.getContent());
    }

    @Test
    public void testFindPageBeyondTheLastFallsBackToTheLastPage() {
        // Arrange: 42 mensajes, de a 20 por pagina -> la ultima pagina valida es la 3.
        final List<InboxMessage> lastPage = Arrays.asList(anyMessage(), anyMessage());
        when(inboxDao.findByRecipientUserId(USER_ID, 3, InboxServiceImpl.PAGE_SIZE)).thenReturn(lastPage);
        when(inboxDao.countByUserId(USER_ID)).thenReturn(42L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindPageCalculatesTotalPages() {
        // Arrange
        when(inboxDao.findByRecipientUserId(USER_ID, 2, InboxServiceImpl.PAGE_SIZE))
                .thenReturn(Collections.emptyList());
        when(inboxDao.countByUserId(USER_ID)).thenReturn(45L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, 2);

        // Assert
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testFindPageReturnsTheRequestedPageAndTheTotalCount() {
        // Arrange
        final List<InboxMessage> lastPage = Arrays.asList(anyMessage(), anyMessage());
        when(inboxDao.findByRecipientUserId(USER_ID, 3, InboxServiceImpl.PAGE_SIZE)).thenReturn(lastPage);
        when(inboxDao.countByUserId(USER_ID)).thenReturn(42L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, 3);

        // Assert
        assertEquals(lastPage, result.getContent());
        assertEquals(42L, result.getTotalElements());
        assertEquals(3, result.getPage());
    }

    // ---- findPageWithEvents() ----

    private static InboxMessage messageAbout(final long eventId) {
        return new InboxMessage("ana@mail.com", InboxMessageType.NEW_PENDING, ModelFixtures.event(eventId), "Juan", null);
    }

    @Test
    public void testFindPageWithEventsHoldsTheMessagesOfThePage() {
        // Arrange
        final List<InboxMessage> messages = Arrays.asList(messageAbout(7L), messageAbout(8L));
        when(inboxDao.findByRecipientUserId(USER_ID, 1, InboxServiceImpl.PAGE_SIZE)).thenReturn(messages);
        when(inboxDao.countByUserId(USER_ID)).thenReturn(2L);

        // Act
        final InboxPage result = inboxService.findPageWithEvents(USER_ID, 1);

        // Assert
        assertEquals(messages, result.getMessages().getContent());
    }

    @Test
    public void testFindPageWithEventsIndexesTheReferencedEventsById() {
        // Arrange: dos avisos del mismo evento y uno sin evento; el evento se busca una sola vez
        final Event event = ModelFixtures.event(7L);
        final InboxMessage withoutEvent = new InboxMessage("ana@mail.com", InboxMessageType.NEW_PENDING, null, "Juan", null);
        when(inboxDao.findByRecipientUserId(USER_ID, 1, InboxServiceImpl.PAGE_SIZE))
                .thenReturn(Arrays.asList(messageAbout(7L), messageAbout(7L), withoutEvent));
        when(inboxDao.countByUserId(USER_ID)).thenReturn(3L);
        when(eventDao.findByIds(Collections.singletonList(7L))).thenReturn(Collections.singletonList(event));

        // Act
        final InboxPage result = inboxService.findPageWithEvents(USER_ID, 1);

        // Assert
        assertEquals(Collections.singletonMap(7L, event), result.getEventsById());
    }

    // ---- markRead() ----

    @Test
    public void testMarkReadReturnsHowManyMessagesWereMarked() {
        // Arrange
        final List<Long> ids = Arrays.asList(1L, 2L);
        when(inboxDao.markReadByIds(eq(USER_ID), eq(ids), any(Date.class))).thenReturn(2);

        // Act
        final int result = inboxService.markRead(USER_ID, ids);

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testMarkReadWithoutIdsMarksNothing() {
        // Act
        final int result = inboxService.markRead(USER_ID, Collections.emptyList());

        // Assert
        assertEquals(0, result);
    }

    @Test
    public void testMarkReadWithNullIdsMarksNothing() {
        // Act
        final int result = inboxService.markRead(USER_ID, null);

        // Assert
        assertEquals(0, result);
    }

    // ---- countUnread() ----

    @Test
    public void testCountUnreadReturnsTheUnreadCountOfThatUser() {
        // Arrange
        when(inboxDao.countUnreadByUserId(USER_ID)).thenReturn(2L);

        // Act
        final long result = inboxService.countUnread(USER_ID);

        // Assert
        assertEquals(2, result);
    }

    // ---- addForRegistrants() / addForFollowersOfCreator() ----

    @Test
    public void testAddForRegistrantsReturnsHowManyMessagesWereLeft() {
        // Arrange: el stub solo responde con el evento, el tipo y los estados correctos.
        when(inboxDao.createForRegistrants(7L, InboxMessageType.EVENT_CANCELLED,
                EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED))).thenReturn(3);

        // Act
        final int result = inboxService.addForRegistrants(7L, InboxMessageType.EVENT_CANCELLED,
                EnumSet.of(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLISTED));

        // Assert
        assertEquals(3, result);
    }

    @Test
    public void testAddForRegistrantsWithNoStatusesLeavesNothing() {
        // Arrange: senuelo -- si el service le pidiera a la base un "IN ()" vacio, el resultado seria 5.
        lenient().when(inboxDao.createForRegistrants(anyLong(), any(), any())).thenReturn(5);

        // Act
        final int result = inboxService.addForRegistrants(7L, InboxMessageType.EVENT_CANCELLED,
                EnumSet.noneOf(RegistrationStatus.class));

        // Assert
        assertEquals(0, result);
    }

    @Test
    public void testAddForFollowersOfCreatorReturnsHowManyMessagesWereLeft() {
        // Arrange
        when(inboxDao.createForFollowersOfCreator(7L, InboxMessageType.FOLLOWED_USER_NEW_EVENT)).thenReturn(2);

        // Act
        final int result = inboxService.addForFollowersOfCreator(7L, InboxMessageType.FOLLOWED_USER_NEW_EVENT);

        // Assert
        assertEquals(2, result);
    }

    // ---- addForRegistrations() ----

    @Test
    public void testAddForRegistrationsReturnsHowManyMessagesWereLeft() {
        // Arrange: el stub solo responde con el tipo y los ids correctos.
        when(inboxDao.createForRegistrations(InboxMessageType.REGISTERED_WAITLISTED, Arrays.asList(4L, 9L))).thenReturn(2);

        // Act
        final int result = inboxService.addForRegistrations(InboxMessageType.REGISTERED_WAITLISTED, Arrays.asList(4L, 9L));

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testAddForRegistrationsWithNoIdsLeavesNothing() {
        // Arrange: senuelo -- si el service le pidiera a la base un "IN ()" vacio, el resultado seria 5.
        lenient().when(inboxDao.createForRegistrations(any(), any())).thenReturn(5);

        // Act
        final int result = inboxService.addForRegistrations(InboxMessageType.REGISTERED_WAITLISTED,
                Collections.<Long>emptyList());

        // Assert
        assertEquals(0, result);
    }

    // ---- addForOrganizerOfRegistrations() ----

    @Test
    public void testAddForOrganizerOfRegistrationsReturnsHowManyMessagesWereLeft() {
        // Arrange: el stub solo responde con el tipo y los ids correctos.
        when(inboxDao.createForOrganizerOfRegistrations(InboxMessageType.NEW_PENDING, Arrays.asList(4L, 9L))).thenReturn(2);

        // Act
        final int result = inboxService.addForOrganizerOfRegistrations(InboxMessageType.NEW_PENDING, Arrays.asList(4L, 9L));

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testAddForOrganizerOfRegistrationsWithNoIdsLeavesNothing() {
        // Arrange: senuelo -- si el service le pidiera a la base un "IN ()" vacio, el resultado seria 5.
        lenient().when(inboxDao.createForOrganizerOfRegistrations(any(), any())).thenReturn(5);

        // Act
        final int result = inboxService.addForOrganizerOfRegistrations(InboxMessageType.NEW_PENDING,
                Collections.<Long>emptyList());

        // Assert
        assertEquals(0, result);
    }
}
