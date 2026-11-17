package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegistrationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";
    private static final String CREATOR_EMAIL = "creador@mail.com";
    // Quien se anota siempre esta logueado (ver EventRegistrationController.rsvp()): el
    // service recibe solo su id y lee la cuenta (nombre y mail) de UserService, que es lo
    // que identifica la fila. USER_ID/CREATOR_ID/
    // OTHER_ID son ids de "utileria" para estos tests con mocks, no hace falta
    // que existan de verdad en ninguna base.
    private static final long USER_ID = 10;
    private static final long CREATOR_ID = 20;
    private static final long OTHER_ID = 30;
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    @Mock
    private RegistrationDao mockRegistrationDao;
    // El evento (con lock) y cuantos lugares tiene ocupados (confirmados + invitaciones sin usar) los da EventService.
    // Sin stubear la ocupacion, un mock devuelve 0: "nadie ocupa nada".
    @Mock
    private EventService eventService;
    // Si quien se anota tiene una invitacion (le reserva el lugar). Sin stubear, un mock devuelve false: "nadie invitado".
    @Mock
    private EventInvitationService invitationService;
    // Avisos y bandeja: "disparar y olvidar" (void). No se testean aca: verificar
    // que se llamaron seria testear la implementacion de RegistrationServiceImpl,
    // no el contrato de RegistrationService.
    @Mock
    private NotificationService notificationService;
    @Mock
    private InboxService inboxService;
    // "Mis inscripciones" trae la reseña propia de cada evento ya pasado.
    @Mock
    private ReviewService reviewService;
    // El nombre y el mail de quien se anota salen de su cuenta (la sesion solo lleva el id).
    @Mock
    private UserService userService;
    // Los anuncios de cambios masivos de estado (RegistrationsMovedEvent) los escucha MassNotifier: aca no escucha nadie.
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private RegistrationServiceImpl registrationService;

    @Before
    public void setUp() {
        registrationService = new RegistrationServiceImpl(
                mockRegistrationDao, eventService, notificationService, invitationService, inboxService, reviewService,
                userService, eventPublisher);
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(someUser()));
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
        // Futura por defecto: isFinished() no tiene chequeo de null (ver Event.isFinished()),
        // asi que cualquier test que no sea especificamente sobre un evento finalizado
        // necesita una fecha, aunque no la use para nada mas.
        event.setEventDate(new Date(System.currentTimeMillis() + ONE_DAY_MILLIS));
        // El resto de estos tests son sobre cupo/lista de espera, no sobre la revision
        // previa (ver EventApprovalStatus): se asume ya aprobado, como cualquier evento
        // publicado antes de que existiera esta feature. El caso pendiente/rechazado
        // tiene sus propios tests, mas abajo.
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        return event;
    }

    private static Event finishedEvent() {
        final Event event = eventWithCapacity(null);
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        return event;
    }

    private static Event eventRequiringApproval(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setRequiresApproval(true);
        return event;
    }

    // ---- register() ----

    @Test
    public void testRegisterReturnsAlreadyRegisteredWhenTheEmailIsAlreadyInThatEvent() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration existing = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.ALREADY_REGISTERED, result.getStatus());
    }

    @Test(expected = EventNotFoundException.class)
    public void testRegisterWithNonexistentEventThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        registrationService.register(EVENT_ID, USER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testRegisterWhenTheEventAlreadyHappenedReturnsFinished() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(finishedEvent()));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert: no se creo ninguna inscripcion (sin stub de create(), un create() devolveria null y romperia)
        assertEquals(RegistrationResult.Status.FINISHED, result.getStatus());
        assertFalse(result.isNotificationSent());
    }

    @Test
    public void testRegisterWhenTheEventIsFullReturnsWaitlisted() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.WAITLISTED, result.getStatus());
        // Ya estaba lleno antes de esta inscripcion: no hay nada nuevo que anunciar.
        assertFalse(result.isNotificationSent());
    }

    @Test
    public void testRegisterWhenAlreadyOnTheWaitlistReturnsAlreadyWaitlisted() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration existing = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.ALREADY_WAITLISTED, result.getStatus());
    }

    @Test
    public void testRegisterWithFreeSpotReturnsConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(0L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
        // Quedo lugar libre (1 de 2): no se disparo el aviso de "evento lleno".
        assertFalse(result.isNotificationSent());
    }

    @Test
    public void testRegisterWhenConfirmingFillsTheLastSpotMarksNotificationSent() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        // Antes de esta inscripcion habia 1 de 2 (no esta lleno); despues de
        // crearla, el segundo chequeo (post-insert) ya ve 2 de 2 (lleno).
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L, 2L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
        // Esta inscripcion ocupo el ultimo lugar: se disparo notifyEventFull().
        // Se confirma por el valor de retorno, no espiando el mock.
        assertTrue(result.isNotificationSent());
    }

    @Test
    public void testRegisterWithUnlimitedCapacityNeverThrowsEvenIfManyAreConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
    }

    // ---- register(): eventos privados ----

    @Test
    public void testRegisterOnAPrivateEventWithAnInvitationSucceeds() {
        // Arrange
        final Event privateEvent = eventWithCapacity(10);
        privateEvent.setVisibility(EventVisibility.PRIVATE);
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent));
        when(invitationService.isInvited(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
    }

    // ---- register() con requiresApproval ----

    @Test
    public void testRegisterOnEventRequiringApprovalReturnsPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(10)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(0L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.PENDING, result.getStatus());
        // Queda pendiente de aprobacion: no ocupa lugar, asi que no se lleno nada.
        assertFalse(result.isNotificationSent());
    }

    // Sin cupo, la lista de espera gana: no tiene sentido "esperando revision"
    // para un lugar que ni siquiera existe.
    @Test
    public void testRegisterOnFullEventRequiringApprovalReturnsWaitlistedNotPending() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(1)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.WAITLISTED, result.getStatus());
        // Ya estaba lleno: va a la espera y no se vuelve a anunciar que se lleno.
        assertFalse(result.isNotificationSent());
    }

    @Test
    public void testRegisterWhenAlreadyPendingReturnsAlreadyPending() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration existing = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.ALREADY_PENDING, result.getStatus());
    }

    @Test
    public void testRegisterWhenPreviouslyRejectedReturnsRejected() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        final Registration existing = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(existing));

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.REJECTED, result.getStatus());
    }

    /**
     * El criterio de cupo: solo cuentan los confirmados (y las invitaciones a un
     * evento privado). Los pedidos pendientes de aprobacion NO ocupan lugar: el
     * organizador los mira a todos y acepta hasta que se llene.
     */
    @Test
    public void testIsFullDoesNotCountPendingApprovals() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L);
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
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.PENDING, result.getStatus());
        // Pendiente no ocupa lugar: aunque haya mas pedidos que lugares, no se lleno nada.
        assertFalse(result.isNotificationSent());
    }

    // ---- avisos en la bandeja ----

    private static Event eventWithCreator(final Integer maxCapacity) {
        final Event event = eventWithCapacity(maxCapacity);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        return event;
    }

    // ---- isFull() ----

    @Test
    public void testIsFullReturnsTrueWhenConfirmedCountReachesCapacity() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenThereIsStillRoom() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L);

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
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAvailableSpotsDiscountsSpotsHeldByUnusedInvitations() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(10)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(9L);

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
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.WAITLISTED, result.getStatus());
        // El ultimo lugar lo tiene una invitacion: va a la espera, no completo el cupo.
        assertFalse(result.isNotificationSent());
    }

    // La persona invitada usa el lugar que ya tenia reservado: para ella el evento
    // no esta lleno, aunque confirmados + invitaciones lleguen al cupo justo.
    @Test
    public void testRegisterWithAnInvitationTakesItsReservedSpotAndIsConfirmed() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);
        when(invitationService.isInvited(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
    }

    // Quien fue invitado ya fue elegido por el organizador: no vuelve a pedirle aprobacion.
    @Test
    public void testRegisterWithAnInvitationSkipsTheApprovalStep() {
        // Arrange
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(invitationService.isInvited(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final RegistrationResult result = registrationService.register(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationResult.Status.CONFIRMED, result.getStatus());
    }

    @Test
    public void testIsFullForAnInvitedPersonIgnoresTheirOwnReservedSpot() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);
        when(invitationService.isInvited(EVENT_ID, USER_ID)).thenReturn(true);

        // Act
        final boolean result = registrationService.isFullFor(EVENT_ID, USER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenCapacityIsUnlimited() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final boolean result = registrationService.isFull(EVENT_ID);

        // Assert
        assertFalse(result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testIsFullWithNonexistentEventThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        registrationService.isFull(EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findStatusesByEventIds() ----

    @Test
    public void testFindStatusesByEventIdsMapsEachEventToTheStatusOfTheOwnRegistration() {
        // Arrange
        final List<Long> eventIds = Arrays.asList(1L, 2L, 3L);
        when(mockRegistrationDao.findByUserIdAndEventIds(USER_ID, eventIds)).thenReturn(Arrays.asList(
                new Registration(ModelFixtures.event(1), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED),
                new Registration(ModelFixtures.event(3), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED)));

        // Act
        final Map<Long, RegistrationStatus> statuses = registrationService.findStatusesByEventIds(USER_ID, eventIds);

        // Assert: el evento 2 no tiene inscripcion, asi que no esta en el mapa
        assertEquals(2, statuses.size());
        assertEquals(RegistrationStatus.CONFIRMED, statuses.get(1L));
        assertEquals(RegistrationStatus.WAITLISTED, statuses.get(3L));
        assertFalse(statuses.containsKey(2L));
    }

    @Test
    public void testFindStatusesByEventIdsIsEmptyWhenThereAreNoRegistrations() {
        // Arrange
        when(mockRegistrationDao.findByUserIdAndEventIds(USER_ID, Collections.singletonList(1L)))
                .thenReturn(Collections.emptyList());

        // Act + Assert
        assertTrue(registrationService.findStatusesByEventIds(USER_ID, Collections.singletonList(1L)).isEmpty());
    }

    // ---- findMyRegistrations() ----

    private static final long PAST_EVENT_ID = 50;
    private static final long UPCOMING_EVENT_ID = 60;

    // Un evento que todavia no paso (el service decide por su fecha si todavia se puede cancelar la inscripcion).
    private static Event upcomingEvent(final long id) {
        final Event event = ModelFixtures.event(id);
        event.setEventDate(new Date(System.currentTimeMillis() + ONE_DAY_MILLIS));
        return event;
    }

    // Un evento que ya paso (el service lo clasifica por su fecha para decidir si se puede reseñar).
    private static Event pastEvent(final long id) {
        final Event event = ModelFixtures.event(id);
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        return event;
    }

    private static Registration registrationAt(final long eventId) {
        return new Registration(ModelFixtures.event(eventId), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
    }

    private void givenTotals(final long upcoming, final long past) {
        when(mockRegistrationDao.countUpcomingByUserId(eq(USER_ID), any(Date.class))).thenReturn(upcoming);
        when(mockRegistrationDao.countPastByUserId(eq(USER_ID), any(Date.class))).thenReturn(past);
    }

    private void givenUpcomingPage(final int page, final long eventId) {
        when(mockRegistrationDao.findUpcomingByUserId(eq(USER_ID), any(Date.class), eq(page), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registrationAt(eventId)));
        when(eventService.findByIds(Collections.singletonList(eventId)))
                .thenReturn(Collections.singletonList(upcomingEvent(eventId)));
    }

    private void givenPastPage(final int page, final long eventId) {
        when(mockRegistrationDao.findPastByUserId(eq(USER_ID), any(Date.class), eq(page), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registrationAt(eventId)));
        when(eventService.findByIds(Collections.singletonList(eventId)))
                .thenReturn(Collections.singletonList(pastEvent(eventId)));
    }

    @Test
    public void testFindMyRegistrationsHoldsBothGroupsInTheSameResult() {
        // Arrange: pocas de cada grupo
        givenTotals(1, 1);
        givenUpcomingPage(1, UPCOMING_EVENT_ID);
        givenPastPage(1, PAST_EVENT_ID);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 1, 1);

        // Assert
        assertEquals(UPCOMING_EVENT_ID, result.getUpcoming().getContent().get(0).getEvent().getId().longValue());
        assertEquals(PAST_EVENT_ID, result.getPast().getContent().get(0).getEvent().getId().longValue());
    }

    @Test
    public void testFindMyRegistrationsPagesEachGroupWithItsOwnIndex() {
        // Arrange: 25 proximas (3 paginas) y 25 pasadas (3 paginas); proximas en la 3 y pasadas en la 2
        givenTotals(25, 25);
        givenUpcomingPage(3, UPCOMING_EVENT_ID);
        givenPastPage(2, PAST_EVENT_ID);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 3, 2);

        // Assert
        assertEquals(3, result.getUpcoming().getPage());
        assertEquals(2, result.getPast().getPage());
        assertEquals(UPCOMING_EVENT_ID, result.getUpcoming().getContent().get(0).getEvent().getId().longValue());
        assertEquals(PAST_EVENT_ID, result.getPast().getContent().get(0).getEvent().getId().longValue());
    }

    @Test
    public void testFindMyRegistrationsEachGroupHasItsOwnTotals() {
        // Arrange: 25 proximas -> 3 paginas; 12 pasadas -> 2 paginas
        givenTotals(25, 12);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 1, 1);

        // Assert
        assertEquals(25L, result.getUpcoming().getTotalElements());
        assertEquals(3, result.getUpcoming().getTotalPages());
        assertEquals(12L, result.getPast().getTotalElements());
        assertEquals(2, result.getPast().getTotalPages());
    }

    @Test
    public void testFindMyRegistrationsAPageBeyondTheLastOfItsGroupShowsThatGroupsLastPage() {
        // Arrange: 12 proximas (2 paginas) y 3 pasadas (1 pagina)
        givenTotals(12, 3);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 99, 99);

        // Assert
        assertEquals(2, result.getUpcoming().getPage());
        assertEquals(1, result.getPast().getPage());
    }

    @Test
    public void testFindMyRegistrationsAPageBelowOneShowsTheFirstPage() {
        // Arrange
        givenTotals(25, 25);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, -3, 0);

        // Assert
        assertEquals(1, result.getUpcoming().getPage());
        assertEquals(1, result.getPast().getPage());
    }

    @Test
    public void testFindMyRegistrationsWithOnlyUpcomingLeavesThePastGroupEmpty() {
        // Arrange
        givenTotals(1, 0);
        givenUpcomingPage(1, UPCOMING_EVENT_ID);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 1, 5);

        // Assert
        assertEquals(1, result.getUpcoming().getContent().size());
        assertTrue(result.getPast().getContent().isEmpty());
        assertEquals(1, result.getPast().getPage());
        assertEquals(0, result.getPast().getTotalPages());
    }

    @Test
    public void testFindMyRegistrationsWithOnlyPastLeavesTheUpcomingGroupEmpty() {
        // Arrange
        givenTotals(0, 1);
        givenPastPage(1, PAST_EVENT_ID);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 5, 1);

        // Assert
        assertTrue(result.getUpcoming().getContent().isEmpty());
        assertEquals(1, result.getPast().getContent().size());
    }

    @Test
    public void testFindMyRegistrationsWithNoRegistrationsReturnsTwoEmptyFirstPages() {
        // Arrange
        givenTotals(0, 0);

        // Act
        final MyRegistrationsView result = registrationService.findMyRegistrations(USER_ID, 5, 5);

        // Assert
        assertTrue(result.getUpcoming().getContent().isEmpty());
        assertTrue(result.getPast().getContent().isEmpty());
        assertEquals(1, result.getUpcoming().getPage());
        assertEquals(1, result.getPast().getPage());
    }

    @Test
    public void testFindMyRegistrationsPairsEachPastRegistrationWithItsEventAndOwnReview() {
        // Arrange: una reseña propia del evento pasado y una senuelo de otro evento
        givenTotals(0, 1);
        final Registration registration = registrationAt(PAST_EVENT_ID);
        final Event event = pastEvent(PAST_EVENT_ID);
        final Review myReview = new Review(event, ModelFixtures.user(USER_ID), 4, "Muy bueno");
        when(mockRegistrationDao.findPastByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registration));
        when(eventService.findByIds(Collections.singletonList(PAST_EVENT_ID))).thenReturn(Collections.singletonList(event));
        when(reviewService.findByEventsAndUser(Collections.singletonList(PAST_EVENT_ID), USER_ID))
                .thenReturn(Collections.singletonList(myReview));

        // Act
        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getPast().getContent().get(0);

        // Assert
        assertSame(registration, entry.getRegistration());
        assertSame(event, entry.getEvent());
        assertSame(myReview, entry.getMyReview());
    }

    @Test
    public void testFindMyRegistrationsLeavesTheReviewEmptyWhenThePastEventHasNone() {
        // Arrange
        givenTotals(0, 1);
        when(mockRegistrationDao.findPastByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registrationAt(PAST_EVENT_ID)));
        when(eventService.findByIds(Collections.singletonList(PAST_EVENT_ID)))
                .thenReturn(Collections.singletonList(pastEvent(PAST_EVENT_ID)));
        when(reviewService.findByEventsAndUser(Collections.singletonList(PAST_EVENT_ID), USER_ID))
                .thenReturn(Collections.emptyList());

        // Act
        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getPast().getContent().get(0);

        // Assert
        assertNull(entry.getMyReview());
    }

    @Test
    public void testFindMyRegistrationsDoesNotAttachReviewsToUpcomingRegistrations() {
        // Arrange: aunque la base tuviera una reseña de ese evento, en las proximas no se busca
        givenTotals(1, 0);
        final Event event = upcomingEvent(UPCOMING_EVENT_ID);
        when(mockRegistrationDao.findUpcomingByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registrationAt(UPCOMING_EVENT_ID)));
        when(eventService.findByIds(Collections.singletonList(UPCOMING_EVENT_ID))).thenReturn(Collections.singletonList(event));
        lenient().when(reviewService.findByEventsAndUser(Collections.singletonList(UPCOMING_EVENT_ID), USER_ID))
                .thenReturn(Collections.singletonList(new Review(event, ModelFixtures.user(USER_ID), 5, "Senuelo")));

        // Act
        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getUpcoming().getContent().get(0);

        // Assert
        assertNull(entry.getMyReview());
    }

    // ---- countByUserId() ----

    @Test
    public void testCountByUserIdDelegatesToTheDao() {
        // Arrange
        when(mockRegistrationDao.countByUserId(USER_ID)).thenReturn(3L);

        // Act
        final long result = registrationService.countByUserId(USER_ID);

        // Assert
        assertEquals(3L, result);
    }

    // ---- cancel() ----

    @Test
    public void testCancelDeletesTheRegistration() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.delete(5)).thenReturn(true);
        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.CANCELLED, result);
    }

    // Un rechazado / revocado no puede "darse de baja": si borrara su fila se saltearia
    // el rechazo (y podria volver a anotarse).
    // Cancelar un pendiente tambien libera su lugar "reservado" frente a la lista de
    // espera (ver reject()/promoteFromWaitlist()) -- pero esa promocion en si ya la
    // prueba su propia sección; aca solo nos importa el contrato de cancel() mismo.
    @Test
    public void testCancelAPendingRegistrationReturnsCancelledPending() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.CANCELLED_PENDING, result);
    }

    @Test
    public void testCancelReturnsFinishedAndKeepsTheRowWhenTheEventAlreadyHappened() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(finishedEvent()));

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert: no se llamo a delete() (sin stub devolveria false y el resultado seria otro)
        assertEquals(UnregisterResult.FINISHED, result);
    }

    @Test
    public void testCancelReturnsEventCancelledAndKeepsTheRowWhenTheEventWasCancelled() {
        // Arrange: el evento no paso, pero se cancelo: no hay nada que cancelar (misma regla, Event.isActive)
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        final Event cancelled = eventWithCapacity(10);
        cancelled.setCancelledAt(new Date());
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.EVENT_CANCELLED, result);
    }

    @Test
    public void testCancelReturnsRejectedAndKeepsTheRowWhenTheRegistrationWasRejected() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.REJECTED, result);
    }

    @Test
    public void testCancelReturnsFinishedEvenIfTheRegistrationWasRejected() {
        // Arrange: gana incluso sobre REJECTED -- mismo orden de prioridad que
        // tenia el controller cuando chequeaba esto antes de buscar la inscripcion.
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        final Event finishedEvent = eventWithCreator(10);
        finishedEvent.setEventDate(new Date(System.currentTimeMillis() - 86_400_000L));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(finishedEvent));

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.FINISHED, result);
    }

    @Test
    public void testCancelReturnsNotFoundWhenTheRegistrationDoesNotExist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        // Act
        final UnregisterResult result = registrationService.cancel(5);

        // Assert
        assertEquals(UnregisterResult.NOT_FOUND, result);
    }

    // ---- cancelOwnRegistration() ----

    @Test
    public void testCancelOwnRegistrationFindsAndCancelsTheUsersRegistration() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5L);
        registration.setUser(ModelFixtures.user(USER_ID));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.of(registration));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        // Act
        final UnregisterResult result = registrationService.cancelOwnRegistration(EVENT_ID, USER_ID);

        // Assert
        assertEquals(UnregisterResult.CANCELLED, result);
    }

    @Test
    public void testCancelOwnRegistrationReturnsNotFoundWhenNotRegistered() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));
        when(mockRegistrationDao.findByEventAndUserId(EVENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        final UnregisterResult result = registrationService.cancelOwnRegistration(EVENT_ID, USER_ID);

        // Assert
        assertEquals(UnregisterResult.NOT_FOUND, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testCancelOwnRegistrationThrowsEventNotFoundExceptionWhenTheEventDoesNotExist() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        registrationService.cancelOwnRegistration(EVENT_ID, USER_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findAccessById() / findAccessByEventAndUserId() ----

    @Test
    public void testFindAccessByIdReturnsTheProjectionTheDaoFound() {
        // Arrange
        when(mockRegistrationDao.findAccessById(5L))
                .thenReturn(Optional.of(new RegistrationAccess(USER_ID, EVENT_ID, RegistrationStatus.PENDING)));

        // Act
        final Optional<RegistrationAccess> result = registrationService.findAccessById(5L);

        // Assert
        assertEquals(Long.valueOf(USER_ID), result.get().getUserId());
        assertEquals(EVENT_ID, result.get().getEventId());
        assertEquals(RegistrationStatus.PENDING, result.get().getStatus());
    }

    @Test
    public void testFindAccessByEventAndUserIdReturnsTheProjectionTheDaoFound() {
        // Arrange
        when(mockRegistrationDao.findAccessByEventAndUserId(EVENT_ID, USER_ID))
                .thenReturn(Optional.of(new RegistrationAccess(USER_ID, EVENT_ID, RegistrationStatus.CONFIRMED)));

        // Act
        final Optional<RegistrationAccess> result = registrationService.findAccessByEventAndUserId(EVENT_ID, USER_ID);

        // Assert
        assertEquals(RegistrationStatus.CONFIRMED, result.get().getStatus());
    }

    // ---- findByEventAndUserId() ----

    @Test
    public void testFindByEventAndUserIdDelegatesToTheDao() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
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

    // ---- findPageByEventId() ----

    @Test
    public void testFindPageByEventIdReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a RegistrationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<Registration> pageTwo = Collections.singletonList(
                new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.countByEventId(EVENT_ID)).thenReturn(25L);
        when(mockRegistrationDao.findByEventId(EVENT_ID, 2, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<Registration> result = registrationService.findPageByEventId(EVENT_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindPageByEventIdClampsAPageBeyondTheLastToTheLastOne() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<Registration> lastPage = Collections.singletonList(
                new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(mockRegistrationDao.countByEventId(EVENT_ID)).thenReturn(25L);
        when(mockRegistrationDao.findByEventId(EVENT_ID, 3, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<Registration> result = registrationService.findPageByEventId(EVENT_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    // ---- findRegistrants() ----

    @Test
    public void testFindRegistrantsReturnsTheEventWithAPageOfItsRegistrations() {
        // Arrange
        final Event event = eventWithCapacity(10);
        final List<Registration> pageOne = Collections.singletonList(
                new Registration(event, null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockRegistrationDao.countByEventId(EVENT_ID)).thenReturn(1L);
        when(mockRegistrationDao.findByEventId(EVENT_ID, 1, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(pageOne);

        // Act
        final Optional<EventRegistrants> result = registrationService.findRegistrants(EVENT_ID, 1);

        // Assert
        assertEquals(EVENT_ID, result.get().getEvent().getId().longValue());
        assertEquals(pageOne, result.get().getRegistrations().getContent());
    }

    @Test
    public void testFindRegistrantsOfAnEventThatDoesNotExistIsEmpty() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<EventRegistrants> result = registrationService.findRegistrants(EVENT_ID, 1);

        // Assert
        assertFalse(result.isPresent());
    }

    // Una inscripcion pasada con ese estado a un evento (cancelado o no), y la reseña (o no) del usuario.
    private RegistrationEntry pastEntry(final RegistrationStatus status, final boolean cancelled, final Review review) {
        final Event event = pastEvent(PAST_EVENT_ID);
        if (cancelled) {
            event.setCancelledAt(new Date());
        }
        givenTotals(0, 1);
        when(mockRegistrationDao.findPastByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(
                        new Registration(event, null, NORMALIZED_EMAIL, NAME, status)));
        when(eventService.findByIds(Collections.singletonList(PAST_EVENT_ID))).thenReturn(Collections.singletonList(event));
        when(reviewService.findByEventsAndUser(Collections.singletonList(PAST_EVENT_ID), USER_ID))
                .thenReturn(review == null ? Collections.<Review>emptyList() : Collections.singletonList(review));
        return registrationService.findMyRegistrations(USER_ID, 1, 1).getPast().getContent().get(0);
    }

    @Test
    public void testAConfirmedPastRegistrationWithoutReviewCanBeReviewed() {
        final RegistrationEntry entry = pastEntry(RegistrationStatus.CONFIRMED, false, null);

        assertTrue(entry.isCanReview());
        assertNull(entry.getNotAttendedReason());
    }

    @Test
    public void testAPastRegistrationAlreadyReviewedCannotBeReviewedAgain() {
        final Review mine = new Review(pastEvent(PAST_EVENT_ID), ModelFixtures.user(USER_ID), 4, "Bien");

        final RegistrationEntry entry = pastEntry(RegistrationStatus.CONFIRMED, false, mine);

        assertFalse(entry.isCanReview());
    }

    @Test
    public void testAConfirmedRegistrationToACancelledPastEventCannotBeReviewed() {
        final RegistrationEntry entry = pastEntry(RegistrationStatus.CONFIRMED, true, null);

        assertFalse(entry.isCanReview());
        assertEquals(NotAttendedReason.CANCELLED_EVENT, entry.getNotAttendedReason());
    }

    @Test
    public void testAWaitlistedPastRegistrationCannotBeReviewedAndSaysWhy() {
        final RegistrationEntry entry = pastEntry(RegistrationStatus.WAITLISTED, false, null);

        assertFalse(entry.isCanReview());
        assertEquals(NotAttendedReason.WAITLISTED, entry.getNotAttendedReason());
    }

    @Test
    public void testAPendingPastRegistrationSaysTheOrganizerNeverAnswered() {
        final RegistrationEntry entry = pastEntry(RegistrationStatus.PENDING, false, null);

        assertFalse(entry.isCanReview());
        assertEquals(NotAttendedReason.PENDING, entry.getNotAttendedReason());
    }

    @Test
    public void testARejectedPastRegistrationCannotBeReviewedAndSaysWhy() {
        final RegistrationEntry entry = pastEntry(RegistrationStatus.REJECTED, false, null);

        assertFalse(entry.isCanReview());
        assertEquals(NotAttendedReason.REJECTED, entry.getNotAttendedReason());
    }

    @Test
    public void testAnUpcomingRegistrationToAnActiveEventCanBeCancelled() {
        givenTotals(1, 0);
        givenUpcomingPage(1, UPCOMING_EVENT_ID);

        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getUpcoming().getContent().get(0);

        assertTrue(entry.isCanCancel());
    }

    @Test
    public void testARejectedUpcomingRegistrationCannotBeCancelled() {
        givenTotals(1, 0);
        final Registration rejected = new Registration(ModelFixtures.event(UPCOMING_EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.REJECTED);
        when(mockRegistrationDao.findUpcomingByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(rejected));
        when(eventService.findByIds(Collections.singletonList(UPCOMING_EVENT_ID)))
                .thenReturn(Collections.singletonList(upcomingEvent(UPCOMING_EVENT_ID)));

        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getUpcoming().getContent().get(0);

        assertFalse(entry.isCanCancel());
    }

    @Test
    public void testAnUpcomingRegistrationToACancelledEventCannotBeCancelled() {
        givenTotals(1, 0);
        final Event cancelled = upcomingEvent(UPCOMING_EVENT_ID);
        cancelled.setEventDate(new Date(System.currentTimeMillis() + ONE_DAY_MILLIS));
        cancelled.setCancelledAt(new Date());
        when(mockRegistrationDao.findUpcomingByUserId(eq(USER_ID), any(Date.class), eq(1), eq(RegistrationServiceImpl.PAGE_SIZE)))
                .thenReturn(Collections.singletonList(registrationAt(UPCOMING_EVENT_ID)));
        when(eventService.findByIds(Collections.singletonList(UPCOMING_EVENT_ID)))
                .thenReturn(Collections.singletonList(cancelled));

        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getUpcoming().getContent().get(0);

        assertFalse(entry.isCanCancel());
    }

    @Test
    public void testAnUpcomingRegistrationCanNeitherBeReviewedNorHasANotAttendedReason() {
        givenTotals(1, 0);
        givenUpcomingPage(1, UPCOMING_EVENT_ID);

        final RegistrationEntry entry = registrationService.findMyRegistrations(USER_ID, 1, 1).getUpcoming().getContent().get(0);

        assertFalse(entry.isCanReview());
        assertNull(entry.getNotAttendedReason());
    }

    // ---- findPendingSummary() ----

    @Test
    public void testFindPendingSummaryHoldsThePendingRegistrationsTheFreeSpotsAndWhetherItIsFull() {
        // Arrange: cupo 3, 2 confirmados -> queda 1 lugar y no esta lleno
        final List<Registration> pending = Collections.singletonList(
                new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING));
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID, 1, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(pending);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(3)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final PendingRegistrations result = registrationService.findPendingSummary(EVENT_ID, 1);

        // Assert
        assertEquals(pending, result.getRegistrations());
        assertEquals(1L, result.getTotalPending());
        assertEquals(Integer.valueOf(1), result.getAvailableSpots());
        assertFalse(result.isFull());
    }

    @Test
    public void testFindPendingSummaryIsFullWhenThereAreNoSpotsLeft() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final PendingRegistrations result = registrationService.findPendingSummary(EVENT_ID, 1);

        // Assert
        assertEquals(Integer.valueOf(0), result.getAvailableSpots());
        assertTrue(result.isFull());
    }

    @Test
    public void testFindPendingSummaryHasNoLimitWhenTheEventHasNoCapacity() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final PendingRegistrations result = registrationService.findPendingSummary(EVENT_ID, 1);

        // Assert
        assertNull(result.getAvailableSpots());
        assertFalse(result.isFull());
    }

    @Test
    public void testFindPendingSummaryReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 pendientes, de a RegistrationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<Registration> pageTwo = Collections.singletonList(
                new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING));
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(25L);
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID, 2, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final PendingRegistrations result = registrationService.findPendingSummary(EVENT_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getRegistrations());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalPending());
    }

    @Test
    public void testFindPendingSummaryClampsAPageBeyondTheLastToTheLastOne() {
        // Arrange: 25 pendientes -> la ultima pagina es la 3
        final List<Registration> lastPage = Collections.singletonList(
                new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING));
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(25L);
        when(mockRegistrationDao.findPendingByEvent(EVENT_ID, 3, RegistrationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        // Act
        final PendingRegistrations result = registrationService.findPendingSummary(EVENT_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getRegistrations());
    }

    // ---- withRegistrationStats() ----

    private static final long APPROVAL_EVENT_ID = 11;
    private static final long PLAIN_EVENT_ID = 12;
    private static final long CANCELLED_APPROVAL_EVENT_ID = 13;

    private static Event statsEvent(final long id, final boolean requiresApproval, final boolean cancelled) {
        final Event event = new Event();
        event.setId(id);
        event.setRequiresApproval(requiresApproval);
        event.setEventDate(new Date(System.currentTimeMillis() + ONE_DAY_MILLIS));
        if (cancelled) {
            event.setCancelledAt(new Date());
        }
        return event;
    }

    // Tres eventos de la pagina: uno que pide aprobacion, uno comun y uno que pide aprobacion pero esta cancelado. El dao
    // devuelve los conteos solo para los ids que el service le pide: pendientes de los que piden aprobacion y
    // confirmados de los que no estan cancelados.
    private PageResult<Event> statsPage() {
        final Map<Long, Long> pending = new HashMap<>();
        pending.put(APPROVAL_EVENT_ID, 4L);
        pending.put(CANCELLED_APPROVAL_EVENT_ID, 1L);
        when(mockRegistrationDao.countByEventsAndStatus(
                Arrays.asList(APPROVAL_EVENT_ID, CANCELLED_APPROVAL_EVENT_ID), RegistrationStatus.PENDING))
                .thenReturn(pending);
        final Map<Long, Long> confirmed = new HashMap<>();
        confirmed.put(APPROVAL_EVENT_ID, 2L);
        confirmed.put(PLAIN_EVENT_ID, 7L);
        when(mockRegistrationDao.countByEventsAndStatus(
                Arrays.asList(APPROVAL_EVENT_ID, PLAIN_EVENT_ID), RegistrationStatus.CONFIRMED))
                .thenReturn(confirmed);
        return new PageResult<>(Arrays.asList(statsEvent(APPROVAL_EVENT_ID, true, false),
                statsEvent(PLAIN_EVENT_ID, false, false), statsEvent(CANCELLED_APPROVAL_EVENT_ID, true, true)),
                3L, 1, 12);
    }

    @Test
    public void testWithRegistrationStatsCountsPendingOnlyForEventsThatRequireApproval() {
        // Arrange
        final PageResult<Event> page = statsPage();

        // Act
        final PageResult<EventWithRegistrationStats> result = registrationService.withRegistrationStats(page);

        // Assert
        assertEquals(Long.valueOf(4), result.getContent().get(0).getPendingCount());
        assertNull(result.getContent().get(1).getPendingCount());
        assertEquals(Long.valueOf(1), result.getContent().get(2).getPendingCount());
    }

    @Test
    public void testWithRegistrationStatsCountsConfirmedOnlyForEventsThatAreNotCancelled() {
        // Arrange
        final PageResult<Event> page = statsPage();

        // Act
        final PageResult<EventWithRegistrationStats> result = registrationService.withRegistrationStats(page);

        // Assert
        assertEquals(2L, result.getContent().get(0).getConfirmedCount());
        assertEquals(7L, result.getContent().get(1).getConfirmedCount());
        assertEquals(0L, result.getContent().get(2).getConfirmedCount());
    }

    @Test
    public void testWithRegistrationStatsKeepsTheEventsAndThePageInformation() {
        // Arrange
        final PageResult<Event> page = statsPage();

        // Act
        final PageResult<EventWithRegistrationStats> result = registrationService.withRegistrationStats(page);

        // Assert
        assertEquals(PLAIN_EVENT_ID, result.getContent().get(1).getEvent().getId().longValue());
        assertEquals(3L, result.getTotalElements());
        assertEquals(1, result.getPage());
    }

    @Test
    public void testWithRegistrationStatsOfAnEmptyPageIsEmpty() {
        // Act
        final PageResult<EventWithRegistrationStats> result =
                registrationService.withRegistrationStats(new PageResult<>(Collections.emptyList(), 0L, 1, 12));

        // Assert
        assertTrue(result.getContent().isEmpty());
        assertEquals(1, result.getPage());
    }

    // ---- approve() ----

    private static Registration pendingRegistration(final long id, final String email) {
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, email, NAME, RegistrationStatus.PENDING);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testApproveConfirmsAPendingRegistration() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.APPROVED, result.getStatus());
        // Sin tope de cupo el evento nunca se llena: no hay nada que anunciar.
        assertFalse(result.isNotificationSent());
    }

    // Con el evento cancelado no se acepta a nadie (ni sale el mail de "confirmado").
    @Test
    public void testApproveReturnsNotFoundWhenTheEventIsCancelled() {
        // Arrange
        final Event cancelled = eventRequiringApproval(null);
        cancelled.setCancelledAt(new Date());
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.NOT_FOUND, result.getStatus());
    }

    // Idem: un evento que ya paso tampoco acepta a nadie.
    @Test
    public void testApproveReturnsNotFoundWhenTheEventAlreadyHappened() {
        // Arrange
        final Event past = eventRequiringApproval(null);
        past.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(past));

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.NOT_FOUND, result.getStatus());
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationIsNotPending() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(5L);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.NOT_FOUND, result.getStatus());
    }

    @Test
    public void testApproveReturnsNotFoundWhenTheRegistrationDoesNotExist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.NOT_FOUND, result.getStatus());
    }

    // El organizador acepta hasta que se llena el cupo, y ni un pedido mas.
    @Test
    public void testApproveReturnsFullAndChangesNothingWhenThereIsNoRoomLeft() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.FULL, result.getStatus());
    }

    // Las invitaciones por mail tambien ocupan lugar: no se puede aceptar a nadie
    // en el lugar que le prometimos a alguien invitado.
    @Test
    public void testApproveReturnsFullWhenTheLastSpotIsHeldByAnInvitation() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.FULL, result.getStatus());
    }

    // Cuando esta aceptacion ocupa el ultimo lugar, el resto de los pendientes ya
    // no tiene a donde entrar: pasan solos a la lista de espera.
    @Test
    public void testApproveTakingTheLastSpotMovesTheRemainingPendingToTheWaitlist() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        // Antes de aceptar quedaba 1 lugar (1 confirmado); despues, 2 de 2.
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L, 2L);
        when(mockRegistrationDao.findPendingIdsByEvent(EVENT_ID)).thenReturn(Collections.singletonList(6L));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.APPROVED, result.getStatus());
        // Esta aceptacion ocupo el ultimo lugar: se disparo notifyEventFull().
        // Se confirma por el valor de retorno, no espiando el mock.
        assertTrue(result.isNotificationSent());
    }

    @Test
    public void testApproveWithSpotsStillLeftKeepsTheOtherRequestsPending() {
        // Arrange
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(1L, 2L);
        // Hay otro pedido pendiente: como todavia hay lugar, no debe pasar a la lista de espera.
        lenient().when(mockRegistrationDao.findPendingIdsByEvent(EVENT_ID)).thenReturn(Collections.singletonList(6L));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.CONFIRMED)).thenReturn(true);
        // Act
        final ApprovalResult result = registrationService.approve(5);

        // Assert
        assertEquals(ApprovalResult.Status.APPROVED, result.getStatus());
        // Quedan lugares (2 de 5): no se lleno, no se aviso.
        assertFalse(result.isNotificationSent());
    }

    // ---- reject() ----

    @Test
    public void testRejectMarksAPendingRegistrationAsRejected() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5L);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(null)));

        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        final boolean result = registrationService.reject(5);

        // Assert
        assertTrue(result);
    }

    // En un evento con aprobacion, un pendiente tiene "reservado" un lugar frente a la lista
    // de espera. Al rechazarlo, ese lugar se libera y promueve al primero de la lista -- pero
    // esa promocion en si ya la prueba su propia sección; aca solo nos importa el contrato
    // de reject() mismo (que la fila en cuestion haya pasado a REJECTED).
    @Test
    public void testRejectReturnsTrueWhenTheEventRequiresApproval() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.PENDING);
        registration.setId(5L);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(2)));
        when(mockRegistrationDao.updateStatus(5, RegistrationStatus.REJECTED)).thenReturn(true);

        // Act
        final boolean result = registrationService.reject(5);

        // Assert
        assertTrue(result);
    }

    // Idem approve(): con el evento cancelado no se rechaza a nadie.
    @Test
    public void testRejectReturnsFalseWhenTheEventIsCancelled() {
        // Arrange
        final Event cancelled = eventRequiringApproval(null);
        cancelled.setCancelledAt(new Date());
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final boolean result = registrationService.reject(5);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRejectReturnsFalseWhenTheEventAlreadyHappened() {
        // Arrange
        final Event past = eventRequiringApproval(null);
        past.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(pendingRegistration(5, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(past));

        // Act
        final boolean result = registrationService.reject(5);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRejectReturnsFalseWhenTheRegistrationIsNotPending() {
        // Arrange
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, NORMALIZED_EMAIL, NAME, RegistrationStatus.WAITLISTED);
        registration.setId(5L);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        // Act
        final boolean result = registrationService.reject(5);

        // Assert
        assertFalse(result);
    }

    // ---- revoke() ----

    private static Registration confirmedRegistration(final long id, final String email) {
        final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null, email, NAME, RegistrationStatus.CONFIRMED);
        registration.setId(id);
        return registration;
    }

    @Test
    public void testRevokeMarksAConfirmedRegistrationAsRejectedAndTellsThePerson() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(10)));

        when(mockRegistrationDao.updateStatus(7, RegistrationStatus.REJECTED)).thenReturn(true);
        // Act
        final boolean result = registrationService.revoke(7);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRevokeReturnsFalseWhenTheRegistrationIsNotConfirmed() {
        // Arrange
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(pendingRegistration(7, NORMALIZED_EMAIL)));

        // Act
        final boolean result = registrationService.revoke(7);

        // Assert
        assertFalse(result);
    }

    // Idem approve(): con el evento cancelado no se revoca a nadie.
    @Test
    public void testRevokeReturnsFalseWhenTheEventIsCancelled() {
        // Arrange
        final Event cancelled = eventWithCreator(10);
        cancelled.setCancelledAt(new Date());
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final boolean result = registrationService.revoke(7);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRevokeReturnsFalseWhenTheEventAlreadyHappened() {
        // Arrange
        final Event past = eventWithCreator(10);
        past.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        when(mockRegistrationDao.findById(7)).thenReturn(Optional.of(confirmedRegistration(7, NORMALIZED_EMAIL)));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(past));

        // Act
        final boolean result = registrationService.revoke(7);

        // Assert
        assertFalse(result);
    }

    // ---- promoteFromWaitlist() ----

    @Test
    public void testPromoteConfirmsAsManyWaitlistedPeopleAsFreeSpotsInArrivalOrder() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(3L);
        // Los 2 primeros de la lista de espera (solo sus ids: no se cargan las inscripciones).
        when(mockRegistrationDao.findNextWaitlistedIds(EVENT_ID, 2)).thenReturn(Arrays.asList(1L, 2L));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(2, promoted);
    }

    // Las invitaciones a un evento privado reservan lugar: no se le da a la
    // lista de espera el lugar que esta prometido a alguien invitado.
    // En un evento que requiere confirmacion, quien sale de la lista de espera pasa a
    // PENDING (no a CONFIRMED): el organizador todavia lo tiene que aceptar.
    @Test
    public void testPromoteMovesWaitlistedToPendingWhenTheEventRequiresApproval() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(0L);
        when(mockRegistrationDao.findNextWaitlistedIds(EVENT_ID, 1)).thenReturn(Collections.singletonList(8L));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
    }

    // Un lugar libre que ya tiene un pedido pendiente esperando respuesta no se le
    // "regala" tambien a la lista de espera.
    @Test
    public void testPromoteToPendingDiscountsRequestsAlreadyWaitingForAnAnswer() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventRequiringApproval(5)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(4L);
        when(mockRegistrationDao.countPending(EVENT_ID)).thenReturn(1L);
        // Hay alguien esperando: si el service ignorara el pendiente, lo promoveria.
        lenient().when(mockRegistrationDao.findNextWaitlistedIds(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(8L));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    @Test
    public void testPromoteRespectsSpotsHeldByUnusedInvitations() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(5)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(5L);
        // Hay alguien esperando: si el service ignorara las invitaciones, lo promoveria.
        lenient().when(mockRegistrationDao.findNextWaitlistedIds(anyLong(), anyInt()))
                .thenReturn(Collections.singletonList(8L));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    @Test
    public void testPromoteDoesNothingWhenTheEventIsFull() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(2)));
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(2L);

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
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(0, promoted);
    }

    // Si el organizador saca el limite de cupo, entra toda la lista de espera.
    @Test
    public void testPromoteConfirmsTheWholeWaitlistWhenTheEventBecomesUnlimited() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCreator(null)));
        when(mockRegistrationDao.findNextWaitlistedIds(EVENT_ID, Integer.MAX_VALUE))
                .thenReturn(Collections.singletonList(1L));

        // Act
        final int promoted = registrationService.promoteFromWaitlist(EVENT_ID);

        // Assert
        assertEquals(1, promoted);
    }
}
