package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: todas las dependencias son @Mock y cada test
 * asierta sobre lo que devuelve el service (sin verify). Los avisos (inbox y
 * mail) son "disparar y olvidar" (void) y no se testean aca: verificar que se
 * llamaron seria testear la implementacion de EventReminderServiceImpl, no el
 * contrato de EventReminderService.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventReminderServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String CREATOR_EMAIL = "org@mail.com";

    @Mock
    private EventService eventService;
    @Mock
    private MassNotifier massNotifier;
    @Mock
    private NotificationService notificationService;
    @Mock
    private InboxService inboxService;

    private EventReminderServiceImpl reminderService;

    @Before
    public void setUp() {
        reminderService = new EventReminderServiceImpl(eventService, massNotifier, notificationService, inboxService);
    }

    private static Event eventInHours(final long id, final long hours) {
        final Event event = new Event();
        event.setId(id);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        // El resto de estos tests son sobre la ventana de tiempo/cancelacion, no sobre
        // la revision previa (ver EventApprovalStatus): se asume ya aprobado, como
        // cualquier evento publicado antes de que existiera esta feature. El caso
        // pendiente tiene su propio test, mas abajo.
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        return event;
    }

    // El evento existe y es el que se reclama: el caso feliz de sendReminder().
    private void claimable(final Event event) {
        when(eventService.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    // ---- sendReminder() ----

    @Test
    public void testSendReminderReturnsTrueWhenItSendsTheReminder() {
        // Arrange
        claimable(eventInHours(EVENT_ID, 10));

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertTrue(result);
    }

    /** Si otra corrida ya lo reclamo, no se manda nada: nunca dos recordatorios. */
    @Test
    public void testSendReminderReturnsFalseWhenTheReminderWasAlreadySent() {
        // Arrange
        when(eventService.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(false);

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderReturnsFalseForACancelledEvent() {
        // Arrange
        final Event event = eventInHours(EVENT_ID, 10);
        event.setCancelledAt(new Date());
        claimable(event);

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderReturnsFalseForAPendingApprovalEvent() {
        // Arrange
        final Event event = eventInHours(EVENT_ID, 10);
        event.setApprovalStatus(EventApprovalStatus.PENDING);
        claimable(event);

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderReturnsFalseForARejectedEvent() {
        // Arrange
        final Event event = eventInHours(EVENT_ID, 10);
        event.setApprovalStatus(EventApprovalStatus.REJECTED);
        claimable(event);

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderReturnsFalseForAnEventThatAlreadyHappened() {
        // Arrange
        claimable(eventInHours(EVENT_ID, -1));

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderReturnsFalseWhenTheEventDoesNotExist() {
        // Arrange
        when(eventService.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    // ---- findEventsNeedingReminder() ----

    // Pide al dao los eventos que empiezan entre ahora y dentro de 24 hs.
    @Test
    public void testFindEventsNeedingReminderReturnsEmptyWhenNoEventIsDue() {
        // Arrange
        when(eventService.findIdsNeedingReminder(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());

        // Act
        final List<Long> result = reminderService.findEventsNeedingReminder();

        // Assert
        assertTrue(result.isEmpty());
    }
}
