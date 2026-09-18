package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long CATEGORY_ID = 7;
    private static final String CREATOR_EMAIL = "creador@mail.com";

    @Mock
    private EventDao mockEventDao;
    @Mock
    private UserService mockUserService;
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;

    @InjectMocks
    private EventServiceImpl eventService;

    private static Event someEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCategoryId(CATEGORY_ID);
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
        assertEquals(CATEGORY_ID, result.get(0).getCategoryId());
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
}
