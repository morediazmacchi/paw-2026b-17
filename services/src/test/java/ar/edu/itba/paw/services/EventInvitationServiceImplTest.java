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
import static org.junit.Assert.assertTrue;
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
    @Mock
    private InboxService mockInboxService;

    // Enlace optimista del Grupo B (ver EventInvitationServiceImpl.invite()).
    @Mock
    private UserService mockUserService;

    private EventInvitationServiceImpl invitationService;

    @Before
    public void setUp() {
        invitationService = new EventInvitationServiceImpl(
                mockInvitationDao, mockEventDao, mockRegistrationDao, mockNotificationService, mockInboxService, mockUserService);
    }

    private static Event privateEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreatorId(CREATOR_ID);
        event.setVisibility(EventVisibility.PRIVATE);
        // Futura: invite() pregunta isFinished(), que no admite fecha nula.
        event.setEventDate(new java.util.Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));
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
    public void testInviteReturnsSentWhenThePrivateEventsCreatorInvitesAnEmailNotYetInvited() {
        // Arrange
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        // El email se busca ya normalizado: si invite() no lo normalizara, este stub no matchea.
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
    }

    @Test
    public void testInviteReturnsSentWhenTheInviteeAlreadyHasAnAccount() {
        // Arrange
        final User invitee = new User(NORMALIZED_INVITEE_EMAIL, "Invitado");
        invitee.setId(42);
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.of(invitee));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
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

    // Un evento cancelado no invita a nadie (saldria el mail de una cosa que no va a pasar).
    @Test
    public void testInviteReturnsNotAllowedWhenTheEventIsCancelled() {
        // Arrange
        final Event cancelled = privateEvent();
        cancelled.setCancelledAt(new java.util.Date());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, CREATOR_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventAlreadyHappened() {
        // Arrange
        final Event past = privateEvent();
        past.setEventDate(new java.util.Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000));
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(past));

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

    // ---- findPageByUser() ----

    @Test
    public void testFindPageByUserReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a EventInvitationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<EventInvitation> pageTwo = Collections.singletonList(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findByUserId(OTHER_ID, 2, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByUser(OTHER_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindPageByUserShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<EventInvitation> lastPage = Collections.singletonList(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findByUserId(OTHER_ID, 3, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByUser(OTHER_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindPageByUserShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        final List<EventInvitation> firstPage = Collections.singletonList(new EventInvitation(EVENT_ID, NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findByUserId(OTHER_ID, 1, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByUser(OTHER_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    @Test
    public void testFindPageByUserWithNoResultsReturnsAnEmptyFirstPage() {
        // Arrange
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(0L);
        when(mockInvitationDao.findByUserId(OTHER_ID, 1, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByUser(OTHER_ID, 5);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }

    // ---- countByUser() ----

    @Test
    public void testCountByUserReturnsWhatTheDaoReturns() {
        // Arrange
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(1L);

        // Act
        final long result = invitationService.countByUser(OTHER_ID);

        // Assert
        assertEquals(1L, result);
    }
}
