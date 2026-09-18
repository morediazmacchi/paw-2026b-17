package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InvitationStatus;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.WaitlistInvitation;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import ar.edu.itba.paw.persistence.WaitlistInvitationDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WaitlistServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long DIA = 24L * 60 * 60 * 1000;

    @Mock
    private WaitlistInvitationDao mockInvitationDao;
    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private NotificationService mockNotificationService;

    @InjectMocks
    private WaitlistServiceImpl waitlistService;

    private static Event event(final Integer maxCapacity) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setMaxCapacity(maxCapacity);
        // Siempre en el futuro: un evento que ya paso no ofrece lugares.
        event.setEventDate(new Date(System.currentTimeMillis() + 30 * DIA));
        return event;
    }

    private static Registration waitlisted(final long id) {
        final Registration registration = new Registration(EVENT_ID, "a@a.com", "Ana", Registration.STATUS_WAITLISTED);
        registration.setId(id);
        return registration;
    }

    private static WaitlistInvitation pendingInvitation(final long id, final long registrationId, final Date expiresAt) {
        final WaitlistInvitation invitation = new WaitlistInvitation(registrationId, "tok" + id, expiresAt);
        invitation.setId(id);
        return invitation;
    }

    private void eventoConCupo(final Integer capacity) {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(capacity)));
    }

    // ---- reconcile() ----

    @Test
    public void testReconcileInvitesTheFirstOneWaitingWhenASpotIsFree() {
        eventoConCupo(10);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waitlisted(77)));

        waitlistService.reconcile(EVENT_ID);

        final ArgumentCaptor<WaitlistInvitation> created = ArgumentCaptor.forClass(WaitlistInvitation.class);
        verify(mockInvitationDao).create(created.capture());
        assertEquals(77, created.getValue().getRegistrationId());
        assertEquals(InvitationStatus.PENDING, created.getValue().getStatus());
    }

    /**
     * EL TEST QUE IMPORTA. Con 10 de cupo, 9 confirmados y 1 invitacion sin
     * contestar, NO queda ningun lugar: ese cupo esta reservado. Si este test
     * falla, la app sobrevende.
     */
    @Test
    public void testReconcileDoesNotInviteWhenAPendingInvitationAlreadyHoldsTheSpot() {
        eventoConCupo(10);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(1L);

        waitlistService.reconcile(EVENT_ID);

        verify(mockInvitationDao, never()).create(any(WaitlistInvitation.class));
        verify(mockRegistrationDao, never()).findNextWaitlisted(anyLong(), anyInt());
    }

    @Test
    public void testReconcileInvitesAsManyPeopleAsSpotsWereFreed() {
        eventoConCupo(10);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(7L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 3))
                .thenReturn(Arrays.asList(waitlisted(1), waitlisted(2)));

        waitlistService.reconcile(EVENT_ID);

        // Se liberaron 3 pero solo habia 2 esperando: se invita a las 2 y el
        // tercer cupo queda abierto al publico. No hay nada mas que hacer.
        verify(mockInvitationDao, org.mockito.Mockito.times(2)).create(any(WaitlistInvitation.class));
    }

    @Test
    public void testReconcileDropsTheRegistrationOfAnExpiredInvitation() {
        eventoConCupo(10);
        final Date ayer = new Date(System.currentTimeMillis() - DIA);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class)))
                .thenReturn(Collections.singletonList(pendingInvitation(5, 88, ayer)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(10L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);

        waitlistService.reconcile(EVENT_ID);

        // Quien no contesto a tiempo sale de la lista: se borra su inscripcion
        // (y la invitacion se va con ella por el ON DELETE CASCADE).
        verify(mockRegistrationDao).delete(88);
    }

    @Test
    public void testReconcileDoesNothingWhenTheEventHasNoCapacityLimit() {
        eventoConCupo(null);

        waitlistService.reconcile(EVENT_ID);

        verify(mockInvitationDao, never()).create(any(WaitlistInvitation.class));
    }

    @Test
    public void testReconcileGivesTheInvitationAtMostTwentyFourHours() {
        eventoConCupo(10);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waitlisted(77)));

        final long antes = System.currentTimeMillis();
        waitlistService.reconcile(EVENT_ID);

        final ArgumentCaptor<WaitlistInvitation> created = ArgumentCaptor.forClass(WaitlistInvitation.class);
        verify(mockInvitationDao).create(created.capture());
        final long vence = created.getValue().getExpiresAt().getTime();
        assertTrue(vence >= antes + DIA - 5000);
        assertTrue(vence <= System.currentTimeMillis() + DIA + 5000);
    }

    // ---- accept() ----

    @Test
    public void testAcceptConfirmsTheRegistration() {
        final Date manana = new Date(System.currentTimeMillis() + DIA);
        when(mockInvitationDao.findByToken("tok")).thenReturn(Optional.of(pendingInvitation(5, 88, manana)));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));

        final InvitationResult result = waitlistService.accept("tok");

        assertEquals(InvitationResult.ACCEPTED, result);
        verify(mockInvitationDao).markAccepted(5);
        verify(mockRegistrationDao).updateStatus(88, Registration.STATUS_CONFIRMED);
    }

    @Test
    public void testAcceptAfterTheDeadlineDropsTheRegistrationInstead() {
        final Date ayer = new Date(System.currentTimeMillis() - DIA);
        when(mockInvitationDao.findByToken("tok")).thenReturn(Optional.of(pendingInvitation(5, 88, ayer)));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(10L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);

        final InvitationResult result = waitlistService.accept("tok");

        assertEquals(InvitationResult.EXPIRED, result);
        verify(mockRegistrationDao).delete(88);
        verify(mockInvitationDao, never()).markAccepted(anyLong());
    }

    @Test
    public void testAcceptTwiceIsNotAnError() {
        final WaitlistInvitation yaAceptada = pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA));
        yaAceptada.setStatus(InvitationStatus.ACCEPTED);
        when(mockInvitationDao.findByToken("tok")).thenReturn(Optional.of(yaAceptada));

        assertEquals(InvitationResult.ALREADY_USED, waitlistService.accept("tok"));
    }

    @Test
    public void testAcceptWithAnUnknownTokenReturnsNotFound() {
        when(mockInvitationDao.findByToken("cualquiera")).thenReturn(Optional.empty());

        assertEquals(InvitationResult.NOT_FOUND, waitlistService.accept("cualquiera"));
    }

    // ---- decline() ----

    @Test
    public void testDeclineDropsTheRegistrationAndOffersTheSpotToTheNextOne() {
        when(mockInvitationDao.findByToken("tok"))
                .thenReturn(Optional.of(pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA))));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        final List<Registration> siguiente = Collections.singletonList(waitlisted(99));
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(siguiente);

        final InvitationResult result = waitlistService.decline("tok");

        assertEquals(InvitationResult.DECLINED, result);
        verify(mockRegistrationDao).delete(88);
        final ArgumentCaptor<WaitlistInvitation> created = ArgumentCaptor.forClass(WaitlistInvitation.class);
        verify(mockInvitationDao).create(created.capture());
        assertEquals(99, created.getValue().getRegistrationId());
    }
}
