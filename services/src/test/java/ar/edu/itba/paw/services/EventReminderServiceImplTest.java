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

import java.util.ArrayList;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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
        // Arrange
        final Event event = eventInHours(10);
        final Registration confirmada = new Registration(EVENT_ID, "conf@mail.com", "C", RegistrationStatus.CONFIRMED);
        final Registration espera = new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED);
        final Registration pendiente = new Registration(EVENT_ID, "pend@mail.com", "P", RegistrationStatus.PENDING);
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmada, espera, pendiente));
        // Los metodos void (notificar/escribir en la bandeja) se capturan con
        // doAnswer() sobre el stub, nunca con Mockito.verify().
        final List<Registration> remindedRegistrations = new ArrayList<>();
        doAnswer(invocation -> {
            remindedRegistrations.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyEventReminder(any(), any());
        final List<String> inboxRecipients = new ArrayList<>();
        doAnswer(invocation -> {
            inboxRecipients.add(invocation.getArgument(0));
            return null;
        }).when(mockInboxService).add(anyString(), any(), any(), any(), any());
        final boolean[] organizerNotified = {false};
        doAnswer(invocation -> {
            organizerNotified[0] = true;
            return null;
        }).when(mockNotificationService).notifyOrganizerEventReminder(any());

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertTrue(result);
        assertEquals(Collections.singletonList(confirmada), remindedRegistrations);
        assertTrue(inboxRecipients.contains("conf@mail.com"));
        assertFalse(inboxRecipients.contains("espera@mail.com"));
        assertFalse(inboxRecipients.contains("pend@mail.com"));
        assertTrue(organizerNotified[0]);
        assertTrue(inboxRecipients.contains(CREATOR_EMAIL));
    }

    /** Si otra corrida ya lo reclamo, no se manda nada: nunca dos recordatorios. */
    @Test
    public void testDoesNothingWhenTheReminderWasAlreadySent() {
        // Arrange
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(false);
        // Si estos colaboradores llegaran a invocarse, el test tiene que fallar:
        // se stubean para que revienten, sin usar Mockito.verify(never()).
        lenient().doThrow(new AssertionError("no deberia notificar al organizador"))
                .when(mockNotificationService).notifyOrganizerEventReminder(any());
        lenient().doThrow(new AssertionError("no deberia escribir nada en la bandeja"))
                .when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testDoesNothingForACancelledEvent() {
        // Arrange
        final Event event = eventInHours(10);
        event.setCancelledAt(new Date());
        when(mockEventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
        lenient().doThrow(new AssertionError("no deberia notificar al organizador"))
                .when(mockNotificationService).notifyOrganizerEventReminder(any());
        lenient().doThrow(new AssertionError("no deberia escribir nada en la bandeja"))
                .when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testFindEventsLooksAtTheNext24Hours() {
        // Arrange
        final Date[] capturedFrom = new Date[1];
        final Date[] capturedUntil = new Date[1];
        doAnswer(invocation -> {
            capturedFrom[0] = invocation.getArgument(0);
            capturedUntil[0] = invocation.getArgument(1);
            return Collections.emptyList();
        }).when(mockEventDao).findIdsNeedingReminder(any(Date.class), any(Date.class));
        // No deberia reclamar ningun recordatorio todavia, solo listar: se
        // stubea para que reviente si se llega a invocar.
        lenient().when(mockEventDao.markReminderSent(anyLong(), any(Date.class)))
                .thenThrow(new AssertionError("findEventsNeedingReminder no deberia reclamar recordatorios"));

        // Act
        reminderService.findEventsNeedingReminder();

        // Assert
        assertEquals(EventReminderServiceImpl.ONE_DAY_MILLIS, capturedUntil[0].getTime() - capturedFrom[0].getTime());
    }
}
