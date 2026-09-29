package ar.edu.itba.paw.services;

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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
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

    @Test
    public void testCreateReturnsTheEventThatTheDaoPersisted() {
        when(mockEventDao.create(any(Event.class))).thenReturn(someEvent());

        final Event result = eventService.create(new Event());

        assertEquals(EVENT_ID, result.getId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithInvalidCategoryThrowsIllegalArgumentException() {
        when(mockEventDao.create(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("fk violation on category_id"));

        eventService.create(someEvent());
    }

    @Test
    public void testCreateStillReturnsTheEventWhenCreatorLookupFindsNothing() {
        // El creador ya viene resuelto (creatorId) desde el controller: esto
        // solo puede pasar si esa fila desapareciera justo entre medio. No
        // tiene que romper la creacion del evento, solo el nombre del mail.
        when(mockEventDao.create(any(Event.class))).thenReturn(someEvent());
        when(mockUserService.findById(CREATOR_ID)).thenReturn(Optional.empty());

        final Event result = eventService.create(someEvent());

        assertNotNull(result);
        assertEquals(EVENT_ID, result.getId());
    }

    @Test
    public void testFindByIdReturnsThePresentEvent() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final Optional<Event> result = eventService.findById(EVENT_ID);

        assertTrue(result.isPresent());
        assertEquals(EVENT_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheEventDoesNotExist() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final Optional<Event> result = eventService.findById(EVENT_ID);

        assertFalse(result.isPresent());
    }

    @Test
    public void testFindAllReturnsWhatTheDaoReturns() {
        final List<Event> events = Arrays.asList(someEvent(), someEvent());
        when(mockEventDao.findAll()).thenReturn(events);

        final List<Event> result = eventService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    public void testFindByCategoryReturnsWhatTheDaoReturns() {
        final List<Event> events = Collections.singletonList(someEvent());
        when(mockEventDao.findByCategory(CATEGORY_ID)).thenReturn(events);

        final List<Event> result = eventService.findByCategory(CATEGORY_ID);

        assertEquals(1, result.size());
        assertEquals(CATEGORY_ID, result.get(0).getCategories().get(0).getCategoryId());
    }

    @Test
    public void testFindByCreatorIdReturnsWhatTheDaoReturns() {
        final List<Event> events = Collections.singletonList(someEvent());
        when(mockEventDao.findByCreatorId(CREATOR_ID)).thenReturn(events);

        final List<Event> result = eventService.findByCreatorId(CREATOR_ID);

        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testUpdateSucceedsAndReturnsUpdated() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(20);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockEventDao).update(changes);
    }

    @Test
    public void testUpdateWhenCallerIsNotTheCreatorReturnsNotFound() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), OTHER_ID);

        assertEquals(EventUpdateResult.NOT_FOUND, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenTheEventDoesNotExistReturnsNotFound() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), CREATOR_ID);

        assertEquals(EventUpdateResult.NOT_FOUND, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenNewCapacityIsBelowOccupiedReturnsCapacityTooLow() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        assertEquals(EventUpdateResult.CAPACITY_TOO_LOW, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenNewCapacityEqualsOccupiedSucceeds() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(5);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        assertEquals(EventUpdateResult.UPDATED, result);
    }

    // Si el organizador sube el cupo, los primeros de la lista de espera entran ya.
    @Test
    public void testUpdatePromotesTheWaitlistSoRaisedCapacityIsFilled() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(20);

        eventService.update(EVENT_ID, changes, CREATOR_ID);

        verify(mockRegistrationService).promoteFromWaitlist(EVENT_ID);
    }

    @Test
    public void testUpdateWhenNewCapacityIsNullSucceedsWithoutCheckingOccupied() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(null);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockRegistrationService, never()).occupiedSpots(EVENT_ID);
    }

    @Test
    public void testUpdateSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                RegistrationStatus.WAITLISTED);
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmed, waitlisted));

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_ID);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockNotificationService).notifyEventUpdated(changes, confirmed);
        verify(mockNotificationService, never())
                .notifyEventUpdated(any(Event.class), org.mockito.ArgumentMatchers.eq(waitlisted));
    }

    @Test
    public void testUpdateWhenCapacityTooLowDoesNotNotifyAnyone() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationService.occupiedSpots(EVENT_ID)).thenReturn(5L);

        eventService.update(EVENT_ID, changes, CREATOR_ID);

        verify(mockNotificationService, never()).notifyEventUpdated(any(Event.class), any(Registration.class));
    }

    @Test
    public void testCancelSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                RegistrationStatus.WAITLISTED);
        final Registration pending = new Registration(EVENT_ID, "pendiente@mail.com", "Pendiente",
                RegistrationStatus.PENDING);
        when(mockRegistrationService.findByEventId(EVENT_ID))
                .thenReturn(Arrays.asList(confirmed, waitlisted, pending));

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        assertEquals(EventCancelResult.CANCELLED, result);
        verify(mockEventDao).cancel(EVENT_ID);
        verify(mockNotificationService).notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(confirmed));
        verify(mockNotificationService, never())
                .notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(waitlisted));
        verify(mockNotificationService, never())
                .notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(pending));
    }

    @Test
    public void testCancelTellsEveryoneStillWaitingInTheInboxButNotTheRejected() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                new Registration(EVENT_ID, "confirmado@mail.com", "C", RegistrationStatus.CONFIRMED),
                new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED),
                new Registration(EVENT_ID, "pendiente@mail.com", "P", RegistrationStatus.PENDING),
                new Registration(EVENT_ID, "rechazado@mail.com", "R", RegistrationStatus.REJECTED)));

        eventService.cancel(EVENT_ID, CREATOR_ID);

        verify(mockInboxService).add("confirmado@mail.com", InboxMessageType.EVENT_CANCELLED, EVENT_ID, null, null);
        verify(mockInboxService).add("espera@mail.com", InboxMessageType.EVENT_CANCELLED, EVENT_ID, null, null);
        verify(mockInboxService).add("pendiente@mail.com", InboxMessageType.EVENT_CANCELLED, EVENT_ID, null, null);
        verify(mockInboxService, never()).add(eq("rechazado@mail.com"), any(), any(), any(), any());
    }

    @Test
    public void testUpdateTellsOnlyConfirmedRegistrantsInTheInbox() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(
                new Registration(EVENT_ID, "confirmado@mail.com", "C", RegistrationStatus.CONFIRMED),
                new Registration(EVENT_ID, "espera@mail.com", "E", RegistrationStatus.WAITLISTED)));

        eventService.update(EVENT_ID, someEvent(), CREATOR_ID);

        verify(mockInboxService).add("confirmado@mail.com", InboxMessageType.EVENT_UPDATED, EVENT_ID, null, null);
        verify(mockInboxService, never()).add(eq("espera@mail.com"), any(), any(), any(), any());
    }

    @Test
    public void testCancelWhenCallerIsNotTheCreatorReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final EventCancelResult result = eventService.cancel(EVENT_ID, OTHER_ID);

        assertEquals(EventCancelResult.NOT_FOUND, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
    }

    @Test
    public void testCancelWhenTheEventDoesNotExistReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        assertEquals(EventCancelResult.NOT_FOUND, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
    }

    @Test
    public void testCancelWhenAlreadyCancelledDoesNotCancelOrNotifyAgain() {
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_ID);

        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
        verify(mockRegistrationService, never()).findByEventId(EVENT_ID);
    }

    // ---- removeByAdmin() (moderacion) ----

    @Test
    public void testRemoveByAdminSucceedsRegardlessOfWhoCreatedTheEvent() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        assertEquals(EventCancelResult.CANCELLED, result);
        verify(mockEventDao).removeByAdmin(EVENT_ID, "Contenido inapropiado");
        verify(mockEventDao, never()).cancel(EVENT_ID);
    }

    @Test
    public void testRemoveByAdminNotifiesTheCreatorWithTheReason() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.emptyList());

        eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        verify(mockNotificationService).notifyEventRemovedByAdmin(any(Event.class), eq("Contenido inapropiado"));
        verify(mockInboxService).add(eq(CREATOR_EMAIL), eq(InboxMessageType.EVENT_REMOVED_BY_ADMIN),
                eq(EVENT_ID), any(), eq("Contenido inapropiado"));
    }

    @Test
    public void testRemoveByAdminAlsoNotifiesConfirmedRegistrantsLikeAnyCancellation() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                RegistrationStatus.CONFIRMED);
        when(mockRegistrationService.findByEventId(EVENT_ID)).thenReturn(Collections.singletonList(confirmed));

        eventService.removeByAdmin(EVENT_ID, "Contenido inapropiado");

        verify(mockNotificationService).notifyEventCancelled(any(Event.class), eq(confirmed));
        verify(mockInboxService).add("confirmado@mail.com", InboxMessageType.EVENT_CANCELLED, EVENT_ID, null, null);
    }

    @Test
    public void testRemoveByAdminWhenTheEventDoesNotExistReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        assertEquals(EventCancelResult.NOT_FOUND, result);
        verify(mockEventDao, never()).removeByAdmin(anyLong(), any());
    }

    @Test
    public void testRemoveByAdminWhenAlreadyCancelledDoesNothing() {
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        final EventCancelResult result = eventService.removeByAdmin(EVENT_ID, "motivo");

        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
        verify(mockEventDao, never()).removeByAdmin(anyLong(), any());
    }
}
