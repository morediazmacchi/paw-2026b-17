package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
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
    // Avisos y bandeja: fakes escritos a mano que anotan lo que les piden.
    private final RecordingNotificationService notifications = new RecordingNotificationService();
    private final RecordingInboxService inbox = new RecordingInboxService();

    // Enlace optimista del Grupo B (ver EventInvitationServiceImpl.invite()).
    @Mock
    private UserService mockUserService;

    private EventInvitationServiceImpl invitationService;

    @Before
    public void setUp() {
        invitationService = new EventInvitationServiceImpl(
                mockInvitationDao, mockEventDao, mockRegistrationDao, notifications, inbox, mockUserService);
    }

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

    // La invitacion que se guardo es la misma que el service le pasa al aviso
    // (notifyEventInvitation(event, invitation)): se lee de ahi.
    private EventInvitation invitationNotified() {
        return (EventInvitation) notifications.argsOf("notifyEventInvitation").get(0)[1];
    }

    // ---- invite() ----

    @Test
    public void testInviteCreatesTheInvitationWithTheNormalizedEmail() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        // El dao recibe la invitacion ya normalizada y con quien invito: si no, este stub no matchea y devuelve null.
        when(mockInvitationDao.create(argThat(invitation ->
                NORMALIZED_INVITEE_EMAIL.equals(invitation.getEmail()) && invitation.getEventId() == EVENT_ID
                        && Long.valueOf(CREATOR_ID).equals(invitation.getInvitedByUserId()))))
                .thenReturn(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
        assertEquals(NORMALIZED_INVITEE_EMAIL, invitationNotified().getEmail());
        assertEquals(EVENT_ID, invitationNotified().getEventId());
        assertEquals(CREATOR_EMAIL, invitationNotified().getInvitedBy());
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
        when(mockInvitationDao.create(argThat(invitation -> Long.valueOf(inviteeId).equals(invitation.getUserId()))))
                .thenReturn(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
        assertEquals(Long.valueOf(inviteeId), invitationNotified().getUserId());
    }

    @Test
    public void testInviteLeavesTheUserIdNullWhenTheInviteeHasNoAccountYet() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockInvitationDao.create(argThat(invitation -> invitation.getUserId() == null)))
                .thenReturn(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
        assertNull(invitationNotified().getUserId());
    }

    @Test
    public void testInviteSendsTheNotification() {
        // Arrange
        final Event event = privateEvent();
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(1, notifications.count("notifyEventInvitation"));
        assertSame(event, notifications.argsOf("notifyEventInvitation").get(0)[0]);
    }

    @Test
    public void testInviteLeavesAMessageInTheInviteesInbox() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(Collections.singletonList(NORMALIZED_INVITEE_EMAIL), inbox.recipients());
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
        // El dao SI tiene una invitacion: si el service la devolviera para cualquiera,
        // el resultado no estaria vacio.
        lenient().when(mockInvitationDao.findByEvent(EVENT_ID))
                .thenReturn(Collections.singletonList(
                        new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL)));

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

    // ---- findByUser() ----

    @Test
    public void testFindByUserReturnsTheInvitationsTheUserReceived() {
        // Arrange
        final List<EventInvitation> invitations = Collections.singletonList(
                new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.findByUserId(OTHER_ID)).thenReturn(invitations);

        // Act
        final List<EventInvitation> result = invitationService.findByUser(OTHER_ID);

        // Assert
        assertEquals(invitations, result);
    }
}
