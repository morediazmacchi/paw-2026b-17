package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventReminderServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String CREATOR_EMAIL = "org@mail.com";

    @Mock
    private EventDao mockEventDao;
    @Mock
    private RegistrationService mockRegistrationService;
    @Mock
    private NotificationService mockNotificationService;
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private EventReminderServiceImpl reminderService;

    private static Event eventInHours(final long hours) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        return event;
    }

    @Test
    public void testSendsMailAndInboxToConfirmedAndToTheOrganizer() {
        final Event event = eventInHours(10);
        final Registration confirmada = new Registration(EVENT_ID, "conf@mail.com", "C", RegistrationStatus.CONFIRMED);
        final Registration espera = new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED);
        final Registration pendiente = new Registration(EVENT_ID, "pend@mail.com", "P", RegistrationStatus.PENDING);
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmada, espera, pendiente));

        assertTrue(reminderService.sendReminder(EVENT_ID));

        verify(mockNotificationService).notifyEventReminder(event, confirmada);
        verify(mockInboxService).add("conf@mail.com", InboxMessageType.EVENT_REMINDER, EVENT_ID, null, null);
        verify(mockNotificationService, never()).notifyEventReminder(event, espera);
        verify(mockNotificationService, never()).notifyEventReminder(event, pendiente);
        verify(mockInboxService, never()).add(eq("espera@mail.com"), any(), any(), any(), any());
        verify(mockInboxService, never()).add(eq("pend@mail.com"), any(), any(), any(), any());

        verify(mockNotificationService).notifyOrganizerEventReminder(event);
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.EVENT_TOMORROW, EVENT_ID, null, null);
    }

    /** Si otra corrida ya lo reclamo, no se manda nada: nunca dos recordatorios. */
    @Test
    public void testDoesNothingWhenTheReminderWasAlreadySent() {
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(false);

        assertFalse(reminderService.sendReminder(EVENT_ID));

        verify(mockNotificationService, never()).notifyOrganizerEventReminder(any());
        verify(mockInboxService, never()).add(any(), any(), any(), any(), any());
    }

    @Test
    public void testDoesNothingForACancelledEvent() {
        final Event event = eventInHours(10);
        event.setCancelledAt(new Date());
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));

        assertFalse(reminderService.sendReminder(EVENT_ID));

        verify(mockNotificationService, never()).notifyOrganizerEventReminder(any());
        verify(mockInboxService, never()).add(any(), any(), any(), any(), any());
    }

    @Test
    public void testFindEventsLooksAtTheNext24Hours() {
        reminderService.findEventsNeedingReminder();

        final org.mockito.ArgumentCaptor<Date> from = org.mockito.ArgumentCaptor.forClass(Date.class);
        final org.mockito.ArgumentCaptor<Date> until = org.mockito.ArgumentCaptor.forClass(Date.class);
        verify(mockEventDao).findIdsNeedingReminder(from.capture(), until.capture());
        assertTrue(until.getValue().getTime() - from.getValue().getTime() == EventReminderServiceImpl.ONE_DAY_MILLIS);
        verify(mockEventDao, never()).markReminderSent(anyLong(), any(Date.class));
    }
}
