package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    // Idem: RegistrationServiceImpl le pregunta cuantas invitaciones por mail a un
    // evento privado siguen sin usar (cada una reserva un lugar) y si quien se
    // anota tiene una. Sin stubear, un mock devuelve 0 / vacio: "nadie invitado".
    @Mock
    private EventInvitationDao mockInvitationDao;

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
     * El criterio de cupo: solo cuentan los confirmados (y las invitaciones a un
     * evento privado). Los pedidos pendientes de aprobacion NO ocupan lugar: el
     * organizador los mira a todos y acepta hasta que se llene.
     */
    @Test
    public void testIsFullDoesNotCountPendingApprovals() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        assertFalse(registrationService.isFull(EVENT_ID));
        verify(mockRegistrationDao, never()).countPending(EVENT_ID);
    }

    // Con la mitad de los lugares confirmados, seguir aceptando pedidos pendientes
    // (aunque haya mas pedidos que lugares) esta permitido: nacen PENDING.
    @Test
    public void testRegisterOnApprovalEventWithMorePendingThanSpotsStillCreatesPending() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        assertEquals(RegistrationResult.PENDING, registrationService.register(EVENT_ID, EMAIL, NAME));
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
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        // Nadie estaba esperando -> el lugar quedo abierto al publico.
        verify(mockNotificationService)
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), eq(false));
    }

    @Test
    public void testCancelTellsTheOrganizerWhenTheSpotWentToTheWaitlist() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration waiting = registrationWith(RegistrationStatus.WAITLISTED);
        waiting.setId(99);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 10)).thenReturn(Collections.singletonList(waiting));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        // El dato sale de a cuantos promovio la lista de espera, no de otra consulta.
        verify(mockNotificationService)
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), eq(true));
    }

    @Test
    public void testCancelDoesNotNotifyWhenSomeoneLeavesTheWaitlist() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

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
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.REGISTRATION_CANCELLED, EVENT_ID, NAME, null);
    }

    @Test
    public void testCancelOfAPendingRequestTellsTheOrganizerInTheInboxButSendsNoMail() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.PENDING)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.cancel(REGISTRATION_ID, NORMALIZED_EMAIL);

        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.REGISTRATION_CANCELLED, EVENT_ID, NAME, null);
        verify(mockNotificationService, never())
                .notifyRegistrationCancelled(any(Event.class), any(Registration.class), anyBoolean());
    }

    @Test
    public void testCancelFromTheWaitlistDoesNotTellTheOrganizer() {
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

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
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

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
     * EL TEST QUE IMPIDE SOBREVENDER. Con cupo 2, 1 confirmado y 1 invitacion a un
     * evento privado sin usar, el evento esta LLENO: cada invitacion reserva un
     * lugar. Si esto devolviera false, alguien de la calle se quedaria con el
     * lugar que le prometimos a quien invitamos.
     */
    @Test
    public void testIsFullCountsSpotsHeldByUnusedInvitations() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        assertTrue(registrationService.isFull(EVENT_ID));
    }

    @Test
    public void testAvailableSpotsDiscountsSpotsHeldByUnusedInvitations() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(7L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        // 10 - 7 confirmados - 2 invitaciones = 1. Es el numero que ve la gente en
        // pantalla: si mostrara 3, tres personas creerian que hay lugar.
        assertEquals(Integer.valueOf(1), registrationService.availableSpots(EVENT_ID));
    }

    @Test
    public void testRegisterGoesToTheWaitlistWhenTheLastSpotIsHeldByAnInvitation() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        assertEquals(RegistrationResult.WAITLISTED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    // La persona invitada usa el lugar que ya tenia reservado: para ella el evento
    // no esta lleno, aunque confirmados + invitaciones lleguen al cupo justo.
    @Test
    public void testRegisterWithAnInvitationTakesItsReservedSpotAndIsConfirmed() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, "tok", CREATOR_EMAIL)));

        assertEquals(RegistrationResult.CONFIRMED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    // Quien fue invitado ya fue elegido por el organizador: no vuelve a pedirle aprobacion.
    @Test
    public void testRegisterWithAnInvitationSkipsTheApprovalStep() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, "tok", CREATOR_EMAIL)));

        assertEquals(RegistrationResult.CONFIRMED, registrationService.register(EVENT_ID, EMAIL, NAME));
    }

    @Test
    public void testIsFullForAnInvitedPersonIgnoresTheirOwnReservedSpot() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, "tok", CREATOR_EMAIL)));

        assertFalse(registrationService.isFullFor(EVENT_ID, EMAIL));
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
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).delete(5);
    }

    // Un rechazado / revocado no puede "darse de baja": si borrara su fila se saltearia
    // el rechazo (y podria volver a anotarse).
    @Test
    public void testCancelReturnsFalseAndKeepsTheRowWhenTheRegistrationWasRejected() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        assertFalse(registrationService.cancel(5, NORMALIZED_EMAIL));
        verify(mockRegistrationDao, never()).delete(anyLong());
    }

    // Orden de locks: el evento se bloquea ANTES de tocar (borrar) la inscripcion.
    @Test
    public void testCancelLocksTheEventBeforeDeletingTheRegistration() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        registrationService.cancel(5, NORMALIZED_EMAIL);

        final org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(mockEventDao, mockRegistrationDao);
        inOrder.verify(mockEventDao).findByIdForUpdate(EVENT_ID);
        inOrder.verify(mockRegistrationDao).delete(5);
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

    private static Registration pendingRegistration(final long id, final String email) {
        final Registration registration = new Registration(EVENT_ID, email, NAME, RegistrationStatus.PENDING);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testApproveConfirmsAPendingRegistrationOwnedByTheCreator() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final ApprovalResult result = registrationService.approve(5, CREATOR_EMAIL);

        assertEquals(ApprovalResult.APPROVED, result);
        verify(mockRegistrationDao).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheEventBelongsToSomeoneElse() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final ApprovalResult result = registrationService.approve(5, "otro@mail.com");

        assertEquals(ApprovalResult.NOT_FOUND, result);
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationIsNotPending() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        assertEquals(ApprovalResult.NOT_FOUND, registrationService.approve(5, CREATOR_EMAIL));
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationDoesNotExist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        assertEquals(ApprovalResult.NOT_FOUND, registrationService.approve(5, CREATOR_EMAIL));
    }

    // El organizador acepta hasta que se llena el cupo, y ni un pedido mas.
    @Test
    public void testApproveReturnsFullAndChangesNothingWhenThereIsNoRoomLeft() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        assertEquals(ApprovalResult.FULL, registrationService.approve(5, CREATOR_EMAIL));
        verify(mockRegistrationDao, never()).updateStatus(5, RegistrationStatus.CONFIRMED);
        verify(mockNotificationService, never()).notifyRegistrationApproved(any(), any());
    }

    // Las invitaciones por mail tambien ocupan lugar: no se puede aceptar a nadie
    // en el lugar que le prometimos a alguien invitado.
    @Test
    public void testApproveReturnsFullWhenTheLastSpotIsHeldByAnInvitation() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        assertEquals(ApprovalResult.FULL, registrationService.approve(5, CREATOR_EMAIL));
    }

    // Cuando esta aceptacion ocupa el ultimo lugar, el resto de los pendientes ya
    // no tiene a donde entrar: pasan solos a la lista de espera.
    @Test
    public void testApproveTakingTheLastSpotMovesTheRemainingPendingToTheWaitlist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        // Antes de aceptar quedaba 1 lugar (1 confirmado); despues, 2 de 2.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);
        final Registration other = pendingRegistration(6, "otro@mail.com");
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID)).thenReturn(Collections.singletonList(other));

        assertEquals(ApprovalResult.APPROVED, registrationService.approve(5, CREATOR_EMAIL));

        verify(mockRegistrationDao).updateStatus(6, RegistrationStatus.WAITLISTED);
        verify(mockNotificationService).notifyWaitlistJoined(any(Event.class), eq(other));
        verify(mockNotificationService).notifyEventFull(any(Event.class));
    }

    @Test
    public void testApproveWithSpotsStillLeftKeepsTheOtherRequestsPending() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);

        assertEquals(ApprovalResult.APPROVED, registrationService.approve(5, CREATOR_EMAIL));

        verify(mockRegistrationDao, never()).findPendingByEvent(EVENT_ID);
    }

    // ---- reject() ----

    @Test
    public void testRejectMarksAPendingRegistrationOwnedByTheCreatorAsRejected() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        final boolean result = registrationService.reject(5, CREATOR_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).updateStatus(5, RegistrationStatus.REJECTED);
    }

    // Un pedido pendiente no ocupaba cupo: rechazarlo no libera ningun lugar, asi
    // que no hay nadie a quien promover desde la lista de espera.
    @Test
    public void testRejectDoesNotTouchTheWaitlistBecausePendingNeverHeldASpot() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        registrationService.reject(5, CREATOR_EMAIL);

        verify(mockRegistrationDao, never()).findNextWaitlisted(anyLong(), anyInt());
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

    // ---- revoke() ----

    private static Registration confirmedRegistration(final long id, final String email) {
        final Registration registration = new Registration(EVENT_ID, email, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testRevokeMarksAConfirmedRegistrationAsRejectedAndTellsThePerson() {
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        assertTrue(registrationService.revoke(7, CREATOR_EMAIL));

        verify(mockRegistrationDao).updateStatus(7, RegistrationStatus.REJECTED);
        verify(mockNotificationService).notifyRegistrationRevoked(any(Event.class), any(Registration.class));
        verify(mockInboxService).add(eq(NORMALIZED_EMAIL), eq(InboxMessageType.REGISTRATION_REVOKED),
                eq(EVENT_ID), any(), any());
    }

    // El lugar libre lo ocupa el primero de la lista de espera, sin invitacion ni espera.
    @Test
    public void testRevokePromotesTheFirstPersonOnTheWaitlist() {
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        // Con el lugar revocado ya no cuenta como confirmado: queda 1 de 2.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        final Registration waiting = new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED);
        waiting.setId(8);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waiting));

        registrationService.revoke(7, CREATOR_EMAIL);

        verify(mockRegistrationDao).updateStatus(8, RegistrationStatus.CONFIRMED);
        verify(mockNotificationService).notifyWaitlistPromoted(any(Event.class), eq(waiting));
    }

    @Test
    public void testRevokeReturnsFalseWhenTheRegistrationIsNotConfirmed() {
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(pendingRegistration(7, NORMALIZED_EMAIL)));

        assertFalse(registrationService.revoke(7, CREATOR_EMAIL));
        verify(mockRegistrationDao, never()).updateStatus(anyLong(), any());
    }

    @Test
    public void testRevokeReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        assertFalse(registrationService.revoke(7, "otro@mail.com"));
        verify(mockRegistrationDao, never()).updateStatus(anyLong(), any());
    }

    // ---- promoteFromWaitlist() ----

    @Test
    public void testPromoteConfirmsAsManyWaitlistedPeopleAsFreeSpotsInArrivalOrder() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(3L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(0L);
        final Registration first = new Registration(EVENT_ID, "a@mail.com", "A", RegistrationStatus.WAITLISTED);
        first.setId(1);
        final Registration second = new Registration(EVENT_ID, "b@mail.com", "B", RegistrationStatus.WAITLISTED);
        second.setId(2);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 2)).thenReturn(java.util.Arrays.asList(first, second));

        assertEquals(2, registrationService.promoteFromWaitlist(EVENT_ID));

        verify(mockRegistrationDao).updateStatus(1, RegistrationStatus.CONFIRMED);
        verify(mockRegistrationDao).updateStatus(2, RegistrationStatus.CONFIRMED);
    }

    // Las invitaciones a un evento privado reservan lugar: no se le da a la
    // lista de espera el lugar que esta prometido a alguien invitado.
    // En un evento que requiere confirmacion, quien sale de la lista de espera pasa a
    // PENDING (no a CONFIRMED): el organizador todavia lo tiene que aceptar.
    @Test
    public void testPromoteMovesWaitlistedToPendingWhenTheEventRequiresApproval() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(0L);
        final Registration waiting = new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED);
        waiting.setId(8);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waiting));

        assertEquals(1, registrationService.promoteFromWaitlist(EVENT_ID));

        verify(mockRegistrationDao).updateStatus(8, RegistrationStatus.PENDING);
        verify(mockRegistrationDao, never()).updateStatus(8, RegistrationStatus.CONFIRMED);
        verify(mockNotificationService).notifyRegistrationPending(any(Event.class), eq(waiting));
        verify(mockNotificationService, never()).notifyWaitlistPromoted(any(), any());
    }

    // Un lugar libre que ya tiene un pedido pendiente esperando respuesta no se le
    // "regala" tambien a la lista de espera.
    @Test
    public void testPromoteToPendingDiscountsRequestsAlreadyWaitingForAnAnswer() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);

        assertEquals(0, registrationService.promoteFromWaitlist(EVENT_ID));
        verify(mockRegistrationDao, never()).findNextWaitlisted(anyLong(), anyInt());
    }

    @Test
    public void testPromoteRespectsSpotsHeldByUnusedInvitations() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(3L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        assertEquals(0, registrationService.promoteFromWaitlist(EVENT_ID));
        verify(mockRegistrationDao, never()).findNextWaitlisted(anyLong(), anyInt());
    }

    @Test
    public void testPromoteDoesNothingWhenTheEventIsFull() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        assertEquals(0, registrationService.promoteFromWaitlist(EVENT_ID));
        verify(mockRegistrationDao, never()).updateStatus(anyLong(), any());
    }

    @Test
    public void testPromoteDoesNothingForACancelledEvent() {
        final Event cancelled = eventWithCreator(5);
        cancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        assertEquals(0, registrationService.promoteFromWaitlist(EVENT_ID));
        verify(mockRegistrationDao, never()).updateStatus(anyLong(), any());
    }

    // Si el organizador saca el limite de cupo, entra toda la lista de espera.
    @Test
    public void testPromoteConfirmsTheWholeWaitlistWhenTheEventBecomesUnlimited() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(null)));
        final Registration only = new Registration(EVENT_ID, "a@mail.com", "A", RegistrationStatus.WAITLISTED);
        only.setId(1);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, Integer.MAX_VALUE))
                .thenReturn(Collections.singletonList(only));

        assertEquals(1, registrationService.promoteFromWaitlist(EVENT_ID));
    }
}
