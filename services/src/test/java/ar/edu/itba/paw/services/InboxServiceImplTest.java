package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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

    @Test
    public void testAddNormalizesTheEmailAndKeepsTheData() {
        when(mockUserService.findByEmail("ana@mail.com")).thenReturn(Optional.empty());

        inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        final ArgumentCaptor<InboxMessage> saved = ArgumentCaptor.forClass(InboxMessage.class);
        verify(mockDao).create(saved.capture());
        assertEquals("ana@mail.com", saved.getValue().getRecipientEmail());
        assertEquals(InboxMessageType.NEW_PENDING, saved.getValue().getType());
        assertEquals(Long.valueOf(7), saved.getValue().getEventId());
        assertEquals("Juan", saved.getValue().getActorName());
        assertNull(saved.getValue().getUserId());
    }

    @Test
    public void testAddLinksTheRecipientsUserIdWhenTheyAlreadyHaveAnAccount() {
        final User recipient = new User("ana@mail.com", "Ana");
        recipient.setId(55);
        when(mockUserService.findByEmail("ana@mail.com")).thenReturn(Optional.of(recipient));

        inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        final ArgumentCaptor<InboxMessage> saved = ArgumentCaptor.forClass(InboxMessage.class);
        verify(mockDao).create(saved.capture());
        assertEquals(Long.valueOf(55), saved.getValue().getUserId());
    }

    private static final long USER_ID = 55;

    @Test
    public void testFindPageTreatsAnInvalidPageAsTheFirst() {
        when(mockDao.findByRecipientUserId(USER_ID, 1, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByUserId(USER_ID)).thenReturn(0L);

        final PageResult<InboxMessage> result = inboxService.findPage(USER_ID, -3);

        assertEquals(1, result.getPage());
    }

    @Test
    public void testFindPageCalculatesTotalPages() {
        when(mockDao.findByRecipientUserId(USER_ID, 2, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByUserId(USER_ID)).thenReturn(45L);

        assertEquals(3, inboxService.findPage(USER_ID, 2).getTotalPages());
    }

    @Test
    public void testMarkAllReadDelegatesToTheDao() {
        inboxService.markAllRead(USER_ID);

        verify(mockDao).markAllReadByUserId(eq(USER_ID), any(Date.class));
    }

    @Test
    public void testCountUnreadDelegatesToTheDao() {
        when(mockDao.countUnreadByUserId(USER_ID)).thenReturn(4L);

        assertEquals(4, inboxService.countUnread(USER_ID));
    }
}
