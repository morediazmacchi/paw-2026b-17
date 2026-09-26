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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
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

    // ---- invite() ----

    @Test
    public void testInviteCreatesTheInvitationWithTheNormalizedEmail() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.SENT, result);
        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertEquals(NORMALIZED_INVITEE_EMAIL, saved.getValue().getEmail());
        assertEquals(EVENT_ID, saved.getValue().getEventId());
        assertEquals(CREATOR_EMAIL, saved.getValue().getInvitedBy());
    }

    @Test
    public void testInviteLinksTheInviteesUserIdWhenTheyAlreadyHaveAnAccount() {
        final long inviteeId = 42;
        final User invitee = new User(NORMALIZED_INVITEE_EMAIL, "Invitado");
        invitee.setId(inviteeId);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.of(invitee));

        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertEquals(Long.valueOf(inviteeId), saved.getValue().getUserId());
    }

    @Test
    public void testInviteLeavesTheUserIdNullWhenTheInviteeHasNoAccountYet() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertNull(saved.getValue().getUserId());
    }

    /**
     * EL TEST QUE IMPIDE MANDAR UN LINK ADIVINABLE. El token tiene que salir
     * de SecureRandom, no de algo previsible: si dos invitaciones dieran el
     * mismo token (o uno derivado del id), alguien podria calcular el link de
     * otra persona y entrar a un evento privado sin haber sido invitado.
     */
    @Test
    public void testInviteGeneratesANonEmptyToken() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertTrue(saved.getValue().getToken() != null && saved.getValue().getToken().length() > 20);
    }

    @Test
    public void testInviteSendsTheNotification() {
        final Event event = privateEvent();
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        verify(mockNotificationService).notifyEventInvitation(eq(event), any(EventInvitation.class));
    }

    @Test
    public void testInviteLeavesAMessageInTheInviteesInbox() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        verify(mockInboxService).add(NORMALIZED_INVITEE_EMAIL, InboxMessageType.PRIVATE_INVITE, EVENT_ID, null, null);
    }

    @Test
    public void testInviteReturnsAlreadyInvitedWhenThatEmailIsAlreadyInvited() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL)));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.ALREADY_INVITED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventIsNotPrivate() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(publicEvent()));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheRequesterIsNotTheCreator() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, OTHER_ID, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventDoesNotExist() {
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    // ---- isInvited() ----
    // Es lo que decide si un evento privado se puede ver: el id es el de la sesion.

    @Test
    public void testIsInvitedReturnsTrueForTheInvitedUserId() {
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, OTHER_ID))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL)));

        assertTrue(invitationService.isInvited(EVENT_ID, OTHER_ID));
    }

    @Test
    public void testIsInvitedReturnsFalseForAnotherUserId() {
        when(mockInvitationDao.findByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());

        assertFalse(invitationService.isInvited(EVENT_ID, OTHER_ID));
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        final List<EventInvitation> invitations = Collections.singletonList(
                new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL));
        when(mockInvitationDao.findByEvent(EVENT_ID)).thenReturn(invitations);

        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, CREATOR_ID);

        assertEquals(invitations, result);
    }

    @Test
    public void testFindByEventReturnsEmptyWhenAskedBySomeoneElse() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, OTHER_ID);

        assertTrue(result.isEmpty());
        verify(mockInvitationDao, never()).findByEvent(EVENT_ID);
    }

    @Test
    public void testFindByEventReturnsEmptyWhenTheEventDoesNotExist() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertTrue(invitationService.findByEvent(EVENT_ID, CREATOR_ID).isEmpty());
    }

    // ---- cupo: cada invitacion reserva un lugar ----

    @Test
    public void testInviteReturnsFullWhenConfirmedPlusInvitationsReachTheCapacity() {
        final Event event = privateEvent();
        event.setMaxCapacity(3);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(2L);

        assertEquals(EventInvitationResult.FULL, invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL));
        verify(mockInvitationDao, never()).create(any());
        verify(mockNotificationService, never()).notifyEventInvitation(any(), any());
    }

    @Test
    public void testInviteStillWorksWhileThereIsRoomLeft() {
        final Event event = privateEvent();
        event.setMaxCapacity(3);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);
        when(mockInvitationDao.countOutstanding(EVENT_ID)).thenReturn(1L);
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        assertEquals(EventInvitationResult.SENT, invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL));
    }
}
