package ar.edu.itba.paw.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: el EventReminderService es un @Mock. sendDueReminders()
 * devuelve cuantos recordatorios salieron, y los tests asertan sobre eso.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventReminderSchedulerTest {

    @Mock
    private EventReminderService reminderService;

    private EventReminderScheduler scheduler;

    @Before
    public void setUp() {
        scheduler = new EventReminderScheduler(reminderService);
    }

    @Test
    public void testSendsTheReminderOfEveryDueEvent() {
        // Arrange
        when(reminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L));
        when(reminderService.sendReminder(1L)).thenReturn(true);
        when(reminderService.sendReminder(2L)).thenReturn(true);

        // Act
        final int result = scheduler.sendDueReminders();

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testSendsNothingWhenNoEventIsDue() {
        // Arrange
        when(reminderService.findEventsNeedingReminder()).thenReturn(Collections.emptyList());

        // Act
        final int result = scheduler.sendDueReminders();

        // Assert
        assertEquals(0, result);
    }

    @Test
    public void testDoesNotCountTheEventsWhoseReminderWasNotSent() {
        // Arrange
        when(reminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L));
        when(reminderService.sendReminder(1L)).thenReturn(true);
        when(reminderService.sendReminder(2L)).thenReturn(false);

        // Act
        final int result = scheduler.sendDueReminders();

        // Assert
        assertEquals(1, result);
    }

    /** Un evento que falla no deja sin recordatorio a los que vienen despues. */
    @Test
    public void testOneBrokenEventDoesNotStopTheRest() {
        // Arrange
        when(reminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L, 3L));
        when(reminderService.sendReminder(1L)).thenReturn(true);
        when(reminderService.sendReminder(2L)).thenThrow(new RuntimeException("se rompio"));
        when(reminderService.sendReminder(3L)).thenReturn(true);

        // Act
        final int result = scheduler.sendDueReminders();

        // Assert
        assertEquals(2, result);
    }
}
