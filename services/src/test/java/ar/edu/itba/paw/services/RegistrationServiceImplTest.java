package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegistrationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";
    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private UserService mockUserService;
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;
    // Idem: RegistrationServiceImpl le pregunta cuantos cupos estan reservados
    // por invitaciones sin contestar. Sin stubear, un nice mock devuelve 0,
    // que es justo el caso "no hay ninguna invitacion dando vueltas".
    @Mock
    private WaitlistService mockWaitlistService;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private static Event eventWithCapacity(final Integer maxCapacity) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setMaxCapacity(maxCapacity);
        return event;
    }

    private static Event eventRequiringApproval(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setRequiresApproval(true);
        return event;
    }

    // ---- register() ----

    @Test
    public void testRegisterReturnsAlreadyRegisteredWhenTheEmailIsAlreadyInThatEvent() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.ALREADY_REGISTERED, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterWithNonexistentEventThrowsIllegalArgumentException() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        registrationService.register(EVENT_ID, EMAIL, NAME);
    }

    @Test
    public void testRegisterWhenTheEventIsFullReturnsWaitlisted() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.WAITLISTED, result);
    }

    @Test
    public void testRegisterWhenTheEventIsFullSavesTheRegistrationAsWaitlisted() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        registrationService.register(EVENT_ID, EMAIL, NAME);

        // Lo que se guarda es parte del contrato: una fila en estado waitlisted
        // es lo que despues deja armar la lista de espera en orden.
        final ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
        verify(mockRegistrationDao).create(saved.capture());
        assertEquals(Registration.STATUS_WAITLISTED, saved.getValue().getStatus());
    }

    @Test
    public void testRegisterWhenAlreadyOnTheWaitlistReturnsAlreadyWaitlisted() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_WAITLISTED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.ALREADY_WAITLISTED, result);
    }

    @Test
    public void testRegisterWithFreeSpotReturnsConfirmed() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    @Test
    public void testRegisterWithUnlimitedCapacityNeverThrowsEvenIfManyAreConfirmed() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    // ---- register() con requiresApproval ----

    @Test
    public void testRegisterOnEventRequiringApprovalReturnsPending() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.PENDING, result);
    }

    @Test
    public void testRegisterOnEventRequiringApprovalSavesTheRegistrationAsPending() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        registrationService.register(EVENT_ID, EMAIL, NAME);

        final ArgumentCaptor<Registration> saved = ArgumentCaptor.forClass(Registration.class);
        verify(mockRegistrationDao).create(saved.capture());
        assertEquals(Registration.STATUS_PENDING, saved.getValue().getStatus());
    }

    // Sin cupo, la lista de espera gana: no tiene sentido "esperando revision"
    // para un lugar que ni siquiera existe.
    @Test
    public void testRegisterOnFullEventRequiringApprovalReturnsWaitlistedNotPending() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(1)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        assertEquals(RegistrationResult.WAITLISTED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    @Test
    public void testRegisterWhenAlreadyPendingReturnsAlreadyPending() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        assertEquals(RegistrationResult.ALREADY_PENDING, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    @Test
    public void testRegisterWhenPreviouslyRejectedReturnsRejected() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_REJECTED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        assertEquals(RegistrationResult.REJECTED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    /**
     * Mismo espiritu que testIsFullCountsSpotsHeldByPendingInvitations: una
     * inscripcion pendiente de revision tambien reserva su lugar, si no
     * alguien de la calle se lo llevaria mientras el creador todavia esta
     * decidiendo.
     */
    @Test
    public void testIsFullCountsSpotsHeldByPendingApprovals() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);

        assertTrue(registrationService.isFull(EVENT_ID));
    }

    // ---- isFull() ----

    @Test
    public void testIsFullReturnsTrueWhenConfirmedCountReachesCapacity() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        final boolean result = registrationService.isFull(EVENT_ID);

        assertTrue(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenThereIsStillRoom() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        final boolean result = registrationService.isFull(EVENT_ID);

        assertFalse(result);
    }

    /**
     * EL TEST QUE IMPIDE SOBREVENDER. Con cupo 2, 1 confirmado y 1 invitacion de
     * lista de espera sin contestar, el evento esta LLENO: ese segundo lugar esta
     * reservado mientras la persona invitada decide. Si esto devolviera false,
     * alguien de la calle se quedaria con el lugar que le estamos ofreciendo.
     */
    @Test
    public void testIsFullCountsSpotsHeldByPendingInvitations() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockWaitlistService.countReserved(EVENT_ID)).thenReturn(1L);

        assertTrue(registrationService.isFull(EVENT_ID));
    }

    @Test
    public void testAvailableSpotsDiscountsSpotsHeldByPendingInvitations() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(7L);
        when(mockWaitlistService.countReserved(EVENT_ID)).thenReturn(2L);

        // 10 - 7 ocupados - 2 reservados = 1. Es el numero que ve la gente en
        // pantalla: si mostrara 3, tres personas creerian que hay lugar.
        assertEquals(Integer.valueOf(1), registrationService.availableSpots(EVENT_ID));
    }

    @Test
    public void testRegisterGoesToTheWaitlistWhenThelastSpotIsHeldByAnInvitation() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockWaitlistService.countReserved(EVENT_ID)).thenReturn(1L);

        assertEquals(RegistrationResult.WAITLISTED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    @Test
    public void testIsFullReturnsFalseWhenCapacityIsUnlimited() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        final boolean result = registrationService.isFull(EVENT_ID);

        assertFalse(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsFullWithNonexistentEventThrowsIllegalArgumentException() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        registrationService.isFull(EVENT_ID);
    }

    // ---- findByEmail() ----

    @Test
    public void testFindByEmailDelegatesToTheDao() {
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED));
        when(mockRegistrationDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEmail(NORMALIZED_EMAIL);

        assertEquals(registrations, result);
    }

    // ---- cancel() ----

    @Test
    public void testCancelDeletesWhenTheEmailOwnsTheRegistration() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).delete(5);
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationDoesNotExist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).delete(5);
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, "otro@mail.com", NAME, Registration.STATUS_CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).delete(5);
    }

    // ---- findByEventAndEmail() ----

    @Test
    public void testFindByEventAndEmailNormalizesTheEmail() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(registration));

        final Optional<Registration> result = registrationService.findByEventAndEmail(EVENT_ID, EMAIL);

        assertTrue(result.isPresent());
        assertEquals(NORMALIZED_EMAIL, result.get().getEmail());
    }

    @Test
    public void testFindByEventAndEmailReturnsEmptyWhenNotRegistered() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        final Optional<Registration> result = registrationService.findByEventAndEmail(EVENT_ID, EMAIL);

        assertFalse(result.isPresent());
    }

    // ---- findByEventId() ----

    @Test
    public void testFindByEventIdDelegatesToTheDao() {
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED));
        when(mockRegistrationDao.findByEventId(EVENT_ID)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEventId(EVENT_ID);

        assertEquals(registrations, result);
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        final List<Registration> pending = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING));
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID)).thenReturn(pending);

        final List<Registration> result = registrationService.findPendingByEvent(EVENT_ID, CREATOR_EMAIL);

        assertEquals(pending, result);
    }

    @Test
    public void testFindPendingByEventReturnsEmptyWhenAskedBySomeoneElse() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final List<Registration> result = registrationService.findPendingByEvent(EVENT_ID, "otro@mail.com");

        assertTrue(result.isEmpty());
        verify(mockRegistrationDao, never()).findPendingByEvent(EVENT_ID);
    }

    @Test
    public void testFindPendingByEventReturnsEmptyWhenTheEventDoesNotExist() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertTrue(registrationService.findPendingByEvent(EVENT_ID, CREATOR_EMAIL).isEmpty());
    }

    // ---- approve() ----

    @Test
    public void testApproveConfirmsAPendingRegistrationOwnedByTheCreator() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.approve(5, CREATOR_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).updateStatus(5, Registration.STATUS_CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.approve(5, "otro@mail.com");

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, Registration.STATUS_CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheRegistrationIsNotPending() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.approve(5, CREATOR_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, Registration.STATUS_CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheRegistrationDoesNotExist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        assertFalse(registrationService.approve(5, CREATOR_EMAIL));
    }

    // ---- reject() ----

    @Test
    public void testRejectMarksAPendingRegistrationOwnedByTheCreatorAsRejected() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.reject(5, CREATOR_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).updateStatus(5, Registration.STATUS_REJECTED);
    }

    // El lugar que la inscripcion rechazada tenia reservado se libera: hay que
    // ofrecerselo enseguida a quien siga en la lista de espera.
    @Test
    public void testRejectReconcilesTheWaitlistToOfferTheFreedSpot() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        registrationService.reject(5, CREATOR_EMAIL);

        verify(mockWaitlistService).reconcile(EVENT_ID);
    }

    @Test
    public void testRejectReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.reject(5, "otro@mail.com");

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, Registration.STATUS_REJECTED);
    }

    @Test
    public void testRejectReturnsFalseWhenTheRegistrationIsNotPending() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_WAITLISTED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        assertFalse(registrationService.reject(5, CREATOR_EMAIL));
    }
}
