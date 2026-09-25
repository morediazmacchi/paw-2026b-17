package ar.edu.itba.paw.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventReminderSchedulerTest {

    @Mock
    private EventReminderService mockReminderService;

    @InjectMocks
    private EventReminderScheduler scheduler;

    @Test
    public void testSendsTheReminderOfEveryDueEvent() {
        when(mockReminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L));

        scheduler.sendDueReminders();

        verify(mockReminderService).sendReminder(1L);
        verify(mockReminderService).sendReminder(2L);
    }

    /** Un evento que falla no deja sin recordatorio a los que vienen despues. */
    @Test
    public void testOneBrokenEventDoesNotStopTheRest() {
        when(mockReminderService.findEventsNeedingReminder()).thenReturn(Arrays.asList(1L, 2L, 3L));
        doThrow(new RuntimeException("se rompio")).when(mockReminderService).sendReminder(2L);

        scheduler.sendDueReminders();

        verify(mockReminderService).sendReminder(3L);
    }
}
