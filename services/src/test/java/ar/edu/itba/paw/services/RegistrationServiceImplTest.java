package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegistrationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";
    private static final String CREATOR_EMAIL = "creador@mail.com";
    // Quien se anota siempre esta logueado (ver EventController.rsvp()): el
    // service ya no recibe un email/nombre sueltos, recibe el User entero
    // (con su id, que es lo que ahora identifica la fila). USER_ID/CREATOR_ID/
    // OTHER_ID son ids de "utileria" para estos tests con mocks, no hace falta
    // que existan de verdad en ninguna base.
    private static final long USER_ID = 10;
    private static final long CREATOR_ID = 20;
    private static final long OTHER_ID = 30;

    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private EventDao mockEventDao;
    // Idem: RegistrationServiceImpl le pregunta cuantas invitaciones por mail a un
    // evento privado siguen sin usar (cada una reserva un lugar) y si quien se
    // anota tiene una. Sin stubear, un mock devuelve 0 / vacio: "nadie invitado".
    @Mock
    private EventInvitationDao mockInvitationDao;
    // Avisos y bandeja: fakes escritos a mano que anotan lo que les piden. Cada test
    // hace assert sobre esa lista.
    private final RecordingNotificationService notifications = new RecordingNotificationService();
    private final RecordingInboxService inbox = new RecordingInboxService();

    private RegistrationServiceImpl registrationService;

    @Before
    public void setUp() {
        registrationService = new RegistrationServiceImpl(
                mockRegistrationDao, mockEventDao, notifications, mockInvitationDao, inbox);
    }

    private static User someUser() {
        final User user = new User(EMAIL, NAME);
        user.setId(USER_ID);
        return user;
    }

    private static Event eventWithCapacity(final Integer maxCapacity) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setMaxCapacity(maxCapacity);
        return event;
    }

    private static Event eventRequiringApproval(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setRequiresApproval(true);
        return event;
    }

    // Los avisos y la bandeja se leen de los fakes (notifications / inbox); el
    // resto del contrato se prueba con lo que devuelve cada metodo.

    // A quien se aviso (la Registration, segundo argumento) con ese tipo de aviso, en orden.
    private List<Object> registrationsNotified(final String method) {
        final List<Object> result = new ArrayList<>();
        for (final Object[] args : notifications.argsOf(method)) {
            result.add(args[1]);
        }
        return result;
    }

    // El service guarda la inscripcion con el email normalizado, el id del usuario y el estado que
    // corresponde: si alguno no coincide, este stub no matchea y la inscripcion no se guarda.
    private void createdAs(final RegistrationStatus status) {
        when(mockRegistrationDao.create(argThat(registration -> registration.getStatus() == status
                && registration.getEventId() == EVENT_ID
                && NORMALIZED_EMAIL.equals(registration.getEmail())
                && Long.valueOf(USER_ID).equals(registration.getUserId()))))
                .thenReturn(new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, status));
    }

    // ---- register() ----

    @Test
    public void testRegisterReturnsAlreadyRegisteredWhenTheEmailIsAlreadyInThatEvent() {
        // Arrange
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.ALREADY_REGISTERED, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterWithNonexistentEventThrowsIllegalArgumentException() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testRegisterWhenTheEventIsFullReturnsWaitlisted() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        createdAs(RegistrationStatus.WAITLISTED);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.WAITLISTED, result);
    }

    @Test
    public void testRegisterWhenAlreadyOnTheWaitlistReturnsAlreadyWaitlisted() {
        // Arrange
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.ALREADY_WAITLISTED, result);
    }

    @Test
    public void testRegisterWithFreeSpotReturnsConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        createdAs(RegistrationStatus.CONFIRMED);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    @Test
    public void testRegisterWithUnlimitedCapacityNeverThrowsEvenIfManyAreConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        createdAs(RegistrationStatus.CONFIRMED);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    // ---- register(): el creador no se anota a su propio evento ----

    @Test
    public void testRegisterReturnsOwnEventWhenTheCreatorTriesToRegister() {
        // Arrange
        final Event ownEvent = eventWithCapacity(10);
        ownEvent.setCreatorId(USER_ID);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(ownEvent));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.OWN_EVENT, result);
    }

    @Test
    public void testRegisterDoesNotCreateOrNotifyWhenTheCreatorTriesToRegister() {
        // Arrange
        final Event ownEvent = eventWithCapacity(10);
        ownEvent.setCreatorId(USER_ID);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(ownEvent));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        // OWN_EVENT solo se devuelve ANTES de crear la inscripcion o notificar a
        // nadie (early return): el resultado ya prueba que nada de eso paso.
        assertEquals(RegistrationResult.OWN_EVENT, result);
    }

    // ---- register() con requiresApproval ----

    @Test
    public void testRegisterOnEventRequiringApprovalReturnsPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        createdAs(RegistrationStatus.PENDING);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.PENDING, result);
    }

    // Sin cupo, la lista de espera gana: no tiene sentido "esperando revision"
    // para un lugar que ni siquiera existe.
    @Test
    public void testRegisterOnFullEventRequiringApprovalReturnsWaitlistedNotPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(1)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        createdAs(RegistrationStatus.WAITLISTED);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.WAITLISTED, result);
    }

    @Test
    public void testRegisterWhenAlreadyPendingReturnsAlreadyPending() {
        // Arrange
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.ALREADY_PENDING, result);
    }

    @Test
    public void testRegisterWhenPreviouslyRejectedReturnsRejected() {
        // Arrange
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.REJECTED, result);
    }

    /**
     * El criterio de cupo: solo cuentan los confirmados (y las invitaciones a un
     * evento privado). Los pedidos pendientes de aprobacion NO ocupan lugar: el
     * organizador los mira a todos y acepta hasta que se llene.
     */
    @Test
    public void testIsFullDoesNotCountPendingApprovals() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        // Hay muchos pendientes: si isFull() los contara, el evento daria "lleno".
        lenient().when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(5L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    // Con la mitad de los lugares confirmados, seguir aceptando pedidos pendientes
    // (aunque haya mas pedidos que lugares) esta permitido: nacen PENDING.
    @Test
    public void testRegisterOnApprovalEventWithMorePendingThanSpotsStillCreatesPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        createdAs(RegistrationStatus.PENDING);
        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.PENDING, result);
    }

    // ---- aviso "tu evento se lleno" ----

    @Test
    public void testRegisterNotifiesTheOrganizerWhenItTakesTheLastSpot() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        // Antes del insert habia 9 confirmados (quedaba 1 lugar); despues, 10.
        // Los dos valores en orden: es exactamente el cruce del limite.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(9L, 10L);

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(1, notifications.count("notifyEventFull"));
    }

    @Test
    public void testRegisterDoesNotNotifyTheOrganizerWhenSpotsRemain() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L, 6L);

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(0, notifications.count("notifyEventFull"));
    }

    @Test
    public void testRegisterDoesNotNotifyAgainWhenTheEventWasAlreadyFull() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(10L);
        // Esta persona queda en lista de espera. El evento ya estaba lleno antes,
        // no hay ningun limite que cruzar: sin este chequeo, el organizador
        // recibiria un mail por cada persona que se sume a la lista.

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(0, notifications.count("notifyEventFull"));
    }

    // ---- cancel(): aviso de baja al organizador ----

    private static final long REGISTRATION_ID = 7;

    private static Registration registrationWith(final RegistrationStatus status) {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, status);
        registration.setId(REGISTRATION_ID);
        registration.setUserId(USER_ID);
        return registration;
    }

    @Test
    public void testCancelTellsTheOrganizerThatTheSpotIsOpenWhenNobodyWasWaiting() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        // Nadie estaba esperando -> el lugar quedo abierto al publico (offeredToWaitlist = false).
        assertEquals(1, notifications.count("notifyRegistrationCancelled"));
        assertEquals(Boolean.FALSE, notifications.argsOf("notifyRegistrationCancelled").get(0)[2]);
    }

    @Test
    public void testCancelTellsTheOrganizerWhenTheSpotWentToTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration waiting = registrationWith(RegistrationStatus.WAITLISTED);
        waiting.setId(99);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 10)).thenReturn(Collections.singletonList(waiting));

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        when(mockRegistrationDao.updateStatus(99, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        // El dato sale de a cuantos promovio la lista de espera, no de otra consulta.
        assertEquals(1, notifications.count("notifyRegistrationCancelled"));
        assertEquals(Boolean.TRUE, notifications.argsOf("notifyRegistrationCancelled").get(0)[2]);
    }

    @Test
    public void testCancelDoesNotNotifyWhenSomeoneLeavesTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        // Para el organizador no cambio nada: el cupo disponible es el mismo.

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        assertEquals(0, notifications.count("notifyRegistrationCancelled"));
    }

    // ---- avisos en la bandeja ----

    private static Event eventWithCreator(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        return event;
    }

    @Test
    public void testRegisterWithFreeSpotLeavesAMessageForThePersonAndForTheOrganizer() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(inbox.recipients().contains(NORMALIZED_EMAIL));
        assertTrue(inbox.recipients().contains(CREATOR_EMAIL));
    }

    @Test
    public void testRegisterOnAFullEventOnlyTellsThePersonTheyAreWaitlisted() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(inbox.recipients().contains(NORMALIZED_EMAIL));
        assertFalse(inbox.recipients().contains(CREATOR_EMAIL));
    }

    @Test
    public void testRegisterOnAnEventRequiringApprovalAsksTheOrganizerToAnswer() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(inbox.recipients().contains(NORMALIZED_EMAIL));
        assertTrue(inbox.recipients().contains(CREATOR_EMAIL));
    }

    @Test
    public void testCancelOfAConfirmedRegistrationTellsTheOrganizerInTheInbox() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        assertTrue(inbox.recipients().contains(CREATOR_EMAIL));
    }

    @Test
    public void testCancelOfAPendingRequestTellsTheOrganizerInTheInboxButSendsNoMail() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.PENDING)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        // Un pendiente nunca tuvo lugar: hay aviso en la bandeja pero no mail de baja.
        assertTrue(inbox.recipients().contains(CREATOR_EMAIL));
        assertEquals(0, notifications.count("notifyRegistrationCancelled"));
    }

    @Test
    public void testCancelFromTheWaitlistDoesNotTellTheOrganizer() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.delete(REGISTRATION_ID)).thenReturn(true);
        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        assertTrue(inbox.all().isEmpty());
    }

    @Test
    public void testApproveTellsThePersonInTheInbox() {
        // Arrange
        final Registration pending = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        pending.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pending));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        registrationService.approve(5, CREATOR_ID);

        // Assert
        assertTrue(inbox.recipients().contains(NORMALIZED_EMAIL));
    }

    @Test
    public void testRejectTellsThePersonInTheInbox() {
        // Arrange
        final Registration pending = new Registration(EVENT_ID, "otra@mail.com", "Otra", RegistrationStatus.PENDING);
        pending.setId(6);
        when(mockRegistrationDao.findById(6)).thenReturn(Optional.of(pending));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(6, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        registrationService.reject(6, CREATOR_ID);

        // Assert
        assertTrue(inbox.recipients().contains("otra@mail.com"));
    }

    // ---- isFull() ----

    @Test
    public void testIsFullReturnsTrueWhenConfirmedCountReachesCapacity() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenThereIsStillRoom() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
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
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAvailableSpotsDiscountsSpotsHeldByUnusedInvitations() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(7L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        // Act
        final Integer result = registrationService.availableSpots(EVENT_ID);

        // Assert
        // 10 - 7 confirmados - 2 invitaciones = 1. Es el numero que ve la gente en
        // pantalla: si mostrara 3, tres personas creerian que hay lugar.
        assertEquals(Integer.valueOf(1), result);
    }

    @Test
    public void testRegisterGoesToTheWaitlistWhenTheLastSpotIsHeldByAnInvitation() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.WAITLISTED, result);
    }

    // La persona invitada usa el lugar que ya tenia reservado: para ella el evento
    // no esta lleno, aunque confirmados + invitaciones lleguen al cupo justo.
    @Test
    public void testRegisterWithAnInvitationTakesItsReservedSpotAndIsConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, CREATOR_EMAIL)));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    // Quien fue invitado ya fue elegido por el organizador: no vuelve a pedirle aprobacion.
    @Test
    public void testRegisterWithAnInvitationSkipsTheApprovalStep() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, CREATOR_EMAIL)));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    @Test
    public void testIsFullForAnInvitedPersonIgnoresTheirOwnReservedSpot() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_EMAIL, CREATOR_EMAIL)));

        // Act
        final boolean result = registrationService.isFullFor(EVENT_ID, USER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenCapacityIsUnlimited() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsFullWithNonexistentEventThrowsIllegalArgumentException() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        registrationService.isFull(EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findByUserId() ----

    @Test
    public void testFindByUserIdDelegatesToTheDao() {
        // Arrange
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.findByUserId(USER_ID)).thenReturn(registrations);

        // Act
        final List<Registration> result = registrationService.findByUserId(USER_ID);

        // Assert
        assertEquals(registrations, result);
    }

    // ---- cancel() ----

    @Test
    public void testCancelDeletesWhenTheUserOwnsTheRegistration() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        registration.setUserId(USER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.delete(5)).thenReturn(true);
        // Act
        final UnregisterResult result = registrationService.cancel(5, USER_ID);

        // Assert
        assertEquals(UnregisterResult.CANCELLED, result);
    }

    // Un rechazado / revocado no puede "darse de baja": si borrara su fila se saltearia
    // el rechazo (y podria volver a anotarse).
    @Test
    public void testCancelReturnsRejectedAndKeepsTheRowWhenTheRegistrationWasRejected() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        registration.setId(5);
        registration.setUserId(USER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        final UnregisterResult result = registrationService.cancel(5, USER_ID);

        // Assert
        assertEquals(UnregisterResult.REJECTED, result);
    }

    @Test
    public void testCancelReturnsNotFoundWhenTheRegistrationDoesNotExist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        // Act
        final UnregisterResult result = registrationService.cancel(5, USER_ID);

        // Assert
        assertEquals(UnregisterResult.NOT_FOUND, result);
    }

    @Test
    public void testCancelReturnsNotFoundWhenTheRegistrationBelongsToSomeoneElse() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, "otro@mail.com", NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        registration.setUserId(OTHER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final UnregisterResult result = registrationService.cancel(5, USER_ID);

        // Assert
        assertEquals(UnregisterResult.NOT_FOUND, result);
    }

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdDelegatesToTheDao() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(registration));

        // Act
        final Optional<Registration> result = registrationService.findByEventAndUserId(EVENT_ID, USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(NORMALIZED_EMAIL, result.get().getEmail());
    }

    @Test
    public void testFindByEventAndUserIdReturnsEmptyWhenNotRegistered() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Registration> result = registrationService.findByEventAndUserId(EVENT_ID, USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findByEventId() ----

    @Test
    public void testFindByEventIdDelegatesToTheDao() {
        // Arrange
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.findByEventId(EVENT_ID)).thenReturn(registrations);

        // Act
        final List<Registration> result = registrationService.findByEventId(EVENT_ID);

        // Assert
        assertEquals(registrations, result);
    }

    // ---- findPendingByEvent() ----

    @Test
    public void testFindPendingByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        final List<Registration> pending = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING));
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID)).thenReturn(pending);

        // Act
        final List<Registration> result = registrationService.findPendingByEvent(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(pending, result);
    }

    @Test
    public void testFindPendingByEventReturnsEmptyWhenAskedBySomeoneElse() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        // El dao SI tiene un pendiente: si el service lo consultara para cualquiera,
        // el resultado no estaria vacio.
        lenient().when(mockRegistrationDao.findPendingByEvent(EVENT_ID))
                .thenReturn(Collections.singletonList(pendingRegistration(5, NORMALIZED_EMAIL)));

        // Act
        final List<Registration> result = registrationService.findPendingByEvent(EVENT_ID, OTHER_ID);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindPendingByEventReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final List<Registration> result = registrationService.findPendingByEvent(EVENT_ID, CREATOR_ID);

        // Assert
        assertTrue(result.isEmpty());
    }

    // ---- approve() ----

    private static Registration pendingRegistration(final long id, final String email) {
        final Registration registration = new Registration(EVENT_ID, email, NAME, RegistrationStatus.PENDING);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testApproveConfirmsAPendingRegistrationOwnedByTheCreator() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.APPROVED, result);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheEventBelongsToSomeoneElse() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        // Act
        final ApprovalResult result = registrationService.approve(5, OTHER_ID);

        // Assert
        assertEquals(ApprovalResult.NOT_FOUND, result);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationIsNotPending() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.NOT_FOUND, result);
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationDoesNotExist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.NOT_FOUND, result);
    }

    // El organizador acepta hasta que se llena el cupo, y ni un pedido mas.
    @Test
    public void testApproveReturnsFullAndChangesNothingWhenThereIsNoRoomLeft() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.FULL, result);
    }

    // Las invitaciones por mail tambien ocupan lugar: no se puede aceptar a nadie
    // en el lugar que le prometimos a alguien invitado.
    @Test
    public void testApproveReturnsFullWhenTheLastSpotIsHeldByAnInvitation() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.FULL, result);
    }

    // Cuando esta aceptacion ocupa el ultimo lugar, el resto de los pendientes ya
    // no tiene a donde entrar: pasan solos a la lista de espera.
    @Test
    public void testApproveTakingTheLastSpotMovesTheRemainingPendingToTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        // Antes de aceptar quedaba 1 lugar (1 confirmado); despues, 2 de 2.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);
        final Registration other = pendingRegistration(6, "otro@mail.com");
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID)).thenReturn(Collections.singletonList(other));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        when(mockRegistrationDao.updateStatus(6, RegistrationStatus.WAITLISTED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.APPROVED, result);
        // El pendiente que quedo sin lugar recibe el aviso de lista de espera.
        assertEquals(Collections.singletonList(other), registrationsNotified("notifyWaitlistJoined"));
    }

    @Test
    public void testApproveTakingTheLastSpotNotifiesThatTheEventIsFull() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(1, notifications.count("notifyEventFull"));
    }

    @Test
    public void testApproveWithSpotsStillLeftKeepsTheOtherRequestsPending() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);
        // Hay otro pedido pendiente: como todavia hay lugar, no debe pasar a la lista de espera.
        lenient().when(mockRegistrationDao.findPendingByEvent(EVENT_ID))
                .thenReturn(Collections.singletonList(pendingRegistration(6, "otro@mail.com")));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.APPROVED, result);
        assertEquals(0, notifications.count("notifyWaitlistJoined"));
    }

    // ---- reject() ----

    @Test
    public void testRejectMarksAPendingRegistrationOwnedByTheCreatorAsRejected() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        final boolean result = registrationService.reject(5, CREATOR_ID);

        // Assert
        assertTrue(result);
    }

    // Un pedido pendiente no ocupaba cupo: rechazarlo no libera ningun lugar, asi
    // que no hay nadie a quien promover desde la lista de espera.
    @Test
    public void testRejectDoesNotTouchTheWaitlistBecausePendingNeverHeldASpot() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        final boolean result = registrationService.reject(5, CREATOR_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRejectReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        // Act
        final boolean result = registrationService.reject(5, OTHER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRejectReturnsFalseWhenTheRegistrationIsNotPending() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final boolean result = registrationService.reject(5, CREATOR_ID);

        // Assert
        assertFalse(result);
    }

    // ---- revoke() ----

    private static Registration confirmedRegistration(final long id, final String email) {
        final Registration registration = new Registration(EVENT_ID, email, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testRevokeMarksAConfirmedRegistrationAsRejectedAndTellsThePerson() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.updateStatus(7, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        final boolean result = registrationService.revoke(7, CREATOR_ID);

        // Assert
        assertTrue(result);
        assertEquals(1, notifications.count("notifyRegistrationRevoked"));
        assertTrue(inbox.recipients().contains(NORMALIZED_EMAIL));
    }

    // El lugar libre lo ocupa el primero de la lista de espera, sin invitacion ni espera.
    @Test
    public void testRevokePromotesTheFirstPersonOnTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        // Con el lugar revocado ya no cuenta como confirmado: queda 1 de 2.
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        final Registration waiting = new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED);
        waiting.setId(8);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waiting));

        when(mockRegistrationDao.updateStatus(7, RegistrationStatus.REJECTED)).thenReturn(true);
        when(mockRegistrationDao.updateStatus(8, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        registrationService.revoke(7, CREATOR_ID);

        // Assert
        assertEquals(Collections.singletonList(waiting), registrationsNotified("notifyWaitlistPromoted"));
    }

    @Test
    public void testRevokeReturnsFalseWhenTheRegistrationIsNotConfirmed() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(pendingRegistration(7, NORMALIZED_EMAIL)));

        // Act
        final boolean result = registrationService.revoke(7, CREATOR_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRevokeReturnsFalseWhenTheEventBelongsToSomeoneElse() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        final boolean result = registrationService.revoke(7, OTHER_ID);

        // Assert
        assertFalse(result);
    }

    // ---- promoteFromWaitlist() ----

    @Test
    public void testPromoteConfirmsAsManyWaitlistedPeopleAsFreeSpotsInArrivalOrder() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(3L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(0L);
        final Registration first = new Registration(EVENT_ID, "a@mail.com", "A", RegistrationStatus.WAITLISTED);
        first.setId(1);
        final Registration second = new Registration(EVENT_ID, "b@mail.com", "B", RegistrationStatus.WAITLISTED);
        second.setId(2);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 2)).thenReturn(Arrays.asList(first, second));

        when(mockRegistrationDao.updateStatus(1, RegistrationStatus.CONFIRMED)).thenReturn(true);
        when(mockRegistrationDao.updateStatus(2, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(2, promoted);
        assertEquals(Arrays.asList(first, second), registrationsNotified("notifyWaitlistPromoted"));
    }

    // Las invitaciones a un evento privado reservan lugar: no se le da a la
    // lista de espera el lugar que esta prometido a alguien invitado.
    // En un evento que requiere confirmacion, quien sale de la lista de espera pasa a
    // PENDING (no a CONFIRMED): el organizador todavia lo tiene que aceptar.
    @Test
    public void testPromoteMovesWaitlistedToPendingWhenTheEventRequiresApproval() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(0L);
        final Registration waiting = new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED);
        waiting.setId(8);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, 1)).thenReturn(Collections.singletonList(waiting));

        when(mockRegistrationDao.updateStatus(8, RegistrationStatus.PENDING)).thenReturn(true);
        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
        // Pasa a PENDING (se le avisa que espera respuesta), no a CONFIRMED.
        assertEquals(Collections.singletonList(waiting), registrationsNotified("notifyRegistrationPending"));
        assertEquals(0, notifications.count("notifyWaitlistPromoted"));
    }

    // Un lugar libre que ya tiene un pedido pendiente esperando respuesta no se le
    // "regala" tambien a la lista de espera.
    @Test
    public void testPromoteToPendingDiscountsRequestsAlreadyWaitingForAnAnswer() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);
        // Hay alguien esperando: si el service ignorara el pendiente, lo promoveria.
        lenient().when(mockRegistrationDao.findNextWaitlisted(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(
                        new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED)));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    @Test
    public void testPromoteRespectsSpotsHeldByUnusedInvitations() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(3L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);
        // Hay alguien esperando: si el service ignorara las invitaciones, lo promoveria.
        lenient().when(mockRegistrationDao.findNextWaitlisted(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(
                        new Registration(EVENT_ID, "espera@mail.com", "Beto", RegistrationStatus.WAITLISTED)));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    @Test
    public void testPromoteDoesNothingWhenTheEventIsFull() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    @Test
    public void testPromoteDoesNothingForACancelledEvent() {
        // Arrange
        final Event cancelled = eventWithCreator(5);
        cancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    // Si el organizador saca el limite de cupo, entra toda la lista de espera.
    @Test
    public void testPromoteConfirmsTheWholeWaitlistWhenTheEventBecomesUnlimited() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(null)));
        final Registration only = new Registration(EVENT_ID, "a@mail.com", "A", RegistrationStatus.WAITLISTED);
        only.setId(1);
        when(mockRegistrationDao.findNextWaitlisted(EVENT_ID, Integer.MAX_VALUE))
                .thenReturn(Collections.singletonList(only));

        when(mockRegistrationDao.updateStatus(1, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
    }

    // ---- occupiedSpots() ----

    @Test
    public void testOccupiedSpotsAddsTheConfirmedAndTheUnusedInvitations() {
        // Arrange
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(7L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        // Act
        final long result = registrationService.occupiedSpots(EVENT_ID);

        // Assert
        assertEquals(9L, result);
    }
}
