package ar.edu.itba.paw.services;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WaitlistSchedulerTest {

    @Mock
    private WaitlistService mockWaitlistService;

    @InjectMocks
    private WaitlistScheduler scheduler;

    @Test
    public void testReconcilesEveryEventThatNeedsIt() {
        when(mockWaitlistService.findEventsNeedingReconcile()).thenReturn(Arrays.asList(1L, 2L, 3L));

        scheduler.reconcileAll();

        verify(mockWaitlistService).reconcile(1L);
        verify(mockWaitlistService).reconcile(2L);
        verify(mockWaitlistService).reconcile(3L);
    }

    /**
     * Si un evento explota, los que vienen despues tienen que procesarse igual.
     * Sin el try/catch adentro del loop, un solo evento roto dejaria la lista de
     * espera de toda la aplicacion sin atender, en silencio.
     */
    @Test
    public void testOneBrokenEventDoesNotStopTheRest() {
        when(mockWaitlistService.findEventsNeedingReconcile()).thenReturn(Arrays.asList(1L, 2L, 3L));
        org.mockito.Mockito.doThrow(new RuntimeException("se rompio"))
                .when(mockWaitlistService).reconcile(2L);

        scheduler.reconcileAll();

        verify(mockWaitlistService).reconcile(3L);
    }

    @Test
    public void testDoesNothingWhenThereIsNobodyWaiting() {
        when(mockWaitlistService.findEventsNeedingReconcile()).thenReturn(Collections.emptyList());

        scheduler.reconcileAll();

        verify(mockWaitlistService, never()).reconcile(org.mockito.ArgumentMatchers.anyLong());
    }
}
