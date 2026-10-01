package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InboxServiceImplTest {

    @Mock
    private InboxMessageDao mockDao;

    // Enlace optimista del Grupo B (ver InboxServiceImpl.add()).
    @Mock
    private UserService mockUserService;

    @InjectMocks
    private InboxServiceImpl inboxService;

    // create() es lo que persiste el mensaje: se captura con doAnswer() sobre
    // el stub (no con Mockito.verify()+ArgumentCaptor) para poder inspeccionar
    // despues del Act que InboxMessage le llego.
    private InboxMessage[] captureCreatedMessage() {
        final InboxMessage[] captured = new InboxMessage[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            return captured[0];
        }).when(mockDao).create(any(InboxMessage.class));
        return captured;
    }

    @Test
    public void testAddNormalizesTheEmailAndKeepsTheData() {
        // Arrange
        when(mockUserService.findByEmail("ana@mail.com")).thenReturn(Optional.empty());
        final InboxMessage[] created = captureCreatedMessage();

        // Act
        inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        // Assert
        assertEquals("ana@mail.com", created[0].getRecipientEmail());
        assertEquals(InboxMessageType.NEW_PENDING, created[0].getType());
        assertEquals(Long.valueOf(7), created[0].getEventId());
        assertEquals("Juan", created[0].getActorName());
        assertNull(created[0].getUserId());
    }

    @Test
    public void testAddLinksTheRecipientsUserIdWhenTheyAlreadyHaveAnAccount() {
        // Arrange
        final User recipient = new User("ana@mail.com", "Ana");
        recipient.setId(55);
        when(mockUserService.findByEmail("ana@mail.com")).thenReturn(Optional.of(recipient));
        final InboxMessage[] created = captureCreatedMessage();

        // Act
        inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        // Assert
        assertEquals(Long.valueOf(55), created[0].getUserId());
    }

    private static final long USER_ID = 55;

    @Test
    public void testFindPageTreatsAnInvalidPageAsTheFirst() {
        // Arrange
        when(mockDao.findByRecipientUserId(USER_ID, 1, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByUserId(USER_ID)).thenReturn(0L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
    }

    @Test
    public void testFindPageCalculatesTotalPages() {
        // Arrange
        when(mockDao.findByRecipientUserId(USER_ID, 2, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByUserId(USER_ID)).thenReturn(45L);

        // Act
        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, 2);

        // Assert
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testMarkAllReadDelegatesToTheDao() {
        // Arrange
        final long[] capturedUserId = new long[1];
        final Date[] capturedDate = new Date[1];
        doAnswer(invocation -> {
            capturedUserId[0] = invocation.getArgument(0);
            capturedDate[0] = invocation.getArgument(1);
            return null;
        }).when(mockDao).markAllReadByUserId(org.mockito.ArgumentMatchers.anyLong(), any(Date.class));

        // Act
        inboxService.markAllRead(USER_ID);

        // Assert
        assertEquals(USER_ID, capturedUserId[0]);
        assertEquals(new java.util.Date().getTime(), capturedDate[0].getTime(), 5000);
    }

    @Test
    public void testCountUnreadDelegatesToTheDao() {
        // Arrange
        when(mockDao.countUnreadByUserId(USER_ID)).thenReturn(4L);

        // Act
        final long result = inboxService.countUnread(USER_ID);

        // Assert
        assertEquals(4, result);
    }
}
