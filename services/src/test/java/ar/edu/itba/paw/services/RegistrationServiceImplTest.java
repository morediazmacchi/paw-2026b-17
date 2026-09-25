package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
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

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

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
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
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
        assertEquals(RegistrationStatus.WAITLISTED, saved.getValue().getStatus());
    }

    @Test
    public void testRegisterWhenAlreadyOnTheWaitlistReturnsAlreadyWaitlisted() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
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

    // ---- register(): el creador no se anota a su propio evento ----

    @Test
    public void testRegisterReturnsOwnEventWhenTheCreatorTriesToRegister() {
        final Event ownEvent = eventWithCapacity(10);
        // Distinto casing a proposito: el chequeo tiene que ser insensible a
        // mayusculas/minusculas, igual que el resto de las comparaciones de
        // email en este service.
        ownEvent.setCreatorEmail("TEST@MAIL.COM");
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(ownEvent));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.OWN_EVENT, result);
    }

    @Test
    public void testRegisterDoesNotCreateOrNotifyWhenTheCreatorTriesToRegister() {
        final Event ownEvent = eventWithCapacity(10);
        ownEvent.setCreatorEmail(NORMALIZED_EMAIL);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(ownEvent));

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockRegistrationDao, never()).create(any());
        verify(mockNotificationService, never()).notifyRegistrationConfirmed(any(), any());
        verify(mockNotificationService, never()).notifyWaitlistJoined(any(), any());
        verify(mockNotificationService, never()).notifyRegistrationPending(any(), any());
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
        assertEquals(RegistrationStatus.PENDING, saved.getValue().getStatus());
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
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        assertEquals(RegistrationResult.ALREADY_PENDING, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    @Test
    public void testRegisterWhenPreviouslyRejectedReturnsRejected() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
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

    // ---- aviso "tu evento se lleno" ----

    @Test
    public void testRegisterNotifiesTheOrganizerWhenItTakesTheLastSpot() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        // Antes del insert habia 9 confirmados (quedaba 1 lugar); despues, 10.
        // Los dos valores en orden: es exactamente el cruce del limite.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L, 10L);

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockNotificationService).notifyEventFull(any(Event.class));
    }

    @Test
    public void testRegisterDoesNotNotifyTheOrganizerWhenSpotsRemain() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L, 6L);

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockNotificationService, never()).notifyEventFull(any(Event.class));
    }

    @Test
    public void testRegisterDoesNotNotifyAgainWhenTheEventWasAlreadyFull() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(10L);

        // Esta persona queda en lista de espera. El evento ya estaba lleno antes,
        // no hay ningun limite que cruzar: sin este chequeo, el organizador
        // recibiria un mail por cada persona que se sume a la lista.
        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockNotificationService, never()).notifyEventFull(any(Event.class));
    }

    // ---- cancel(): aviso de baja al organizador ----

    private static final long REGISTRATION_ID = 7;

    private static Registration registrationWith(final RegistrationStatus status) {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, status);
        registration.setId(REGISTRATION_ID);
        return registration;
    }

    @Test
    public void testCancelTellsTheOrganizerThatTheSpotIsOpenWhenNobodyWasWaiting() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockWaitlistService.reconcile(EVENT_ID)).thenReturn(0);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        // reconcile no invito a nadie -> el lugar quedo abierto al publico.
        verify(mockNotificationService)
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), eq(false));
    }

    @Test
    public void testCancelTellsTheOrganizerWhenTheSpotWentToTheWaitlist() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockWaitlistService.reconcile(EVENT_ID)).thenReturn(1);
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        // El dato sale de lo que devolvio reconcile, no de otra consulta.
        verify(mockNotificationService)
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), eq(true));
    }

    @Test
    public void testCancelDoesNotNotifyWhenSomeoneLeavesTheWaitlist() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockWaitlistService.reconcile(EVENT_ID)).thenReturn(0);

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        // Para el organizador no cambio nada: el cupo disponible es el mismo.
        verify(mockNotificationService, never())
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), anyBoolean());
    }

    // ---- avisos en la bandeja ----

    private static Event eventWithCreator(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        return event;
    }

    @Test
    public void testRegisterWithFreeSpotLeavesAMessageForThePersonAndForTheOrganizer() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockInboxService).add(NORMALIZED_EMAIL, InboxMessageType.REGISTERED_CONFIRMED, EVENT_ID, null, null);
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.NEW_REGISTRATION, EVENT_ID, NAME, null);
    }

    @Test
    public void testRegisterOnAFullEventOnlyTellsThePersonTheyAreWaitlisted() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockInboxService).add(NORMALIZED_EMAIL, InboxMessageType.REGISTERED_WAITLISTED, EVENT_ID, null, null);
        verify(mockInboxService, never()).add(eq(CREATOR_EMAIL), any(), any(), any(), any());
    }

    @Test
    public void testRegisterOnAnEventRequiringApprovalAsksTheOrganizerToAnswer() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));

        registrationService.register(EVENT_ID, EMAIL, NAME);

        verify(mockInboxService).add(NORMALIZED_EMAIL, InboxMessageType.REGISTERED_PENDING, EVENT_ID, null, null);
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.NEW_PENDING, EVENT_ID, NAME, null);
    }

    @Test
    public void testCancelOfAConfirmedRegistrationTellsTheOrganizerInTheInbox() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.REGISTRATION_CANCELLED, EVENT_ID, NAME, null);
    }

    @Test
    public void testCancelOfAPendingRequestTellsTheOrganizerInTheInboxButSendsNoMail() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.PENDING)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.REGISTRATION_CANCELLED, EVENT_ID, NAME, null);
        verify(mockNotificationService, never())
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), anyBoolean());
    }

    @Test
    public void testCancelFromTheWaitlistDoesNotTellTheOrganizer() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        verify(mockInboxService, never()).add(any(), any(), any(), any(), any());
    }

    @Test
    public void testApproveAndRejectTellThePerson() {
        final Registration aprobada = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        aprobada.setId(5);
        final Registration rechazada = new Registration(EVENT_ID, "otra@mail.com", "Otra", RegistrationStatus.PENDING);
        rechazada.setId(6);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(aprobada));
        when(mockRegistrationDao.findById(6)).thenReturn(Optional.of(rechazada));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        registrationService.approve(5, CREATOR_EMAIL);
        registrationService.reject(6, CREATOR_EMAIL);

        verify(mockInboxService).add(NORMALIZED_EMAIL, InboxMessageType.REQUEST_APPROVED, EVENT_ID, null, null);
        verify(mockInboxService).add("otra@mail.com", InboxMessageType.REQUEST_REJECTED, EVENT_ID, null, null);
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
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEmail(NORMALIZED_EMAIL);

        assertEquals(registrations, result);
    }

    // ---- cancel() ----

    @Test
    public void testCancelDeletesWhenTheEmailOwnsTheRegistration() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
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
        final Registration registration = new Registration(EVENT_ID, "otro@mail.com", NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).delete(5);
    }

    // ---- findByEventAndEmail() ----

    @Test
    public void testFindByEventAndEmailNormalizesTheEmail() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
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
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.findByEventId(EVENT_ID)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEventId(EVENT_ID);

        assertEquals(registrations, result);
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        final List<Registration> pending = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING));
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
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.approve(5, CREATOR_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.approve(5, "otro@mail.com");

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheRegistrationIsNotPending() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.approve(5, CREATOR_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsFalseWhenTheRegistrationDoesNotExist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        assertFalse(registrationService.approve(5, CREATOR_EMAIL));
    }

    // ---- reject() ----

    @Test
    public void testRejectMarksAPendingRegistrationOwnedByTheCreatorAsRejected() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.reject(5, CREATOR_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).updateStatus(5, RegistrationStatus.REJECTED);
    }

    // El lugar que la inscripcion rechazada tenia reservado se libera: hay que
    // ofrecerselo enseguida a quien siga en la lista de espera.
    @Test
    public void testRejectReconcilesTheWaitlistToOfferTheFreedSpot() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        registrationService.reject(5, CREATOR_EMAIL);

        verify(mockWaitlistService).reconcile(EVENT_ID);
    }

    @Test
    public void testRejectReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.reject(5, "otro@mail.com");

        assertFalse(result);
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.REJECTED);
    }

    @Test
    public void testRejectReturnsFalseWhenTheRegistrationIsNotPending() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        assertFalse(registrationService.reject(5, CREATOR_EMAIL));
    }
}
