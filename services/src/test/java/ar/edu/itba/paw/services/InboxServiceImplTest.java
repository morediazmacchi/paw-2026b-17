package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InboxServiceImplTest {

    @Mock
    private InboxMessageDao mockDao;

    @InjectMocks
    private InboxServiceImpl inboxService;

    @Test
    public void testAddNormalizesTheEmailAndKeepsTheData() {
        inboxService.add("  Ana@Mail.com ", InboxMessageType.NEW_PENDING, 7L, "Juan", null);

        final ArgumentCaptor<InboxMessage> saved = ArgumentCaptor.forClass(InboxMessage.class);
        verify(mockDao).create(saved.capture());
        assertEquals("ana@mail.com", saved.getValue().getRecipientEmail());
        assertEquals(InboxMessageType.NEW_PENDING, saved.getValue().getType());
        assertEquals(Long.valueOf(7), saved.getValue().getEventId());
        assertEquals("Juan", saved.getValue().getActorName());
    }

    @Test
    public void testFindPageTreatsAnInvalidPageAsTheFirst() {
        when(mockDao.findByRecipient("ana@mail.com", 1, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByRecipient("ana@mail.com")).thenReturn(0L);

        final PageResult<InboxMessage> result = inboxService.findPage("Ana@mail.com", -3);

        assertEquals(1, result.getPage());
    }

    @Test
    public void testFindPageCalculatesTotalPages() {
        when(mockDao.findByRecipient("ana@mail.com", 2, InboxServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());
        when(mockDao.countByRecipient("ana@mail.com")).thenReturn(45L);

        assertEquals(3, inboxService.findPage("ana@mail.com", 2).getTotalPages());
    }

    @Test
    public void testMarkAllReadNormalizesTheEmail() {
        inboxService.markAllRead("ANA@mail.com");

        verify(mockDao).markAllRead(eq("ana@mail.com"), any(Date.class));
    }

    @Test
    public void testCountUnreadNormalizesTheEmail() {
        when(mockDao.countUnread("ana@mail.com")).thenReturn(4L);

        assertEquals(4, inboxService.countUnread(" ana@MAIL.com"));
    }
}
