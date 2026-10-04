package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventAccessServiceImplTest {

    private static final long EVENT_ID = 7L;
    private static final long CREATOR_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private EventInvitationService eventInvitationService;

    private EventAccessServiceImpl accessService;

    @Before
    public void setUp() {
        accessService = new EventAccessServiceImpl(eventService, registrationService, eventInvitationService);
    }

    private static Event event(final EventVisibility visibility) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorId(CREATOR_ID);
        event.setVisibility(visibility);
        return event;
    }

    private static User user(final long id) {
        final User user = new User();
        user.setId(id);
        return user;
    }

    // ---- canView(event, user) ----

    @Test
    public void testCanViewAPublicEventIsTrueEvenWithoutSession() {
        // Act
        final boolean result = accessService.canView(event(EventVisibility.PUBLIC), null);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPrivateEventWithoutSessionIsFalse() {
        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), null);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCanViewAPrivateEventIsTrueForARegisteredUser() {
        // Arrange
        when(registrationService.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(new Registration()));

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPrivateEventIsTrueForAnInvitedUser() {
        // Arrange
        when(registrationService.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(true);

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPrivateEventIsFalseForAStranger() {
        // Arrange
        when(registrationService.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(false);

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertFalse(result);
    }

    // ---- requireViewable() ----

    @Test
    public void testRequireViewableDoesNotThrowWhenTheEventIsViewable() {
        // Act
        accessService.requireViewable(event(EventVisibility.PUBLIC), null, Optional.empty());

        // Assert: si no tiro nada, el test ya paso.
    }

    @Test(expected = EventNotFoundException.class)
    public void testRequireViewableThrowsEventNotFoundExceptionWhenItIsNot() {
        // Act
        accessService.requireViewable(event(EventVisibility.PRIVATE), user(OTHER_ID), Optional.empty());

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- getViewableEvent() ----

    @Test
    public void testGetViewableEventReturnsThePublicEvent() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final Event result = accessService.getViewableEvent(EVENT_ID, user(OTHER_ID));

        // Assert
        assertSame(event, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetViewableEventWhenTheEventDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        accessService.getViewableEvent(EVENT_ID, user(OTHER_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetViewableEventOfAPrivateEventTheUserCannotSeeThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EventVisibility.PRIVATE)));
        when(registrationService.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(false);

        // Act
        accessService.getViewableEvent(EVENT_ID, user(OTHER_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- getOwnedEvent() ----

    @Test
    public void testGetOwnedEventReturnsTheEventToItsCreator() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final Event result = accessService.getOwnedEvent(EVENT_ID, user(CREATOR_ID));

        // Assert
        assertSame(event, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetOwnedEventWhenTheUserIsNotTheCreatorThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EventVisibility.PUBLIC)));

        // Act
        accessService.getOwnedEvent(EVENT_ID, user(OTHER_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetOwnedEventWhenTheEventDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        accessService.getOwnedEvent(EVENT_ID, user(CREATOR_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- getOwnedPrivateEvent() ----

    @Test
    public void testGetOwnedPrivateEventReturnsThePrivateEventToItsCreator() {
        // Arrange
        final Event event = event(EventVisibility.PRIVATE);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final Event result = accessService.getOwnedPrivateEvent(EVENT_ID, user(CREATOR_ID));

        // Assert
        assertSame(event, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetOwnedPrivateEventWhenTheEventIsPublicThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EventVisibility.PUBLIC)));

        // Act
        accessService.getOwnedPrivateEvent(EVENT_ID, user(CREATOR_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetOwnedPrivateEventWhenTheUserIsNotTheCreatorThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EventVisibility.PRIVATE)));

        // Act
        accessService.getOwnedPrivateEvent(EVENT_ID, user(OTHER_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetOwnedPrivateEventWhenTheEventDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        accessService.getOwnedPrivateEvent(EVENT_ID, user(CREATOR_ID));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }
}
