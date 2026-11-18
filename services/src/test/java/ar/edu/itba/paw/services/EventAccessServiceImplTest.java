package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.exception.EventCancelledException;
import ar.edu.itba.paw.model.exception.EventFinishedException;
import ar.edu.itba.paw.model.exception.EventRejectedException;
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
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setVisibility(visibility);
        // El resto de estos tests son sobre la logica de publico/privado/invitacion,
        // no sobre la revision previa (ver EventApprovalStatus): se asume ya
        // aprobado, como cualquier evento publicado antes de que existiera esta
        // feature. La logica de PENDING/REJECTED tiene sus propios tests abajo.
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        // Todavia no paso (uno pasado no se edita: tiene su propio test abajo).
        event.setEventDate(new java.util.Date(System.currentTimeMillis() + 86_400_000L));
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
    public void testCanViewAPrivateEventIsTrueForAnAdminWhoIsNotInvited() {
        // Arrange
        final User admin = user(7);
        admin.getRoles().add(Role.ADMIN);

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), admin);

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
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(new RegistrationAccess(OTHER_ID, EVENT_ID, RegistrationStatus.REJECTED)));

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPrivateEventIsTrueForAnInvitedUser() {
        // Arrange
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(true);

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPrivateEventIsFalseForAStranger() {
        // Arrange
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(false);

        // Act
        final boolean result = accessService.canView(event(EventVisibility.PRIVATE), user(OTHER_ID));

        // Assert
        assertFalse(result);
    }

    // ---- canView() con la proyeccion EventAccessSummary (la que usa el acceso a imagenes) ----

    @Test
    public void testCanViewAPublicApprovedSummaryIsTrueWithoutSession() {
        assertTrue(accessService.canView(new EventAccessSummary(EVENT_ID, CREATOR_ID, false, true), null));
    }

    @Test
    public void testCanViewAPublicNotApprovedSummaryIsFalseForAStranger() {
        assertFalse(accessService.canView(new EventAccessSummary(EVENT_ID, CREATOR_ID, false, false), user(OTHER_ID)));
    }

    @Test
    public void testCanViewAPrivateSummaryIsFalseWithoutSession() {
        assertFalse(accessService.canView(new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true), null));
    }

    @Test
    public void testCanViewAPrivateSummaryIsTrueForTheCreator() {
        assertTrue(accessService.canView(new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true), user(CREATOR_ID)));
    }

    @Test
    public void testCanViewAPrivateSummaryIsTrueForAnInvitedUser() {
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(true);

        assertTrue(accessService.canView(new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true), user(OTHER_ID)));
    }

    // ---- canView(event, userId, isAdmin): lo que lleva el principal de la sesion ----

    @Test
    public void testCanViewByIdOfAPublicEventIsTrueWithoutSession() {
        assertTrue(accessService.canView(event(EventVisibility.PUBLIC), null, false));
    }

    @Test
    public void testCanViewByIdOfAPrivateEventIsFalseWithoutSession() {
        assertFalse(accessService.canView(event(EventVisibility.PRIVATE), null, false));
    }

    @Test
    public void testCanViewByIdOfAPrivateEventIsTrueForItsCreator() {
        assertTrue(accessService.canView(event(EventVisibility.PRIVATE), CREATOR_ID, false));
    }

    @Test
    public void testCanViewByIdOfAPrivateEventIsTrueForAnAdmin() {
        assertTrue(accessService.canView(event(EventVisibility.PRIVATE), OTHER_ID, true));
    }

    @Test
    public void testCanViewByIdOfAPrivateEventIsTrueForAnInvitedUser() {
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(true);

        assertTrue(accessService.canView(event(EventVisibility.PRIVATE), OTHER_ID, false));
    }

    @Test
    public void testCanViewByIdOfAPrivateEventIsFalseForAStranger() {
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(false);

        assertFalse(accessService.canView(event(EventVisibility.PRIVATE), OTHER_ID, false));
    }

    // ---- getEditableEvent() ----
    // Quien es el creador lo decide @PreAuthorize en el controller (ver EventSecurity): aca solo
    // se prueba la regla de estado.

    @Test
    public void testGetEditableEventReturnsTheEvent() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final Event result = accessService.getEditableEvent(EVENT_ID).get();

        // Assert
        assertSame(event, result);
    }

    @Test(expected = EventCancelledException.class)
    public void testGetEditableEventWhenTheEventIsCancelledThrowsEventCancelledException() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setCancelledAt(new java.util.Date());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        accessService.getEditableEvent(EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventRejectedException.class)
    public void testGetEditableEventWhenTheEventWasRejectedThrowsEventRejectedException() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.REJECTED);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        accessService.getEditableEvent(EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test(expected = EventFinishedException.class)
    public void testGetEditableEventWhenTheEventAlreadyHappenedThrowsEventFinishedException() {
        // Arrange: fue ayer
        final Event event = event(EventVisibility.PUBLIC);
        event.setEventDate(new java.util.Date(System.currentTimeMillis() - 86_400_000L));
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        accessService.getEditableEvent(EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    @Test
    public void testGetEditableEventReturnsAnEventStillPendingApproval() {
        // Arrange: pendiente de aprobacion si se edita (solo el rechazado y el cancelado no)
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        // Act
        final Event result = accessService.getEditableEvent(EVENT_ID).get();

        // Assert
        assertSame(event, result);
    }

    @Test
    public void testGetEditableEventWhenTheEventDoesNotExistIsEmpty() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Event> result = accessService.getEditableEvent(EVENT_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- canRegister() ----

    @Test
    public void testCannotRegisterWithoutASession() {
        // Act
        final boolean result = accessService.canRegister(event(EventVisibility.PUBLIC), null);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testAnyoneWithASessionCanRegisterInAPublicApprovedEvent() {
        // Act
        final boolean result = accessService.canRegister(event(EventVisibility.PUBLIC), OTHER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testTheOrganizerCannotRegisterInTheirOwnEvent() {
        // Act
        final boolean result = accessService.canRegister(event(EventVisibility.PUBLIC), CREATOR_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCannotRegisterInAnEventStillPendingApproval() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        // Act
        final boolean result = accessService.canRegister(event, OTHER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCannotRegisterInAPrivateEventWithoutRegistrationNorInvitation() {
        // Act
        final boolean result = accessService.canRegister(event(EventVisibility.PRIVATE), OTHER_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCanRegisterInAPrivateEventWhenInvited() {
        // Arrange
        when(eventInvitationService.isInvited(EVENT_ID, OTHER_ID)).thenReturn(true);

        // Act
        final boolean result = accessService.canRegister(event(EventVisibility.PRIVATE), OTHER_ID);

        // Assert
        assertTrue(result);
    }

    // ---- canView() con revision previa (ver EventApprovalStatus) ----

    @Test
    public void testCanViewAPendingEventIsFalseForAStranger() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        // Act
        final boolean result = accessService.canView(event, user(OTHER_ID));

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCanViewAPendingEventIsFalseForAnAnonymousUser() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        // Act
        final boolean result = accessService.canView(event, null);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testCanViewAPendingEventIsTrueForItsCreator() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        // Act
        final boolean result = accessService.canView(event, user(CREATOR_ID));

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewAPendingEventIsTrueForAnAdmin() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.PENDING);
        final User admin = user(OTHER_ID);
        admin.getRoles().add(Role.ADMIN);

        // Act
        final boolean result = accessService.canView(event, admin);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testCanViewARejectedEventIsFalseForAStranger() {
        // Arrange
        final Event event = event(EventVisibility.PUBLIC);
        event.setApprovalStatus(EventApprovalStatus.REJECTED);

        // Act
        final boolean result = accessService.canView(event, user(OTHER_ID));

        // Assert
        assertFalse(result);
    }
}
