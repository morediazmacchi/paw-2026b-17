package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long CATEGORY_ID = 7;
    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final long CREATOR_ID = 42;

    private static final long OTHER_ID = 43;

    @Mock
    private EventDao mockEventDao;
    @Mock
    private UserService mockUserService;
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;
    @Mock
    private RegistrationService mockRegistrationService;

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private EventServiceImpl eventService;

    private static Event someEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCategories(Collections.singletonList(new EventCategoryInfo(CATEGORY_ID)));
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        return event;
    }

    private static EventDTO someDto() {
        final EventDTO dto = new EventDTO();
        dto.setCategories(Collections.singletonList(new EventCategoryInfo(CATEGORY_ID)));
        dto.setCreatorEmail(CREATOR_EMAIL);
        dto.setCreatorId(CREATOR_ID);
        return dto;
    }

    // notifyEventUpdated()/notifyEventCancelled() son void: se captura cada
    // registration con la que se los llamo (con doAnswer() sobre el stub,
    // nunca con Mockito.verify()) para comprobar despues a quienes se avisa.
    private List<Registration> captureUpdateNotifications() {
        final List<Registration> notified = new ArrayList<>();
        doAnswer(invocation -> {
            notified.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyEventUpdated(any(Event.class), any(Registration.class));
        return notified;
    }

    private List<Registration> captureCancelNotifications() {
        final List<Registration> notified = new ArrayList<>();
        doAnswer(invocation -> {
            notified.add(invocation.getArgument(1));
            return null;
        }).when(mockNotificationService).notifyEventCancelled(any(Event.class), any(Registration.class));
        return notified;
    }

    private List<String> captureInboxRecipients() {
        final List<String> recipients = new ArrayList<>();
        doAnswer(invocation -> {
            recipients.add(invocation.getArgument(0));
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());
        return recipients;
    }

    @Test
    public void testCreateReturnsTheEventThatTheDaoPersisted() {
        // Arrange
        when(mockEventDao.create(any(Event.class))).thenReturn(someEvent());

        // Act
        final Event result = eventService.create(new EventDTO());

        // Assert
        assertEquals(EVENT_ID, result.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithInvalidCategoryThrowsIllegalArgumentException() {
        // Arrange
        when(mockEventDao.create(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("fk violation on category_id"));

        // Act
        eventService.create(someDto());

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testCreateStillReturnsTheEventWhenCreatorLookupFindsNothing() {
        // Arrange
        // El creador ya viene resuelto (creatorId) desde el controller: esto
        // solo puede pasar si esa fila desapareciera justo entre medio. No
        // tiene que romper la creacion del evento, solo el nombre del mail.
        when(mockEventDao.create(any(Event.class))).thenReturn(someEvent());
        when(mockUserService.findById(CREATOR_ID)).thenReturn(Optional.empty());

        // Act
        final Event result = eventService.create(someDto());

        // Assert
        assertNotNull(result);
        assertEquals(EVENT_ID, result.getId());
    }

    @Test
    public void testFindByIdReturnsThePresentEvent() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        // Act
        final Optional<Event> result = eventService.findById(EVENT_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(EVENT_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Event> result = eventService.findById(EVENT_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindAllReturnsWhatTheDaoReturns() {
        // Arrange
        final List<Event> events = Arrays.asList(someEvent(), someEvent());
        when(mockEventDao.findAll()).thenReturn(events);

        // Act
        final List<Event> result = eventService.findAll();

        // Assert
        assertEquals(2, result.size());
    }

    @Test
    public void testFindByCategoryReturnsWhatTheDaoReturns() {
        // Arrange
        final List<Event> events = Collections.singletonList(someEvent());
        when(mockEventDao.findByCategory(CATEGORY_ID)).thenReturn(events);

        // Act
        final List<Event> result = eventService.findByCategory(CATEGORY_ID);

        // Assert
        assertEquals(1, result.size());
        assertEquals(CATEGORY_ID, result.get(0).getCategories().get(0).getCategoryId());
    }

    @Test
    public void testFindByCreatorIdReturnsWhatTheDaoReturns() {
        // Arrange
        final List<Event> events = Collections.singletonList(someEvent());
        when(mockEventDao.findByCreatorId(CREATOR_ID)).thenReturn(events);

        // Act
        final List<Event> result = eventService.findByCreatorId(CREATOR_ID);

        // Assert
        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testUpdateSucceedsAndReturnsUpdated() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(20);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.UPDATED, result);
    }

    @Test
    public void testUpdateWhenCallerIsNotTheCreatorReturnsNotFound() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), OTHER_ID);

        // Assert
        assertEquals(EventUpdateResult.NOT_FOUND, result);
    }

    @Test
    public void testUpdateWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.NOT_FOUND, result);
    }

    @Test
    public void testUpdateWhenNewCapacityIsBelowOccupiedReturnsCapacityTooLow() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.CAPACITY_TOO_LOW, result);
    }

    @Test
    public void testUpdateWhenNewCapacityEqualsOccupiedSucceeds() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(5);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.UPDATED, result);
    }

    // Si el organizador sube el cupo, los primeros de la lista de espera entran ya.
    @Test
    public void testUpdatePromotesTheWaitlistSoRaisedCapacityIsFilled() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(20);
        final boolean[] promoted = {false};
        doAnswer(invocation -> {
            promoted[0] = true;
            return 0;
        }).when(mockRegistrationService).promoteFromWaitlist(EVENT_ID);

        // Act
        eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertTrue(promoted[0]);
    }

    @Test
    public void testUpdateWhenNewCapacityIsNullSucceedsWithoutCheckingOccupied() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(null);
        // Si se llegara a consultar occupiedSpots() con cupo null, es un bug:
        // se stubea para que reviente en vez de devolver un valor cualquiera.
        lenient().when(mockRegistrationService.occupiedSpots(EVENT_ID))
                .thenThrow(new AssertionError("no deberia consultar occupiedSpots con cupo null"));

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.UPDATED, result);
    }

    @Test
    public void testUpdateSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                RegistrationStatus.WAITLISTED);
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmed, waitlisted));
        final List<Registration> notified = captureUpdateNotifications();

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.UPDATED, result);
        assertEquals(Collections.singletonList(confirmed), notified);
    }

    @Test
    public void testUpdateWhenCapacityTooLowDoesNotNotifyAnyone() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        // Act
        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        // Assert
        assertEquals(EventUpdateResult.CAPACITY_TOO_LOW, result);
    }

    @Test
    public void testCancelSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                RegistrationStatus.WAITLISTED);
        final Registration pending = new Registration(EVENT_ID, "pendiente@mail.com", "Pendiente",
                RegistrationStatus.PENDING);
        when(mockRegistrationService.findByEventId(EVENT_ID))
                .thenReturn(Arrays.asList(confirmed, waitlisted, pending));
        final List<Registration> notified = captureCancelNotifications();

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventCancelResult.CANCELLED, result);
        assertEquals(Collections.singletonList(confirmed), notified);
    }

    @Test
    public void testCancelTellsEveryoneStillWaitingInTheInboxButNotTheRejected() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                new Registration(EVENT_ID, "confirmado@mail.com", "C", RegistrationStatus.CONFIRMED),
                new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED),
                new Registration(EVENT_ID, "pendiente@mail.com", "P", RegistrationStatus.PENDING),
                new Registration(EVENT_ID, "rechazado@mail.com", "R", RegistrationStatus.REJECTED)));
        final List<String> recipients = captureInboxRecipients();

        // Act
        eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertTrue(recipients.contains("confirmado@mail.com"));
        assertTrue(recipients.contains("espera@mail.com"));
        assertTrue(recipients.contains("pendiente@mail.com"));
        assertFalse(recipients.contains("rechazado@mail.com"));
    }

    @Test
    public void testUpdateTellsOnlyConfirmedRegistrantsInTheInbox() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                new Registration(EVENT_ID, "confirmado@mail.com", "C", RegistrationStatus.CONFIRMED),
                new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED)));
        final List<String> recipients = captureInboxRecipients();

        // Act
        eventService.update(EVENT_ID, someEvent(), CREATOR_ID);

        // Assert
        assertTrue(recipients.contains("confirmado@mail.com"));
        assertFalse(recipients.contains("espera@mail.com"));
    }

    @Test
    public void testCancelWhenCallerIsNotTheCreatorReturnsNotFound() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, OTHER_ID);

        // Assert
        assertEquals(EventCancelResult.NOT_FOUND, result);
    }

    @Test
    public void testCancelWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventCancelResult.NOT_FOUND, result);
    }

    @Test
    public void testCancelWhenAlreadyCancelledDoesNotCancelOrNotifyAgain() {
        // Arrange
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        // Act
        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
    }

    // ---- removeByAdmin() (moderacion) ----

    @Test
    public void testRemoveByAdminSucceedsRegardlessOfWhoCreatedTheEvent() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());
        // removeByAdmin() y cancel() devuelven el mismo CANCELLED: sin capturar
        // cual de los dos se uso, serian indistinguibles para el test.
        final boolean[] calledRemoveByAdmin = {false};
        doAnswer(invocation -> {
            calledRemoveByAdmin[0] = true;
            return null;
        }).when(mockEventDao).removeByAdmin(EVENT_ID, "Contenido inapropiado");
        lenient().doThrow(new AssertionError("removeByAdmin no deberia usar cancel()"))
                .when(mockEventDao).cancel(anyLong());

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        // Assert
        assertEquals(EventCancelResult.CANCELLED, result);
        assertTrue(calledRemoveByAdmin[0]);
    }

    @Test
    public void testRemoveByAdminNotifiesTheCreatorWithTheReason() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());
        final Object[] notifyArgs = new Object[2];
        doAnswer(invocation -> {
            notifyArgs[0] = invocation.getArgument(0);
            notifyArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockNotificationService).notifyEventRemovedByAdmin(any(Event.class), any());
        final List<Object[]> inboxCalls = new ArrayList<>();
        doAnswer(invocation -> {
            inboxCalls.add(new Object[] {invocation.getArgument(0), invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(4)});
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        // Assert
        assertEquals("Contenido inapropiado", notifyArgs[1]);
        final Object[] creatorInboxCall = inboxCalls.stream()
                .filter(call -> CREATOR_EMAIL.equals(call[0]))
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(InboxMessageType.EVENT_REMOVED_BY_ADMIN, creatorInboxCall[1]);
        assertEquals(EVENT_ID, creatorInboxCall[2]);
        assertEquals("Contenido inapropiado", creatorInboxCall[3]);
    }

    @Test
    public void testRemoveByAdminAlsoNotifiesConfirmedRegistrantsLikeAnyCancellation() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.singletonList(confirmed));
        final List<Registration> notified = captureCancelNotifications();
        final List<String> recipients = captureInboxRecipients();

        // Act
        eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        // Assert
        assertEquals(Collections.singletonList(confirmed), notified);
        assertTrue(recipients.contains("confirmado@mail.com"));
    }

    @Test
    public void testRemoveByAdminWhenTheEventDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        // Assert
        assertEquals(EventCancelResult.NOT_FOUND, result);
    }

    @Test
    public void testRemoveByAdminWhenAlreadyCancelledDoesNothing() {
        // Arrange
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        // Act
        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        // Assert
        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
    }

    // ---- findFeatured() / setFeatured() (banner de /eventos) ----

    @Test
    public void testFindFeaturedDelegatesToTheDao() {
        // Arrange
        when(mockEventDao.findFeatured(5)).thenReturn(java.util.Collections.singletonList(someEvent()));

        // Act
        final int size = eventService.findFeatured(5).size();

        // Assert
        assertEquals(1, size);
    }

    @Test
    public void testSetFeaturedDelegatesToTheDao() {
        // Arrange
        // setFeatured() es void: se captura con doAnswer() en vez de con
        // Mockito.verify().
        final Object[] calledWith = new Object[2];
        doAnswer(invocation -> {
            calledWith[0] = invocation.getArgument(0);
            calledWith[1] = invocation.getArgument(1);
            return null;
        }).when(mockEventDao).setFeatured(anyLong(), org.mockito.ArgumentMatchers.anyBoolean());

        // Act
        eventService.setFeatured(EVENT_ID, true);

        // Assert
        assertEquals(EVENT_ID, calledWith[0]);
        assertEquals(true, calledWith[1]);
    }
}
