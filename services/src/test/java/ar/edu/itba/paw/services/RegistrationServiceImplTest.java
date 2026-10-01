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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
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
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

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

    // Todos los metodos void (create/updateStatus/notify.../inboxService.add)
    // se capturan con doAnswer() sobre el stub, nunca con Mockito.verify():
    // Unidad 5 - Unit Testing dice explicitamente que verify() prueba la
    // implementacion, no el contrato.

    private Registration[] captureCreatedRegistration() {
        final Registration[] created = new Registration[1];
        doAnswer(invocation -> {
            created[0] = invocation.getArgument(0);
            return null;
        }).when(mockRegistrationDao).create(any(Registration.class));
        return created;
    }

    // Lista de (id, status) con cada llamada a updateStatus(), en orden.
    private List<Map.Entry<Long, RegistrationStatus>> captureUpdateStatusCalls() {
        final List<Map.Entry<Long, RegistrationStatus>> calls = new ArrayList<>();
        doAnswer(invocation -> {
            calls.add(new AbstractMap.SimpleEntry<>(invocation.getArgument(0), invocation.getArgument(1)));
            return null;
        }).when(mockRegistrationDao).updateStatus(anyLong(), any(RegistrationStatus.class));
        return calls;
    }

    private boolean[] captureNotifyEventFull() {
        final boolean[] notified = {false};
        doAnswer(invocation -> {
            notified[0] = true;
            return null;
        }).when(mockNotificationService).notifyEventFull(any(Event.class));
        return notified;
    }

    // Lista de emails destinatarios, en el orden en que se les escribio en la bandeja.
    private List<String> captureInboxRecipients() {
        final List<String> recipients = new ArrayList<>();
        doAnswer(invocation -> {
            recipients.add(invocation.getArgument(0));
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());
        return recipients;
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

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.WAITLISTED, result);
    }

    @Test
    public void testRegisterWhenTheEventIsFullSavesTheRegistrationAsWaitlisted() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);
        final Registration[] created = captureCreatedRegistration();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        // Lo que se guarda es parte del contrato: una fila en estado waitlisted
        // es lo que despues deja armar la lista de espera en orden.
        assertEquals(RegistrationStatus.WAITLISTED, created[0].getStatus());
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

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationResult.PENDING, result);
    }

    @Test
    public void testRegisterOnEventRequiringApprovalSavesTheRegistrationAsPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);
        final Registration[] created = captureCreatedRegistration();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertEquals(RegistrationStatus.PENDING, created[0].getStatus());
    }

    // Sin cupo, la lista de espera gana: no tiene sentido "esperando revision"
    // para un lugar que ni siquiera existe.
    @Test
    public void testRegisterOnFullEventRequiringApprovalReturnsWaitlistedNotPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(1)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

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
        // isFull() no deberia consultar countPending en absoluto: se stubea
        // para que reviente si se llega a llamar.
        lenient().when(mockRegistrationDao.countPending(EVENT_ID))
                .thenThrow(new AssertionError("isFull() no deberia consultar countPending"));

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
        final boolean[] notifiedFull = captureNotifyEventFull();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(notifiedFull[0]);
    }

    @Test
    public void testRegisterDoesNotNotifyTheOrganizerWhenSpotsRemain() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L, 6L);
        lenient().doThrow(new AssertionError("no deberia avisar que el evento se lleno"))
                .when(mockNotificationService).notifyEventFull(any(Event.class));

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert: que no reviente el stub de arriba ES el assert.
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
        lenient().doThrow(new AssertionError("no deberia avisar de nuevo que el evento se lleno"))
                .when(mockNotificationService).notifyEventFull(any(Event.class));

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    // ---- cancel(): aviso de baja al organizador ----

    private static final long REGISTRATION_ID = 7;

    private static Registration registrationWith(final RegistrationStatus status) {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, status);
        registration.setId(REGISTRATION_ID);
        registration.setUserId(USER_ID);
        return registration;
    }

    private Boolean[] captureNotifyRegistrationCancelledPromoted() {
        final Boolean[] promoted = new Boolean[1];
        doAnswer(invocation -> {
            promoted[0] = invocation.getArgument(2);
            return null;
        }).when(mockNotificationService).notifyRegistrationCancelled(any(Event.class), any(Registration.class), anyBoolean());
        return promoted;
    }

    @Test
    public void testCancelTellsTheOrganizerThatTheSpotIsOpenWhenNobodyWasWaiting() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Boolean[] promoted = captureNotifyRegistrationCancelledPromoted();

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        // Nadie estaba esperando -> el lugar quedo abierto al publico.
        assertEquals(Boolean.FALSE, promoted[0]);
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
        final Boolean[] promoted = captureNotifyRegistrationCancelledPromoted();

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        // El dato sale de a cuantos promovio la lista de espera, no de otra consulta.
        assertEquals(Boolean.TRUE, promoted[0]);
    }

    @Test
    public void testCancelDoesNotNotifyWhenSomeoneLeavesTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        // Para el organizador no cambio nada: el cupo disponible es el mismo.
        lenient().doThrow(new AssertionError("no deberia notificar la baja de alguien en lista de espera"))
                .when(mockNotificationService).notifyRegistrationCancelled(any(), any(), anyBoolean());

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert: que no reviente el stub de arriba ES el assert.
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
        final List<String> recipients = captureInboxRecipients();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(recipients.contains(NORMALIZED_EMAIL));
        assertTrue(recipients.contains(CREATOR_EMAIL));
    }

    @Test
    public void testRegisterOnAFullEventOnlyTellsThePersonTheyAreWaitlisted() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);
        final List<String> recipients = captureInboxRecipients();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(recipients.contains(NORMALIZED_EMAIL));
        assertFalse(recipients.contains(CREATOR_EMAIL));
    }

    @Test
    public void testRegisterOnAnEventRequiringApprovalAsksTheOrganizerToAnswer() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        final List<String> recipients = captureInboxRecipients();

        // Act
        registrationService.register(EVENT_ID, someUser());

        // Assert
        assertTrue(recipients.contains(NORMALIZED_EMAIL));
        assertTrue(recipients.contains(CREATOR_EMAIL));
    }

    @Test
    public void testCancelOfAConfirmedRegistrationTellsTheOrganizerInTheInbox() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        final List<String> recipients = captureInboxRecipients();

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        assertTrue(recipients.contains(CREATOR_EMAIL));
    }

    @Test
    public void testCancelOfAPendingRequestTellsTheOrganizerInTheInboxButSendsNoMail() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.PENDING)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        final List<String> recipients = captureInboxRecipients();
        lenient().doThrow(new AssertionError("un pendiente nunca tuvo lugar, no corresponde mail de baja"))
                .when(mockNotificationService).notifyRegistrationCancelled(any(), any(), anyBoolean());

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert
        assertTrue(recipients.contains(CREATOR_EMAIL));
    }

    @Test
    public void testCancelFromTheWaitlistDoesNotTellTheOrganizer() {
        // Arrange
        when(mockRegistrationDao.findById(REGISTRATION_ID))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.WAITLISTED)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        lenient().doThrow(new AssertionError("no deberia escribir nada en la bandeja"))
                .when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        registrationService.cancel(REGISTRATION_ID, USER_ID);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    @Test
    public void testApproveAndRejectTellThePerson() {
        // Arrange
        final Registration aprobada = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        aprobada.setId(5);
        final Registration rechazada = new Registration(EVENT_ID, "otra@mail.com", "Otra", RegistrationStatus.PENDING);
        rechazada.setId(6);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(aprobada));
        when(mockRegistrationDao.findById(6)).thenReturn(Optional.of(rechazada));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));
        final List<String> recipients = captureInboxRecipients();

        // Act
        registrationService.approve(5, CREATOR_ID);
        registrationService.reject(6, CREATOR_ID);

        // Assert
        assertTrue(recipients.contains(NORMALIZED_EMAIL));
        assertTrue(recipients.contains("otra@mail.com"));
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

        // Act
        final boolean result = registrationService.cancel(5, USER_ID);

        // Assert
        assertTrue(result);
    }

    // Un rechazado / revocado no puede "darse de baja": si borrara su fila se saltearia
    // el rechazo (y podria volver a anotarse).
    @Test
    public void testCancelReturnsFalseAndKeepsTheRowWhenTheRegistrationWasRejected() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        registration.setId(5);
        registration.setUserId(USER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        final boolean result = registrationService.cancel(5, USER_ID);

        // Assert
        assertFalse(result);
    }

    // Orden de locks: el evento se bloquea ANTES de tocar (borrar) la inscripcion.
    @Test
    public void testCancelLocksTheEventBeforeDeletingTheRegistration() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        registration.setUserId(USER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        // El orden de llamadas se registra en una lista comun (sin verify/InOrder):
        // cada stub agrega su nombre apenas lo invocan.
        final List<String> callOrder = new ArrayList<>();
        doAnswer(invocation -> {
            callOrder.add("lock");
            return Optional.of(eventWithCreator(10));
        }).when(mockEventDao).findByIdForUpdate(EVENT_ID);
        doAnswer(invocation -> {
            callOrder.add("delete");
            return null;
        }).when(mockRegistrationDao).delete(5);

        // Act
        registrationService.cancel(5, USER_ID);

        // Assert
        // No es una lista exacta: cancel() exitoso tambien dispara
        // promoteFromWaitlist(), que vuelve a pedir el lock del evento
        // despues. Lo que importa aca es que el PRIMER lock llega antes que
        // el delete.
        assertTrue(callOrder.indexOf("lock") < callOrder.indexOf("delete"));
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationDoesNotExist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        // Act
        final boolean result = registrationService.cancel(5, USER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationBelongsToSomeoneElse() {
        // Arrange
        final Registration registration = new Registration(EVENT_ID, "otro@mail.com", NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5);
        registration.setUserId(OTHER_ID);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final boolean result = registrationService.cancel(5, USER_ID);

        // Assert
        assertFalse(result);
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
        // Si quien pregunta no es el dueño, no deberia ni consultar al dao.
        lenient().when(mockRegistrationDao.findPendingByEvent(EVENT_ID))
                .thenThrow(new AssertionError("no deberia consultar el dao si quien pregunta no es el dueño"));

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
        final List<Map.Entry<Long, RegistrationStatus>> statusCalls = captureUpdateStatusCalls();
        final List<Registration> waitlistJoined = new ArrayList<>();
        doAnswer(invocation -> {
            waitlistJoined.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyWaitlistJoined(any(Event.class), any(Registration.class));
        final boolean[] notifiedFull = captureNotifyEventFull();

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.APPROVED, result);
        assertTrue(statusCalls.contains(new AbstractMap.SimpleEntry<>(6L, RegistrationStatus.WAITLISTED)));
        assertEquals(Collections.singletonList(other), waitlistJoined);
        assertTrue(notifiedFull[0]);
    }

    @Test
    public void testApproveWithSpotsStillLeftKeepsTheOtherRequestsPending() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L, 2L);
        lenient().when(mockRegistrationDao.findPendingByEvent(EVENT_ID))
                .thenThrow(new AssertionError("todavia hay lugar: no deberia mover a nadie a la lista de espera"));

        // Act
        final ApprovalResult result = registrationService.approve(5, CREATOR_ID);

        // Assert
        assertEquals(ApprovalResult.APPROVED, result);
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
        final boolean[] notifiedRevoked = {false};
        doAnswer(invocation -> {
            notifiedRevoked[0] = true;
            return null;
        }).when(mockNotificationService).notifyRegistrationRevoked(any(Event.class), any(Registration.class));
        final List<String> recipients = captureInboxRecipients();

        // Act
        final boolean result = registrationService.revoke(7, CREATOR_ID);

        // Assert
        assertTrue(result);
        assertTrue(notifiedRevoked[0]);
        assertTrue(recipients.contains(NORMALIZED_EMAIL));
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
        final List<Map.Entry<Long, RegistrationStatus>> statusCalls = captureUpdateStatusCalls();
        final List<Registration> promotedNotifications = new ArrayList<>();
        doAnswer(invocation -> {
            promotedNotifications.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyWaitlistPromoted(any(Event.class), any(Registration.class));

        // Act
        registrationService.revoke(7, CREATOR_ID);

        // Assert
        assertTrue(statusCalls.contains(new AbstractMap.SimpleEntry<>(8L, RegistrationStatus.CONFIRMED)));
        assertEquals(Collections.singletonList(waiting), promotedNotifications);
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
        final List<Map.Entry<Long, RegistrationStatus>> statusCalls = captureUpdateStatusCalls();

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(2, promoted);
        assertTrue(statusCalls.contains(new AbstractMap.SimpleEntry<>(1L, RegistrationStatus.CONFIRMED)));
        assertTrue(statusCalls.contains(new AbstractMap.SimpleEntry<>(2L, RegistrationStatus.CONFIRMED)));
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
        final List<Map.Entry<Long, RegistrationStatus>> statusCalls = captureUpdateStatusCalls();
        final List<Registration> pendingNotifications = new ArrayList<>();
        doAnswer(invocation -> {
            pendingNotifications.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyRegistrationPending(any(Event.class), any(Registration.class));
        lenient().doThrow(new AssertionError("con requiresApproval no deberia promover directo a CONFIRMED"))
                .when(mockNotificationService).notifyWaitlistPromoted(any(), any());

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
        assertEquals(Collections.singletonList(new AbstractMap.SimpleEntry<>(8L, RegistrationStatus.PENDING)), statusCalls);
        assertEquals(Collections.singletonList(waiting), pendingNotifications);
    }

    // Un lugar libre que ya tiene un pedido pendiente esperando respuesta no se le
    // "regala" tambien a la lista de espera.
    @Test
    public void testPromoteToPendingDiscountsRequestsAlreadyWaitingForAnAnswer() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);
        lenient().when(mockRegistrationDao.findNextWaitlisted(anyLong(), anyInt()))
                .thenThrow(new AssertionError("sin lugar libre no deberia consultar la lista de espera"));

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
        lenient().when(mockRegistrationDao.findNextWaitlisted(anyLong(), anyInt()))
                .thenThrow(new AssertionError("sin lugar libre no deberia consultar la lista de espera"));

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

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
    }
}
