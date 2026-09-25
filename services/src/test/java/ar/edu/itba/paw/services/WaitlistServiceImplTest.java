package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InvitationStatus;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WaitlistServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String CREATOR_EMAIL = "org@mail.com";
    private static final long DIA = 24L * 60 * 60 * 1000;

    @Mock
    private WaitlistInvitationDao mockInvitationDao;
    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private NotificationService mockNotificationService;

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private WaitlistServiceImpl waitlistService;

    private static Event event(final Integer maxCapacity) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setMaxCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        // Siempre en el futuro: un evento que ya paso no ofrece lugares.
        event.setEventDate(new Date(System.currentTimeMillis() + 30 * DIA));
        return event;
    }

    private static Registration waitlisted(final long id) {
        final Registration registration = new Registration(EVENT_ID, "a@a.com", "Ana", RegistrationStatus.WAITLISTED);
        registration.setId(id);
        return registration;
    }

    private static WaitlistInvitation pendingInvitation(final long id, final long registrationId, final Date expiresAt) {
        final WaitlistInvitation invitation = new WaitlistInvitation(registrationId, "tok" + id, expiresAt);
        invitation.setId(id);
        return invitation;
    }

    // "a@a.com" (la de waitlisted()) esta logueada y tiene esta invitacion.
    private void stubMyInvitation(final WaitlistInvitation invitation) {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, "a@a.com"))
                .thenReturn(Optional.of(waitlisted(invitation.getRegistrationId())));
        when(mockInvitationDao.findByRegistrationId(invitation.getRegistrationId())).thenReturn(Optional.of(invitation));
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
        stubMyInvitation(pendingInvitation(5, 88, manana));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));

        final InvitationResult result = waitlistService.accept(EVENT_ID, "a@a.com");

        assertEquals(InvitationResult.ACCEPTED, result);
        verify(mockInvitationDao).markAccepted(5);
        verify(mockRegistrationDao).updateStatus(88, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testAcceptAfterTheDeadlineDropsTheRegistrationInstead() {
        final Date ayer = new Date(System.currentTimeMillis() - DIA);
        stubMyInvitation(pendingInvitation(5, 88, ayer));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(10L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);

        final InvitationResult result = waitlistService.accept(EVENT_ID, "a@a.com");

        assertEquals(InvitationResult.EXPIRED, result);
        verify(mockRegistrationDao).delete(88);
        verify(mockInvitationDao, never()).markAccepted(anyLong());
    }

    @Test
    public void testAcceptTwiceIsNotAnError() {
        final WaitlistInvitation yaAceptada = pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA));
        yaAceptada.setStatus(InvitationStatus.ACCEPTED);
        stubMyInvitation(yaAceptada);

        assertEquals(InvitationResult.ALREADY_USED, waitlistService.accept(EVENT_ID, "a@a.com"));
    }

    @Test
    public void testAcceptFromAnotherAccountReturnsNotFound() {
        // Le reenviaron el mail y entra con su cuenta: no tiene inscripcion en el evento.
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, "otro@a.com")).thenReturn(Optional.empty());

        assertEquals(InvitationResult.NOT_FOUND, waitlistService.accept(EVENT_ID, "otro@a.com"));
        verify(mockInvitationDao, never()).markAccepted(anyLong());
    }

    // ---- decline() ----

    @Test
    public void testDeclineDropsTheRegistrationAndOffersTheSpotToTheNextOne() {
        stubMyInvitation(pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA)));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        final List<Registration> siguiente = Collections.singletonList(waitlisted(99));
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(siguiente);

        final InvitationResult result = waitlistService.decline(EVENT_ID, "a@a.com");

        assertEquals(InvitationResult.DECLINED, result);
        verify(mockRegistrationDao).delete(88);
        final ArgumentCaptor<WaitlistInvitation> created = ArgumentCaptor.forClass(WaitlistInvitation.class);
        verify(mockInvitationDao).create(created.capture());
        assertEquals(99, created.getValue().getRegistrationId());
    }

    // ---- avisos en la bandeja ----

    @Test
    public void testReconcileTellsTheInvitedPersonInTheInbox() {
        eventoConCupo(10);
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waitlisted(77)));

        waitlistService.reconcile(EVENT_ID);

        verify(mockInboxService).add("a@a.com", InboxMessageType.WAITLIST_SPOT_OFFERED, EVENT_ID, null, null);
    }

    @Test
    public void testAcceptLeavesItsOwnMessageForTheOrganizerAndNotANewRegistration() {
        stubMyInvitation(pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA)));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));

        waitlistService.accept(EVENT_ID, "a@a.com");

        verify(mockInboxService).add("a@a.com", InboxMessageType.REGISTERED_CONFIRMED, EVENT_ID, null, null);
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.WAITLIST_ACCEPTED, EVENT_ID, "Ana", null);
        verify(mockInboxService, never()).add(any(), eq(InboxMessageType.NEW_REGISTRATION), any(), any(), any());
    }

    @Test
    public void testDeclineTellsTheOrganizerInTheInbox() {
        stubMyInvitation(pendingInvitation(5, 88, new Date(System.currentTimeMillis() + DIA)));
        when(mockRegistrationDao.findById(88)).thenReturn(Optional.of(waitlisted(88)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event(10)));
        when(mockInvitationDao.findExpired(anyLong(), any(Date.class))).thenReturn(Collections.emptyList());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L);
        when(mockInvitationDao.countPending(EVENT_ID)).thenReturn(0L);

        waitlistService.decline(EVENT_ID, "a@a.com");

        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.WAITLIST_DECLINED, EVENT_ID, "Ana", null);
    }
}
