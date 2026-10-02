package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: EventDao y RegistrationService son @Mock y cada test
 * asierta sobre lo que devuelve el service (sin verify). Los avisos (inbox y mail) son
 * "disparar y olvidar" (void), asi que se siguen anotando con los fakes a mano
 * RecordingInboxService / RecordingNotificationService.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventReminderServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String CREATOR_EMAIL = "org@mail.com";

    @Mock
    private EventDao eventDao;
    @Mock
    private RegistrationService registrationService;

    private RecordingNotificationService notifications;
    private RecordingInboxService inbox;

    private EventReminderServiceImpl reminderService;

    @Before
    public void setUp() {
        notifications = new RecordingNotificationService();
        inbox = new RecordingInboxService();
        reminderService = new EventReminderServiceImpl(eventDao, registrationService, notifications, inbox);
    }

    private static Event eventInHours(final long id, final long hours) {
        final Event event = new Event();
        event.setId(id);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setEventDate(new Date(System.currentTimeMillis() + hours * 60 * 60 * 1000));
        return event;
    }

    private static Registration registration(final String email, final RegistrationStatus status) {
        return new Registration(EVENT_ID, email, "Nombre", status);
    }

    // El evento existe y es el que se reclama: el caso feliz de sendReminder().
    private void claimable(final Event event) {
        when(eventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(eventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
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

    @Test
    public void testSendReminderMailsOnlyTheConfirmedRegistrants() {
        // Arrange
        claimable(eventInHours(EVENT_ID, 10));
        final Registration confirmed = registration("conf@mail.com", RegistrationStatus.CONFIRMED);
        when(registrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmed,
                registration("espera@mail.com", RegistrationStatus.WAITLISTED),
                registration("pend@mail.com", RegistrationStatus.PENDING)));

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        final List<Object> reminded = new ArrayList<>();
        for (final Object[] args : notifications.argsOf("notifyEventReminder")) {
            reminded.add(args[1]);
        }
        assertEquals(Collections.singletonList(confirmed), reminded);
    }

    @Test
    public void testSendReminderTellsTheConfirmedRegistrantsInTheInbox() {
        // Arrange
        claimable(eventInHours(EVENT_ID, 10));
        when(registrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                registration("conf@mail.com", RegistrationStatus.CONFIRMED),
                registration("espera@mail.com", RegistrationStatus.WAITLISTED),
                registration("pend@mail.com", RegistrationStatus.PENDING)));

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertEquals(Collections.singletonList(InboxMessageType.EVENT_REMINDER),
                typesOf(inbox.to("conf@mail.com")));
        assertTrue(inbox.to("espera@mail.com").isEmpty());
        assertTrue(inbox.to("pend@mail.com").isEmpty());
    }

    @Test
    public void testSendReminderMailsTheOrganizer() {
        // Arrange
        claimable(eventInHours(EVENT_ID, 10));
        when(registrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertEquals(1, notifications.count("notifyOrganizerEventReminder"));
    }

    @Test
    public void testSendReminderTellsTheOrganizerInTheInboxThatTheEventIsTomorrow() {
        // Arrange
        claimable(eventInHours(EVENT_ID, 10));
        when(registrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertEquals(Collections.singletonList(InboxMessageType.EVENT_TOMORROW),
                typesOf(inbox.to(CREATOR_EMAIL)));
    }

    /** Si otra corrida ya lo reclamo, no se manda nada: nunca dos recordatorios. */
    @Test
    public void testSendReminderReturnsFalseWhenTheReminderWasAlreadySent() {
        // Arrange
        when(eventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(false);

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendReminderWhenAlreadySentDoesNotNotifyAnyone() {
        // Arrange
        when(eventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(false);

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertTrue(notifications.methods().isEmpty());
        assertTrue(inbox.all().isEmpty());
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
    public void testSendReminderForACancelledEventDoesNotNotifyAnyone() {
        // Arrange
        final Event event = eventInHours(EVENT_ID, 10);
        event.setCancelledAt(new Date());
        claimable(event);

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertTrue(notifications.methods().isEmpty());
        assertTrue(inbox.all().isEmpty());
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
    public void testSendReminderForAnEventThatAlreadyHappenedDoesNotNotifyAnyone() {
        // Arrange
        claimable(eventInHours(EVENT_ID, -1));

        // Act
        reminderService.sendReminder(EVENT_ID);

        // Assert
        assertTrue(notifications.methods().isEmpty());
        assertTrue(inbox.all().isEmpty());
    }

    @Test
    public void testSendReminderReturnsFalseWhenTheEventDoesNotExist() {
        // Arrange
        when(eventDao.markReminderSent(eq(EVENT_ID), any(Date.class))).thenReturn(true);
        when(eventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = reminderService.sendReminder(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    // ---- findEventsNeedingReminder() ----

    // Pide al dao los eventos que empiezan entre ahora y dentro de 24 hs.
    @Test
    public void testFindEventsNeedingReminderAsksForTheNext24Hours() {
        // Arrange
        final long before = System.currentTimeMillis();
        when(eventDao.findIdsNeedingReminder(
                argThat(from -> Math.abs(from.getTime() - before) < 5000),
                argThat(until -> Math.abs(until.getTime() - (before + EventReminderServiceImpl.ONE_DAY_MILLIS)) < 5000)))
                .thenReturn(Arrays.asList(1L, 2L));

        // Act
        final List<Long> result = reminderService.findEventsNeedingReminder();

        // Assert
        assertEquals(Arrays.asList(1L, 2L), result);
    }

    @Test
    public void testFindEventsNeedingReminderReturnsEmptyWhenNoEventIsDue() {
        // Arrange
        when(eventDao.findIdsNeedingReminder(any(Date.class), any(Date.class))).thenReturn(Collections.emptyList());

        // Act
        final List<Long> result = reminderService.findEventsNeedingReminder();

        // Assert
        assertTrue(result.isEmpty());
    }

    private static List<InboxMessageType> typesOf(final List<RecordingInboxService.Added> messages) {
        final List<InboxMessageType> types = new ArrayList<>();
        for (final RecordingInboxService.Added message : messages) {
            types.add(message.type);
        }
        return types;
    }
}
