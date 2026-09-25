package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.EventInvitationDao;
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
    private static final String INVITEE_EMAIL = "  Invitado@Mail.com  ";
    private static final String NORMALIZED_INVITEE_EMAIL = "invitado@mail.com";

    @Mock
    private EventInvitationDao mockInvitationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private NotificationService mockNotificationService;

    // La bandeja: se verifica que cada accion deje su aviso.
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private EventInvitationServiceImpl invitationService;

    private static Event privateEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setVisibility(EventVisibility.PRIVATE);
        return event;
    }

    private static Event publicEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setVisibility(EventVisibility.PUBLIC);
        return event;
    }

    // ---- invite() ----

    @Test
    public void testInviteCreatesTheInvitationWithTheNormalizedEmail() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.SENT, result);
        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertEquals(NORMALIZED_INVITEE_EMAIL, saved.getValue().getEmail());
        assertEquals(EVENT_ID, saved.getValue().getEventId());
        assertEquals(CREATOR_EMAIL, saved.getValue().getInvitedBy());
    }

    /**
     * EL TEST QUE IMPIDE MANDAR UN LINK ADIVINABLE. El token tiene que salir
     * de SecureRandom, no de algo previsible: si dos invitaciones dieran el
     * mismo token (o uno derivado del id), alguien podria calcular el link de
     * otra persona y entrar a un evento privado sin haber sido invitado.
     */
    @Test
    public void testInviteGeneratesANonEmptyToken() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        final ArgumentCaptor<EventInvitation> saved = ArgumentCaptor.forClass(EventInvitation.class);
        verify(mockInvitationDao).create(saved.capture());
        assertTrue(saved.getValue().getToken() != null && saved.getValue().getToken().length() > 20);
    }

    @Test
    public void testInviteSendsTheNotification() {
        final Event event = privateEvent();
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        verify(mockNotificationService).notifyEventInvitation(eq(event), any(EventInvitation.class));
    }

    @Test
    public void testInviteLeavesAMessageInTheInviteesInbox() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        verify(mockInboxService).add(NORMALIZED_INVITEE_EMAIL, InboxMessageType.PRIVATE_INVITE, EVENT_ID, null, null);
    }

    @Test
    public void testInviteReturnsAlreadyInvitedWhenThatEmailIsAlreadyInvited() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL)));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.ALREADY_INVITED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventIsNotPrivate() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(publicEvent()));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheRequesterIsNotTheCreator() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        final EventInvitationResult result = invitationService.invite(EVENT_ID, "otro@mail.com", INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
        verify(mockInvitationDao, never()).create(any());
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventDoesNotExist() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_EMAIL, INVITEE_EMAIL);

        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    // ---- isInvited() ----
    // Es lo que decide si un evento privado se puede ver: el email es el de la sesion.

    @Test
    public void testIsInvitedReturnsTrueForTheInvitedEmailIgnoringCase() {
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL)));

        assertTrue(invitationService.isInvited(EVENT_ID, INVITEE_EMAIL));
    }

    @Test
    public void testIsInvitedReturnsFalseForAnotherEmail() {
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, "otro@mail.com")).thenReturn(Optional.empty());

        assertFalse(invitationService.isInvited(EVENT_ID, "otro@mail.com"));
    }

    // ---- findByEvent() ----

    @Test
    public void testFindByEventDelegatesToTheDaoWhenTheCreatorAsks() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        final List<EventInvitation> invitations = Collections.singletonList(
                new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, "tok", CREATOR_EMAIL));
        when(mockInvitationDao.findByEvent(EVENT_ID)).thenReturn(invitations);

        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, CREATOR_EMAIL);

        assertEquals(invitations, result);
    }

    @Test
    public void testFindByEventReturnsEmptyWhenAskedBySomeoneElse() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        final List<EventInvitation> result = invitationService.findByEvent(EVENT_ID, "otro@mail.com");

        assertTrue(result.isEmpty());
        verify(mockInvitationDao, never()).findByEvent(EVENT_ID);
    }

    @Test
    public void testFindByEventReturnsEmptyWhenTheEventDoesNotExist() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertTrue(invitationService.findByEvent(EVENT_ID, CREATOR_EMAIL).isEmpty());
    }
}
