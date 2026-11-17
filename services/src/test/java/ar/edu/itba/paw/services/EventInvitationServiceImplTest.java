package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventInvitationDao;
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
    private EventService eventService;
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
                mockInvitationDao, eventService, mockNotificationService, mockInboxService, mockUserService);
    }

    private static Event privateEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setVisibility(EventVisibility.PRIVATE);
        // Ya aprobado por un admin: antes de eso no se puede invitar (ver el test de NOT_APPROVED).
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        // Futura: invite() pregunta isFinished(), que no admite fecha nula.
        event.setEventDate(new java.util.Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));
        return event;
    }

    private static Event publicEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setVisibility(EventVisibility.PUBLIC);
        return event;
    }

    // ---- invite() ----

    @Test
    public void testInviteReturnsSentWhenThePrivateEventsCreatorInvitesAnEmailNotYetInvited() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        // El email se busca ya normalizado: si invite() no lo normalizara, este stub no matchea.
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
    }

    @Test
    public void testInviteWithABlankEmailReturnsInvalidEmail() {
        // Arrange: el evento privado existe; si el service invitara igual, el resultado seria otro
        lenient().when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, "   ");

        // Assert
        assertEquals(EventInvitationResult.INVALID_EMAIL, result);
    }

    @Test
    public void testInviteWithAnEmailLongerThanTheMaximumReturnsInvalidEmail() {
        // Arrange
        lenient().when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        final String tooLong = new String(new char[EventInvitationService.MAX_EMAIL_LENGTH]).replace('\0', 'a') + "@mail.com";

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, tooLong);

        // Assert
        assertEquals(EventInvitationResult.INVALID_EMAIL, result);
    }

    @Test
    public void testInviteReturnsSentWhenTheInviteeAlreadyHasAnAccount() {
        // Arrange
        final User invitee = new User(NORMALIZED_INVITEE_EMAIL, "Invitado");
        invitee.setId(42L);
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.of(invitee));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
    }

    @Test
    public void testInviteReturnsAlreadyInvitedWhenThatEmailIsAlreadyInvited() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(privateEvent()));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL))
                .thenReturn(Optional.of(new EventInvitation(ModelFixtures.event(EVENT_ID), NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL)));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.ALREADY_INVITED, result);
    }

    @Test
    public void testInviteToAPublicEventReturnsEventPublicAsABusinessResult() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(publicEvent()));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.EVENT_PUBLIC, result);
    }

    // Privado pero todavia sin aprobar: solo lo ven su creador y los admins, la invitacion llevaria a un 404.
    @Test
    public void testInviteReturnsNotApprovedWhenThePrivateEventIsStillPendingApproval() {
        // Arrange
        final Event pending = privateEvent();
        pending.setApprovalStatus(EventApprovalStatus.PENDING);
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(pending));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_APPROVED, result);
    }

    // Un evento cancelado no invita a nadie (saldria el mail de una cosa que no va a pasar).
    @Test
    public void testInviteReturnsNotAllowedWhenTheEventIsCancelled() {
        // Arrange
        final Event cancelled = privateEvent();
        cancelled.setCancelledAt(new java.util.Date());
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(cancelled));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventAlreadyHappened() {
        // Arrange
        final Event past = privateEvent();
        past.setEventDate(new java.util.Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000));
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(past));

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    @Test
    public void testInviteReturnsNotAllowedWhenTheEventDoesNotExist() {
        // Arrange
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.NOT_ALLOWED, result);
    }

    // ---- isInvited() ----
    // Es lo que decide si un evento privado se puede ver: el id es el de la sesion.

    @Test
    public void testIsInvitedReturnsTrueForTheInvitedUserId() {
        // Arrange
        when(mockInvitationDao.existsByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(true);

        // Act
        final boolean result = invitationService.isInvited(EVENT_ID, OTHER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsInvitedReturnsFalseForAnotherUserId() {
        // Arrange
        when(mockInvitationDao.existsByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(false);

        // Act
        final boolean result = invitationService.isInvited(EVENT_ID, OTHER_ID);

        // Assert
        assertFalse(result);
    }

    // ---- findPageByEvent() ----

    @Test
    public void testFindPageByEventReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a EventInvitationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<EventInvitation> pageTwo = Collections.singletonList(
                new EventInvitation(ModelFixtures.event(EVENT_ID), NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByEvent(EVENT_ID)).thenReturn(25L);
        when(mockInvitationDao.findByEvent(EVENT_ID, 2, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByEvent(EVENT_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindPageByEventClampsAPageBeyondTheLastToTheLastOne() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<EventInvitation> lastPage = Collections.singletonList(
                new EventInvitation(ModelFixtures.event(EVENT_ID), NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByEvent(EVENT_ID)).thenReturn(25L);
        when(mockInvitationDao.findByEvent(EVENT_ID, 3, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByEvent(EVENT_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindPageByEventTreatsAPageBelowOneAsTheFirst() {
        // Arrange
        final List<EventInvitation> firstPage = Collections.singletonList(
                new EventInvitation(ModelFixtures.event(EVENT_ID), NORMALIZED_INVITEE_EMAIL, CREATOR_EMAIL));
        when(mockInvitationDao.countByEvent(EVENT_ID)).thenReturn(25L);
        when(mockInvitationDao.findByEvent(EVENT_ID, 1, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<EventInvitation> result = invitationService.findPageByEvent(EVENT_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    // ---- cupo: cada invitacion reserva un lugar ----
    // Cuantos lugares hay ocupados (confirmados + invitaciones sin usar) lo da EventService.occupiedSpots() y se testea
    // en EventServiceImplTest / EventJpaDaoTest: aca solo importa que invite() lo compare con el cupo del evento.

    @Test
    public void testInviteReturnsFullWhenTheEventIsFull() {
        // Arrange
        final Event full = privateEvent();
        full.setMaxCapacity(10);
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(full));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(10L);

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.FULL, result);
    }

    @Test
    public void testInviteStillWorksWhileThereIsRoomLeft() {
        // Arrange
        final Event withRoom = privateEvent();
        withRoom.setMaxCapacity(10);
        when(eventService.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(withRoom));
        when(mockInvitationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());
        when(eventService.occupiedSpots(EVENT_ID)).thenReturn(9L);
        when(mockUserService.findByEmail(NORMALIZED_INVITEE_EMAIL)).thenReturn(Optional.empty());

        // Act
        final EventInvitationResult result = invitationService.invite(EVENT_ID, INVITEE_EMAIL);

        // Assert
        assertEquals(EventInvitationResult.SENT, result);
    }

    // ---- findInvitedEventsPage() ----

    @Test
    public void testFindInvitedEventsPageReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a EventInvitationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<Event> pageTwo = Collections.singletonList(ModelFixtures.event(EVENT_ID));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findInvitedEventsByUserId(OTHER_ID, 2, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<Event> result = invitationService.findInvitedEventsPage(OTHER_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindInvitedEventsPageShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<Event> lastPage = Collections.singletonList(ModelFixtures.event(EVENT_ID));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findInvitedEventsByUserId(OTHER_ID, 3, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<Event> result = invitationService.findInvitedEventsPage(OTHER_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testFindInvitedEventsPageShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        final List<Event> firstPage = Collections.singletonList(ModelFixtures.event(EVENT_ID));
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(25L);
        when(mockInvitationDao.findInvitedEventsByUserId(OTHER_ID, 1, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<Event> result = invitationService.findInvitedEventsPage(OTHER_ID, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    @Test
    public void testFindInvitedEventsPageWithNoResultsReturnsAnEmptyFirstPage() {
        // Arrange
        when(mockInvitationDao.countByUserId(OTHER_ID)).thenReturn(0L);
        when(mockInvitationDao.findInvitedEventsByUserId(OTHER_ID, 1, EventInvitationServiceImpl.PAGE_SIZE)).thenReturn(Collections.emptyList());

        // Act
        final PageResult<Event> result = invitationService.findInvitedEventsPage(OTHER_ID, 5);

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

    // ---- linkAccount() ----

    @Test
    public void testLinkAccountReturnsHowManyInvitationsWereLinked() {
        // Arrange: el stub solo responde con el mail ya normalizado y el id de la cuenta.
        when(mockInvitationDao.linkUserId(NORMALIZED_INVITEE_EMAIL, 77L)).thenReturn(2);

        // Act
        final int result = invitationService.linkAccount(INVITEE_EMAIL, 77L);

        // Assert
        assertEquals(2, result);
    }
}
