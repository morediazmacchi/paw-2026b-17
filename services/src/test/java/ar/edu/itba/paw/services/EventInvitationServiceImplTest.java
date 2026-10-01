package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventInvitationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final long CREATOR_ID = 20;
    private static final long OTHER_ID = 30;
    private static final String INVITEE_EMAIL = "  Invitado@Mail.com  ";
    private static final String NORMALIZED_INVITEE_EMAIL = "invitado@mail.com";

    @Mock
    private EventInvitationDao mockInvitationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private NotificationService mockNotificationService;

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

    // Enlace optimista del Grupo B (ver EventInvitationServiceImpl.invite()).
    @Mock
    private UserService mockUserService;

    @InjectMocks
    private EventInvitationServiceImpl invitationService;

    private static Event privateEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setVisibility(EventVisibility.PRIVATE);
        return event;
    }

    private static Event publicEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setVisibility(EventVisibility.PUBLIC);
        return event;
    }

    // create() es void: se captura la invitacion persistida con doAnswer()
    // sobre el stub, nunca con Mockito.verify()+ArgumentCaptor.
    private EventInvitation[] captureCreatedInvitation() {
        final EventInvitation[] created = new EventInvitation[1];
        doAnswer(invocation -> {
            created[0] = invocation.getArgument(0);
            return created[0];
        }).when(mockInvitationDao).create(any(EventInvitation.class));
        return created;
    }

    // ---- invite() ----

    @Test
    public void testInviteCreatesTheInvitationWithTheNormalizedEmail() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        final EventInvitation[] created = captureCreatedInvitation();

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
        assertEquals(NORMALIZED_INVITEE_EMAIL, created[0].getEmail());
        assertEquals(EVENT_ID, created[0].getEventId());
        assertEquals(CREATOR_EMAIL, created[0].getInvitedBy());
    }

    @Test
    public void testInviteLinksTheInviteesUserIdWhenTheyAlreadyHaveAnAccount() {
        // Arrange
        final long inviteeId = 42;
        final User invitee = new User(NORMALIZED_INVITEE_EMAIL, "Invitado");
        invitee.setId(inviteeId);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.of(invitee));
        final EventInvitation[] created = captureCreatedInvitation();

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(Long.valueOf(inviteeId), created[0].getUserId());
    }

    @Test
    public void testInviteLeavesTheUserIdNullWhenTheInviteeHasNoAccountYet() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        final EventInvitation[] created = captureCreatedInvitation();

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertNull(created[0].getUserId());
    }

    @Test
    public void testInviteSendsTheNotification() {
        // Arrange
        final Event event = privateEvent();
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        final boolean[] notified = {false};
        doAnswer(invocation -> {
            notified[0] = true;
            return null;
        }).when(mockNotificationService).notifyEventInvitation(any(), any());

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertTrue(notified[0]);
    }

    @Test
    public void testInviteLeavesAMessageInTheInviteesInbox() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        final String[] inboxRecipient = new String[1];
        doAnswer(invocation -> {
            inboxRecipient[0] = invocation.getArgument(0);
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(NORMALIZED_INVITEE_EMAIL, inboxRecipient[0]);
    }

    @Test
    public void testInviteReturnsAlreadyInvitedWhenThatEmailIsAlreadyInvited() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL)));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.ALREADY_INVITED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventIsNotPrivate() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(publicEvent()));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheRequesterIsNotTheCreator() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, OTHER_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventDoesNotExist() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    // ---- isInvited() ----
    // Es lo que decide si un evento privado se puede ver: el id es el de la sesion.

    @Test
    public void testIsInvitedReturnsTrueForTheInvitedUserId() {
        // Arrange
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, OTHER_ID))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL)));

        // Act
        final boolean result = invitationService.isInvited(EVENT_ID, OTHER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsInvitedReturnsFalseForAnotherUserId() {
        // Arrange
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = invitationService.isInvited(EVENT_ID, OTHER_ID);

        // Assert
        assertFalse(result);
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        final List<EventInvitation> invitations = Collections.singletonList(
                new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.findByEvent(EVENT_ID)).thenReturn(invitations);

        // Act
        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, CREATOR_ID);

        // Assert
        assertEquals(invitations, result);
    }

    @Test
    public void testFindByEventReturnsEmptyWhenAskedBySomeoneElse() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        // Si quien pregunta no es el dueño, la implementacion no deberia ni
        // consultar al dao: se stubea para que reviente si se llega a invocar.
        lenient().when(mockInvitationDao.findByEvent(EVENT_ID))
                .thenThrow(new AssertionError("no deberia consultar el dao si quien pregunta no es el dueño"));

        // Act
        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, OTHER_ID);

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindByEventReturnsEmptyWhenTheEventDoesNotExist() {
        // Arrange
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, CREATOR_ID);

        // Assert
        assertTrue(result.isEmpty());
    }

    // ---- cupo: cada invitacion reserva un lugar ----

    @Test
    public void testInviteReturnsFullWhenConfirmedPlusInvitationsReachTheCapacity() {
        // Arrange
        final Event event = privateEvent();
        event.setMaxCapacity(3);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.FULL, result);
    }

    @Test
    public void testInviteStillWorksWhileThereIsRoomLeft() {
        // Arrange
        final Event event = privateEvent();
        event.setMaxCapacity(3);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
    }
}
