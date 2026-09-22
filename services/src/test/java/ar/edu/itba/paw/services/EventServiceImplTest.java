package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long CATEGORY_ID = 7;
    private static final String CREATOR_EMAIL = "creador@mail.com";

    private static final String OTHER_EMAIL = "otro@mail.com";

    @Mock
    private EventDao mockEventDao;
    @Mock
    private UserService mockUserService;
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;
    @Mock
    private RegistrationDao mockRegistrationDao;

    @InjectMocks
    private EventServiceImpl eventService;

    private static Event someEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCategories(Collections.singletonList(new EventCategoryInfo(CATEGORY_ID)));
        event.setCreatorEmail(CREATOR_EMAIL);
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
    public void testCreateStillReturnsTheEventWhenUserServiceFails() {
        when(mockEventDao.create(any(Event.class))).thenReturn(someEvent());
        when(mockUserService.findOrCreate(anyString(), any()))
                .thenThrow(new RuntimeException("no se pudo crear el user"));

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
    public void testFindByCreatorEmailReturnsWhatTheDaoReturns() {
        final List<Event> events = Collections.singletonList(someEvent());
        when(mockEventDao.findByCreatorEmail(CREATOR_EMAIL)).thenReturn(events);

        final List<Event> result = eventService.findByCreatorEmail(CREATOR_EMAIL);

        assertEquals(1, result.size());
        assertEquals(CREATOR_EMAIL, result.get(0).getCreatorEmail());
    }

    @Test
    public void testFindByCreatorEmailNormalizesTheEmail() {
        when(mockEventDao.findByCreatorEmail(CREATOR_EMAIL)).thenReturn(Collections.singletonList(someEvent()));

        eventService.findByCreatorEmail("  CREADOR@Mail.com ");

        org.mockito.Mockito.verify(mockEventDao).findByCreatorEmail(CREATOR_EMAIL);
    }

    @Test
    public void testUpdateSucceedsAndReturnsUpdated() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(20);
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockEventDao).update(changes);
    }

    @Test
    public void testUpdateWhenCallerIsNotTheCreatorReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), OTHER_EMAIL);

        assertEquals(EventUpdateResult.NOT_FOUND, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenTheEventDoesNotExistReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final EventUpdateResult result = eventService.update(EVENT_ID, someEvent(), CREATOR_EMAIL);

        assertEquals(EventUpdateResult.NOT_FOUND, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenNewCapacityIsBelowConfirmedReturnsCapacityTooLow() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        assertEquals(EventUpdateResult.CAPACITY_TOO_LOW, result);
        verify(mockEventDao, never()).update(any(Event.class));
    }

    @Test
    public void testUpdateWhenNewCapacityEqualsConfirmedSucceeds() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(5);
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        assertEquals(EventUpdateResult.UPDATED, result);
    }

    @Test
    public void testUpdateWhenNewCapacityIsNullSucceedsWithoutCheckingConfirmed() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(null);

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockRegistrationDao, never()).countConfirmed(EVENT_ID);
    }

    @Test
    public void testUpdateSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                Registration.STATUS_CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                Registration.STATUS_WAITLISTED);
        when(mockRegistrationDao.findByEventId(EVENT_ID)).thenReturn(Arrays.asList(confirmed, waitlisted));

        final EventUpdateResult result = eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        assertEquals(EventUpdateResult.UPDATED, result);
        verify(mockNotificationService).notifyEventUpdated(changes, confirmed);
        verify(mockNotificationService, never())
                .notifyEventUpdated(any(Event.class), org.mockito.ArgumentMatchers.eq(waitlisted));
    }

    @Test
    public void testUpdateWhenCapacityTooLowDoesNotNotifyAnyone() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Event changes = someEvent();
        changes.setMaxCapacity(3);
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(5L);

        eventService.update(EVENT_ID, changes, CREATOR_EMAIL);

        verify(mockNotificationService, never()).notifyEventUpdated(any(Event.class), any(Registration.class));
    }

    @Test
    public void testCancelSucceedsAndNotifiesOnlyConfirmedRegistrants() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Registration confirmed = new Registration(EVENT_ID, "confirmado@mail.com", "Confirmado",
                Registration.STATUS_CONFIRMED);
        final Registration waitlisted = new Registration(EVENT_ID, "espera@mail.com", "Espera",
                Registration.STATUS_WAITLISTED);
        final Registration pending = new Registration(EVENT_ID, "pendiente@mail.com", "Pendiente",
                Registration.STATUS_PENDING);
        when(mockRegistrationDao.findByEventId(EVENT_ID))
                .thenReturn(Arrays.asList(confirmed, waitlisted, pending));

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_EMAIL);

        assertEquals(EventCancelResult.CANCELLED, result);
        verify(mockEventDao).cancel(EVENT_ID);
        verify(mockNotificationService).notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(confirmed));
        verify(mockNotificationService, never())
                .notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(waitlisted));
        verify(mockNotificationService, never())
                .notifyEventCancelled(any(Event.class), org.mockito.ArgumentMatchers.eq(pending));
    }

    @Test
    public void testCancelWhenCallerIsNotTheCreatorReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final EventCancelResult result = eventService.cancel(EVENT_ID, OTHER_EMAIL);

        assertEquals(EventCancelResult.NOT_FOUND, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
    }

    @Test
    public void testCancelWhenTheEventDoesNotExistReturnsNotFound() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_EMAIL);

        assertEquals(EventCancelResult.NOT_FOUND, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
    }

    @Test
    public void testCancelWhenAlreadyCancelledDoesNotCancelOrNotifyAgain() {
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        final EventCancelResult result = eventService.cancel(EVENT_ID, CREATOR_EMAIL);

        assertEquals(EventCancelResult.ALREADY_CANCELLED, result);
        verify(mockEventDao, never()).cancel(EVENT_ID);
        verify(mockRegistrationDao, never()).findByEventId(EVENT_ID);
    }
}
