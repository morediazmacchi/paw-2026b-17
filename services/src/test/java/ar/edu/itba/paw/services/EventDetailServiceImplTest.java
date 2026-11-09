package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: todos los services que usa el detalle son @Mock. Donde una rama no deberia
 * consultar algo (ej. el visitante anonimo), el mock tiene un dato senuelo (con lenient()) que cambiaria el
 * resultado si el service lo consultara igual.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventDetailServiceImplTest {

    private static final long EVENT_ID = 10;
    private static final long VIEWER_ID = 2;
    private static final long CREATOR_ID = 3;
    private static final int QUESTIONS_PAGE = 2;
    private static final int REVIEWS_PAGE = 4;
    private static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private FavoriteService favoriteService;
    @Mock
    private EventChatService eventChatService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private EventSimilarityService eventSimilarityService;
    @Mock
    private EventQuestionService eventQuestionService;
    @Mock
    private EventAccessService eventAccessService;
    @Mock
    private EventInvitationService eventInvitationService;
    @Mock
    private FollowService followService;

    private EventDetailServiceImpl detailService;
    private Event event;

    @Before
    public void setUp() {
        detailService = new EventDetailServiceImpl(eventService, registrationService, favoriteService,
                eventChatService, reviewService, eventSimilarityService, eventQuestionService, eventAccessService,
                eventInvitationService, followService);
        event = ModelFixtures.event(EVENT_ID);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        // Un evento que todavia no paso (el estado "finalizado" se prueba aparte).
        event.setEventDate(new Date(System.currentTimeMillis() + ONE_DAY_MILLIS));
        when(eventService.findByIdOrThrow(EVENT_ID)).thenReturn(event);
        // Lo que el detalle siempre pide, sea quien sea que mire.
        lenient().when(eventQuestionService.findPage(EVENT_ID, QUESTIONS_PAGE)).thenReturn(emptyQuestions());
    }

    private static PageResult<EventQuestion> emptyQuestions() {
        return new PageResult<>(Collections.<EventQuestion>emptyList(), 0, 1, 10);
    }

    private static CreatorReputation reputation() {
        return new CreatorReputation(new PageResult<>(Collections.<Review>emptyList(), 0, 1, 10), 0,
                Collections.<Long, Event>emptyMap());
    }

    @Test
    public void testGetDetailHoldsTheEventTheFreeSpotsAndThePageOfQuestions() {
        // Arrange
        final PageResult<EventQuestion> questions = new PageResult<>(Collections.<EventQuestion>emptyList(), 25, 2, 10);
        when(eventQuestionService.findPage(EVENT_ID, QUESTIONS_PAGE)).thenReturn(questions);
        when(registrationService.availableSpots(EVENT_ID)).thenReturn(7);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertSame(event, result.getEvent());
        assertEquals(Integer.valueOf(7), result.getAvailableSpots());
        assertSame(questions, result.getQuestions());
    }

    @Test
    public void testGetDetailForAnAnonymousVisitorUsesTheGeneralFullness() {
        // Arrange: sin sesion no hay invitacion que cuente; el dato de "lleno para esta persona" es un senuelo
        when(registrationService.isFull(EVENT_ID)).thenReturn(true);
        lenient().when(registrationService.isFullFor(anyLong(), anyLong())).thenReturn(false);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertTrue(result.isFull());
    }

    @Test
    public void testGetDetailForAnAnonymousVisitorHasNoParticipationFavoriteNorChat() {
        // Arrange: senuelos que solo se verian si se los consultara para una persona sin sesion
        lenient().when(registrationService.findByEventAndUserId(anyLong(), anyLong()))
                .thenReturn(Optional.of(registrationWith(RegistrationStatus.CONFIRMED)));
        lenient().when(favoriteService.isFavorite(anyLong(), anyLong())).thenReturn(true);
        lenient().when(eventChatService.isMember(anyLong(), anyLong())).thenReturn(true);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertNull(result.getParticipation());
        assertFalse(result.isFavorite());
        assertFalse(result.isChatMember());
    }

    @Test
    public void testGetDetailForALoggedInViewerUsesTheFullnessFromTheirPointOfView() {
        // Arrange: lleno en general, pero no para esta persona (tiene un lugar reservado por una invitacion)
        lenient().when(registrationService.isFull(EVENT_ID)).thenReturn(true);
        when(registrationService.isFullFor(EVENT_ID, VIEWER_ID)).thenReturn(false);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertFalse(result.isFull());
    }

    @Test
    public void testGetDetailForALoggedInViewerHoldsTheirParticipationFavoriteAndChatMembership() {
        // Arrange
        viewerRegistered(RegistrationStatus.CONFIRMED);
        when(favoriteService.isFavorite(VIEWER_ID, EVENT_ID)).thenReturn(true);
        when(eventChatService.isMember(EVENT_ID, VIEWER_ID)).thenReturn(true);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertTrue(result.getParticipation().isRegistered());
        assertTrue(result.getParticipation().isChatConfirmed());
        assertTrue(result.isFavorite());
        assertTrue(result.isChatMember());
    }

    @Test
    public void testGetDetailHoldsTheReputationOfTheCreatorForTheRequestedReviewsPage() {
        // Arrange
        final CreatorReputation reputation = reputation();
        when(reviewService.findCreatorReputation(CREATOR_ID, REVIEWS_PAGE)).thenReturn(reputation);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertSame(reputation, result.getCreatorReputation());
    }

    @Test
    public void testGetDetailOfAnEventWithoutCreatorHasNoReputation() {
        // Arrange: un evento viejo sin organizador cargado; el dato senuelo solo aparece si se busca igual
        event.setCreator(null);
        lenient().when(reviewService.findCreatorReputation(anyLong(), anyInt())).thenReturn(reputation());

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertNull(result.getCreatorReputation());
    }

    @Test
    public void testGetDetailHoldsTheRelatedEventsAndTheViewersRegistrationStatusInEach() {
        // Arrange
        final Event related = ModelFixtures.event(11L);
        final Map<Long, RegistrationStatus> statuses = Collections.singletonMap(11L, RegistrationStatus.CONFIRMED);
        when(eventSimilarityService.findSimilar(EVENT_ID, VIEWER_ID)).thenReturn(Collections.singletonList(related));
        when(registrationService.findStatusesByEventIds(VIEWER_ID, Collections.singletonList(11L))).thenReturn(statuses);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertEquals(Collections.singletonList(related), result.getRelatedEvents());
        assertEquals(statuses, result.getRelatedStatuses());
    }

    @Test
    public void testGetDetailHoldsWhichRelatedEventsAreTheViewersFavorites() {
        // Arrange
        final Event related = ModelFixtures.event(11L);
        final Set<Long> favorites = Collections.singleton(11L);
        when(eventSimilarityService.findSimilar(EVENT_ID, VIEWER_ID)).thenReturn(Collections.singletonList(related));
        when(favoriteService.findFavoriteEventIds(VIEWER_ID, Collections.singletonList(11L))).thenReturn(favorites);

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertEquals(favorites, result.getRelatedFavoriteIds());
    }

    @Test
    public void testGetDetailForAnAnonymousVisitorHasNoFavoritesAmongTheRelatedEvents() {
        // Arrange: el senuelo solo aparece si se buscan favoritos sin haber una persona con sesion
        final Event related = ModelFixtures.event(11L);
        when(eventSimilarityService.findSimilar(EVENT_ID, null)).thenReturn(Collections.singletonList(related));
        lenient().when(favoriteService.findFavoriteEventIds(anyLong(), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Collections.singleton(11L));

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertTrue(result.getRelatedFavoriteIds().isEmpty());
    }

    @Test
    public void testGetDetailForAnAnonymousVisitorHasNoStatusesForTheRelatedEvents() {
        // Arrange: el senuelo solo aparece si se buscan estados sin haber una persona con sesion
        final Event related = ModelFixtures.event(11L);
        when(eventSimilarityService.findSimilar(EVENT_ID, null)).thenReturn(Collections.singletonList(related));
        lenient().when(registrationService.findStatusesByEventIds(anyLong(), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Collections.singletonMap(11L, RegistrationStatus.CONFIRMED));

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertEquals(Collections.singletonList(related), result.getRelatedEvents());
        assertTrue(result.getRelatedStatuses().isEmpty());
    }

    @Test
    public void testGetDetailWithoutRelatedEventsHasNoStatuses() {
        // Arrange: findSimilar devuelve vacio (evento privado o no aprobado); el senuelo no deberia mostrarse
        when(eventSimilarityService.findSimilar(EVENT_ID, VIEWER_ID)).thenReturn(Collections.<Event>emptyList());
        lenient().when(registrationService.findStatusesByEventIds(anyLong(), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Collections.singletonMap(11L, RegistrationStatus.CONFIRMED));

        // Act
        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert
        assertTrue(result.getRelatedEvents().isEmpty());
        assertTrue(result.getRelatedStatuses().isEmpty());
    }

    @Test(expected = EventNotFoundException.class)
    public void testGetDetailOfAMissingEventThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findByIdOrThrow(EVENT_ID)).thenThrow(new EventNotFoundException(EVENT_ID));

        // Act
        detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- accion disponible (EventAction), acceso al chat y darse de baja ----

    private static Registration registrationWith(final RegistrationStatus status) {
        return new Registration(ModelFixtures.event(EVENT_ID), null, "viewer@mail.com", "Viewer", status);
    }

    // La persona logueada: puede anotarse (regla de EventAccessService.canRegister) y tiene (o no) inscripcion.
    private void viewer(final RegistrationStatus status, final boolean canRegister) {
        when(registrationService.findByEventAndUserId(EVENT_ID, VIEWER_ID))
                .thenReturn(Optional.ofNullable(status).map(EventDetailServiceImplTest::registrationWith));
        when(eventAccessService.canRegister(event, VIEWER_ID)).thenReturn(canRegister);
    }

    private void viewerRegistered(final RegistrationStatus status) {
        viewer(status, true);
    }

    private EventAction actionFor(final Long viewerId) {
        return detailService.getDetail(EVENT_ID, viewerId, QUESTIONS_PAGE, REVIEWS_PAGE).getAction();
    }

    @Test
    public void testActionIsRegisterForAViewerWithRoomAndNoRegistration() {
        viewer(null, true);

        assertEquals(EventAction.REGISTER, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsRequestApprovalWhenTheEventNeedsTheOrganizersApproval() {
        event.setRequiresApproval(true);
        viewer(null, true);

        assertEquals(EventAction.REQUEST_APPROVAL, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsRegisterForAnInviteeToAnEventThatRequiresApproval() {
        // Arrange: lo invitaron, asi que register() la deja confirmada (no hay aprobacion que pedir)
        event.setRequiresApproval(true);
        viewer(null, true);
        when(eventInvitationService.isInvited(EVENT_ID, VIEWER_ID)).thenReturn(true);

        assertEquals(EventAction.REGISTER, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsRequestApprovalForSomeoneNotInvitedToAnEventThatRequiresApproval() {
        // Arrange: hay una invitacion senuelo para OTRA persona
        event.setRequiresApproval(true);
        viewer(null, true);
        lenient().when(eventInvitationService.isInvited(EVENT_ID, VIEWER_ID + 1)).thenReturn(true);

        assertEquals(EventAction.REQUEST_APPROVAL, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsJoinWaitlistWhenTheEventIsFullForThatViewer() {
        viewer(null, true);
        when(registrationService.isFullFor(EVENT_ID, VIEWER_ID)).thenReturn(true);

        assertEquals(EventAction.JOIN_WAITLIST, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsJoinWaitlistEvenIfTheEventRequiresApprovalWhenItIsFull() {
        event.setRequiresApproval(true);
        viewer(null, true);
        when(registrationService.isFullFor(EVENT_ID, VIEWER_ID)).thenReturn(true);

        assertEquals(EventAction.JOIN_WAITLIST, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsUnregisterForAConfirmedRegistration() {
        viewerRegistered(RegistrationStatus.CONFIRMED);

        assertEquals(EventAction.UNREGISTER, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsLeaveWaitlistForAWaitlistedRegistration() {
        viewerRegistered(RegistrationStatus.WAITLISTED);

        assertEquals(EventAction.LEAVE_WAITLIST, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsCancelPendingForARegistrationWaitingForApproval() {
        viewerRegistered(RegistrationStatus.PENDING);

        assertEquals(EventAction.CANCEL_PENDING, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsRegistrationRejectedForARejectedRegistration() {
        viewerRegistered(RegistrationStatus.REJECTED);

        assertEquals(EventAction.REGISTRATION_REJECTED, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsOwnerForTheOrganizer() {
        viewer(null, true);
        when(eventAccessService.canRegister(event, CREATOR_ID)).thenReturn(true);

        assertEquals(EventAction.OWNER, actionFor(CREATOR_ID));
    }

    @Test
    public void testActionIsOwnerPendingWhenTheOrganizersEventAwaitsReview() {
        event.setApprovalStatus(EventApprovalStatus.PENDING);

        assertEquals(EventAction.OWNER_PENDING, actionFor(CREATOR_ID));
    }

    @Test
    public void testActionIsOwnerRejectedWhenAnAdminRejectedTheOrganizersEvent() {
        event.setApprovalStatus(EventApprovalStatus.REJECTED);

        assertEquals(EventAction.OWNER_REJECTED, actionFor(CREATOR_ID));
    }

    @Test
    public void testTheOwnerWinsOverACancelledEvent() {
        event.setCancelledAt(new Date());

        assertEquals(EventAction.OWNER, actionFor(CREATOR_ID));
    }

    @Test
    public void testActionIsAdminReviewOnlyForAViewerWhoCannotRegisterInAnUnapprovedEvent() {
        event.setApprovalStatus(EventApprovalStatus.PENDING);
        viewer(null, false);

        assertEquals(EventAction.ADMIN_REVIEW_ONLY, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsViewOnlyForAViewerWhoCannotRegisterInAnApprovedEvent() {
        viewer(null, false);

        assertEquals(EventAction.VIEW_ONLY, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsClosedCancelledForACancelledEvent() {
        event.setCancelledAt(new Date());
        viewer(null, true);

        assertEquals(EventAction.CLOSED_CANCELLED, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsClosedRemovedByAdminForAnEventTakenDownByModeration() {
        event.setCancelledAt(new Date());
        event.setRemovedByAdmin(true);
        viewer(null, true);

        assertEquals(EventAction.CLOSED_REMOVED_BY_ADMIN, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionIsClosedFinishedForAnEventThatAlreadyHappened() {
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        viewer(null, true);

        assertEquals(EventAction.CLOSED_FINISHED, actionFor(VIEWER_ID));
    }

    @Test
    public void testACancelledEventWinsOverAConfirmedRegistration() {
        event.setCancelledAt(new Date());
        viewerRegistered(RegistrationStatus.CONFIRMED);

        assertEquals(EventAction.CLOSED_CANCELLED, actionFor(VIEWER_ID));
    }

    @Test
    public void testActionForAnAnonymousVisitorIsRegister() {
        assertEquals(EventAction.REGISTER, actionFor(null));
    }

    @Test
    public void testActionForAnAnonymousVisitorIsJoinWaitlistWhenTheEventIsFull() {
        when(registrationService.isFull(EVENT_ID)).thenReturn(true);

        assertEquals(EventAction.JOIN_WAITLIST, actionFor(null));
    }

    @Test
    public void testActionForAnAnonymousVisitorIsClosedWhenTheEventWasCancelled() {
        event.setCancelledAt(new Date());

        assertEquals(EventAction.CLOSED_CANCELLED, actionFor(null));
    }

    @Test
    public void testAViewerCanAskAndReportButCannotAnswerOnSomeoneElsesOpenEvent() {
        viewer(null, true);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertTrue(result.isCanAsk());
        assertTrue(result.isCanReport());
        assertFalse(result.isCanAnswer());
    }

    @Test
    public void testTheOrganizerCanAnswerButCannotAskNorReportOnTheirOwnEvent() {
        final EventDetail result = detailService.getDetail(EVENT_ID, CREATOR_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertTrue(result.isCanAnswer());
        assertFalse(result.isCanAsk());
        assertFalse(result.isCanReport());
    }

    @Test
    public void testNobodyCanAskOnAnEventThatIsNoLongerOpenForQuestions() {
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        viewer(null, true);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertFalse(result.isCanAsk());
    }

    @Test
    public void testAnAnonymousVisitorCanAskAndReport() {
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertTrue(result.isCanAsk());
        assertTrue(result.isCanReport());
        assertFalse(result.isCanAnswer());
    }

    @Test
    public void testChatAccessIsOpenForAChatMemberEvenIfTheEventFinished() {
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        viewerRegistered(RegistrationStatus.CONFIRMED);
        when(eventChatService.isMember(EVENT_ID, VIEWER_ID)).thenReturn(true);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertEquals(ChatAccess.OPEN, result.getChatAccess());
    }

    @Test
    public void testChatAccessIsJoinForAConfirmedNonMemberWhileTheEventIsActive() {
        viewerRegistered(RegistrationStatus.CONFIRMED);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertEquals(ChatAccess.JOIN, result.getChatAccess());
    }

    @Test
    public void testChatAccessIsNoneForAConfirmedNonMemberOnceTheEventFinished() {
        event.setEventDate(new Date(System.currentTimeMillis() - ONE_DAY_MILLIS));
        viewerRegistered(RegistrationStatus.CONFIRMED);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertEquals(ChatAccess.NONE, result.getChatAccess());
    }

    @Test
    public void testChatAccessIsNoneForSomeoneWhoseRegistrationIsNotConfirmed() {
        viewerRegistered(RegistrationStatus.WAITLISTED);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertEquals(ChatAccess.NONE, result.getChatAccess());
    }

    @Test
    public void testChatAccessIsNoneForAnAnonymousVisitor() {
        final EventDetail result = detailService.getDetail(EVENT_ID, null, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertEquals(ChatAccess.NONE, result.getChatAccess());
    }

    @Test
    public void testCanUnregisterIsTrueForARegisteredViewerWhileTheEventIsActive() {
        viewerRegistered(RegistrationStatus.PENDING);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertTrue(result.isCanUnregister());
    }

    @Test
    public void testCanUnregisterIsFalseOnceTheEventWasCancelled() {
        event.setCancelledAt(new Date());
        viewerRegistered(RegistrationStatus.CONFIRMED);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertFalse(result.isCanUnregister());
    }

    @Test
    public void testCanUnregisterIsFalseForARejectedRegistration() {
        viewerRegistered(RegistrationStatus.REJECTED);

        final EventDetail result = detailService.getDetail(EVENT_ID, VIEWER_ID, QUESTIONS_PAGE, REVIEWS_PAGE);

        assertFalse(result.isCanUnregister());
    }
}
