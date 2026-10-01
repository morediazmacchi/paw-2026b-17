package ar.edu.itba.paw.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventReminderSchedulerTest {

    @Mock
    private EventReminderService mockReminderService;

    @InjectMocks
    private EventReminderScheduler scheduler;

    // sendReminder() es void: se captura cada id con el que se lo llamo (con
    // doAnswer() sobre el stub, no con Mockito.verify()) para despues poder
    // comprobar con un assert comun a cuales se les mando.
    private List<Long> captureRemindedIds() {
        final List<Long> remindedIds = new ArrayList<>();
        doAnswer(invocation -> {
            remindedIds.add(invocation.getArgument(0));
            return null;
        }).when(mockReminderService).sendReminder(anyLong());
        return remindedIds;
    }

    @Test
    public void testSendsTheReminderOfEveryDueEvent() {
        // Arrange
        when(mockReminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L));
        final List<Long> remindedIds = captureRemindedIds();

        // Act
        scheduler.sendDueReminders();

        // Assert
        assertEquals(Arrays.asList(1L, 2L), remindedIds);
    }

    /** Un evento que falla no deja sin recordatorio a los que vienen despues. */
    @Test
    public void testOneBrokenEventDoesNotStopTheRest() {
        // Arrange
        when(mockReminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L, 3L));
        final List<Long> remindedIds = captureRemindedIds();
        // Mas especifico que el stub general de arriba: para el id 2, gana este.
        doThrow(new RuntimeException("se rompio")).when(mockReminderService).sendReminder(2L);

        // Act
        scheduler.sendDueReminders();

        // Assert
        assertTrue(remindedIds.contains(3L));
    }
}
