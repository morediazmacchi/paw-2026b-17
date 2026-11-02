package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.RegistrationAccess;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventChatService;
import ar.edu.itba.paw.services.EventQuestionService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EventSecurityTest {

    private static final long EVENT_ID = 7L;
    private static final long OTHER_EVENT_ID = 8L;
    private static final long REGISTRATION_ID = 40L;
    private static final long CREATOR_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final String CREATOR_EMAIL = "creator@test.com";
    private static final String OTHER_EMAIL = "other@test.com";

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private EventAccessService eventAccessService;
    @Mock
    private ImageService imageService;
    @Mock
    private EventChatService eventChatService;
    @Mock
    private EventQuestionService eventQuestionService;

    private EventSecurity security;

    @Before
    public void setUp() {
        security = new EventSecurity(eventService, registrationService, eventAccessService, imageService, eventChatService,
                eventQuestionService);
    }

    private static User user(final long id) {
        final User user = new User();
        user.setId(id);
        return user;
    }

    // Lo que hay en la sesion despues del login: el principal es un PlancitoUserDetails.
    private static Authentication auth(final long id, final String email, final String... roles) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, email, "Nombre", "x", true,
                AuthorityUtils.createAuthorityList(roles));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Authentication creator() {
        return auth(CREATOR_ID, CREATOR_EMAIL, Role.USER.getAuthority());
    }

    private static Authentication other() {
        return auth(OTHER_ID, OTHER_EMAIL, Role.USER.getAuthority());
    }

    // Lo que devuelve la consulta de 4 columnas (id, organizador, privado, aprobado).
    private static EventAccessSummary event(final long id, final EventVisibility visibility) {
        return new EventAccessSummary(id, CREATOR_ID, visibility == EventVisibility.PRIVATE, true);
    }

    // ---- isOwner() ----

    @Test
    public void testIsOwnerIsTrueForTheCreator() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertTrue(security.isOwner(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerIsFalseForAnotherUser() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwner(EVENT_ID, other()));
    }

    @Test
    public void testIsOwnerIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwner(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerIsFalseWithoutAuthentication() {
        assertFalse(security.isOwner(EVENT_ID, null));
    }

    @Test
    public void testIsOwnerIsFalseWhenTheAuthenticationIsNotAuthenticated() {
        final Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertFalse(security.isOwner(EVENT_ID, authentication));
    }

    @Test
    public void testIsOwnerIsFalseWhenThePrincipalIsNotAPlancitoUserDetails() {
        final Authentication authentication = new UsernamePasswordAuthenticationToken(
                "otro@test.com", null, AuthorityUtils.createAuthorityList(Role.USER.getAuthority()));

        assertFalse(security.isOwner(EVENT_ID, authentication));
    }

    // Invitar solo pide ser el dueno (el evento puede ser publico o privado): que sea privado es regla
    // de negocio del service, no de acceso.
    @Test
    public void testIsOwnerIsTrueForTheCreatorOfAPrivateEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertTrue(security.isOwner(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerIsTrueForTheCreatorOfAPublicEventToo() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertTrue(security.isOwner(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerIsFalseForAnotherUserOnAPrivateEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertFalse(security.isOwner(EVENT_ID, other()));
    }

    // ---- isOwnerOfRegistrationEvent() ----

    private RegistrationAccess registrationOfEvent(final long eventId) {
        return new RegistrationAccess(CREATOR_ID, eventId, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsTrueWhenTheRegistrationBelongsToTheOwnedEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        final RegistrationAccess registration = registrationOfEvent(EVENT_ID);
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertTrue(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationBelongsToAnotherEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        final RegistrationAccess registration = registrationOfEvent(OTHER_EVENT_ID);
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheUserDoesNotOwnTheEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, other()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    // ---- isRegistrant() ----

    private RegistrationAccess registrationOfUser(final long userId) {
        return new RegistrationAccess(userId, EVENT_ID, RegistrationStatus.CONFIRMED);
    }

    @Test
    public void testIsRegistrantIsTrueForTheOwnerOfTheRegistration() {
        final RegistrationAccess registration = registrationOfUser(CREATOR_ID);
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertTrue(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseForTheRegistrationOfAnotherUser() {
        final RegistrationAccess registration = registrationOfUser(OTHER_ID);
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseWhenTheRegistrationDoesNotExist() {
        when(registrationService.findAccessById(REGISTRATION_ID)).thenReturn(Optional.empty());

        assertFalse(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseWithoutAuthentication() {
        assertFalse(security.isRegistrant(REGISTRATION_ID, null));
    }

    // ---- canAnswerQuestion() ----

    private static final long QUESTION_ID = 40L;

    @Test
    public void testCanAnswerQuestionIsTrueForTheCreatorOfTheQuestionsEvent() {
        when(eventQuestionService.findEventCreatorId(QUESTION_ID)).thenReturn(Optional.of(CREATOR_ID));

        assertTrue(security.canAnswerQuestion(QUESTION_ID, creator()));
    }

    @Test
    public void testCanAnswerQuestionIsFalseForSomeoneElse() {
        when(eventQuestionService.findEventCreatorId(QUESTION_ID)).thenReturn(Optional.of(CREATOR_ID));

        assertFalse(security.canAnswerQuestion(QUESTION_ID, other()));
    }

    @Test
    public void testCanAnswerQuestionIsFalseWhenTheQuestionDoesNotExist() {
        when(eventQuestionService.findEventCreatorId(QUESTION_ID)).thenReturn(Optional.empty());

        assertFalse(security.canAnswerQuestion(QUESTION_ID, creator()));
    }

    @Test
    public void testCanAnswerQuestionIsFalseWithoutAuthentication() {
        assertFalse(security.canAnswerQuestion(QUESTION_ID, null));
    }

    // ---- canView() ----

    @Test
    public void testCanViewIsTrueWhenEventAccessServiceAllowsTheIdOfThePrincipal() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le pregunta por el id del usuario de la sesion y sin ser admin.
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), false)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, other()));
    }

    @Test
    public void testCanViewTellsEventAccessServiceWhenThePrincipalIsAdmin() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le avisa que es admin (sale de las authorities del principal).
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), true)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, auth(OTHER_ID, OTHER_EMAIL, Role.USER.getAuthority(), Role.ADMIN.getAuthority())));
    }

    @Test
    public void testCanViewTreatsAnAnonymousVisitorAsSomeoneWithoutSession() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PUBLIC);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le pregunta sin usuario y sin admin.
        when(eventAccessService.canView(event, null, false)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseForAnAnonymousVisitorWhenEventAccessServiceDenies() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventAccessService.canView(event, null, false)).thenReturn(false);

        assertFalse(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseForAnAnonymousVisitorWhenTheEventDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canView(EVENT_ID, other()));
    }

    // ---- canRegister() ----

    @Test
    public void testCanRegisterIsTrueWhenEventAccessServiceAllowsItWithoutTheAdminException() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PUBLIC);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le pregunta sin privilegio de admin.
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), false)).thenReturn(true);

        assertTrue(security.canRegister(EVENT_ID, other()));
    }

    @Test
    public void testCanRegisterIsTrueForAnAdminWhoWasInvitedToAPrivateEvent() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Sin privilegio de admin, la invitacion le alcanza: el access service dice que si.
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), false)).thenReturn(true);

        assertTrue(security.canRegister(EVENT_ID, auth(OTHER_ID, OTHER_EMAIL, "ROLE_USER", "ROLE_ADMIN")));
    }

    @Test
    public void testCanRegisterIsFalseForAnAdminWithoutInvitationEvenThoughAnAdminCouldViewTheEvent() {
        final EventAccessSummary event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(event));
        // Con privilegio de admin el access service diria que si (puede VER): si canRegister se lo
        // pasara, el test fallaria. Sin privilegio y sin invitacion, no.
        lenient().when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), true)).thenReturn(true);
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), false)).thenReturn(false);

        assertFalse(security.canRegister(EVENT_ID, auth(OTHER_ID, OTHER_EMAIL, "ROLE_USER", "ROLE_ADMIN")));
    }

    @Test
    public void testCanRegisterIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canRegister(EVENT_ID, other()));
    }

    @Test
    public void testCanRegisterIsFalseWithoutSession() {
        assertFalse(security.canRegister(EVENT_ID, anonymous()));
    }

    // ---- isConfirmedAttendee() ----

    private RegistrationAccess registrationWithStatus(final RegistrationStatus status) {
        return new RegistrationAccess(OTHER_ID, EVENT_ID, status);
    }

    @Test
    public void testIsConfirmedAttendeeIsTrueForAConfirmedRegistration() {
        final RegistrationAccess registration = registrationWithStatus(RegistrationStatus.CONFIRMED);
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(registration));

        assertTrue(security.isConfirmedAttendee(EVENT_ID, other()));
    }

    @Test
    public void testIsConfirmedAttendeeIsFalseForAPendingRegistration() {
        final RegistrationAccess registration = registrationWithStatus(RegistrationStatus.PENDING);
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isConfirmedAttendee(EVENT_ID, other()));
    }

    @Test
    public void testIsConfirmedAttendeeIsFalseForAWaitlistedRegistration() {
        final RegistrationAccess registration = registrationWithStatus(RegistrationStatus.WAITLISTED);
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isConfirmedAttendee(EVENT_ID, other()));
    }

    @Test
    public void testIsConfirmedAttendeeIsFalseForARejectedRegistration() {
        final RegistrationAccess registration = registrationWithStatus(RegistrationStatus.REJECTED);
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isConfirmedAttendee(EVENT_ID, other()));
    }

    @Test
    public void testIsConfirmedAttendeeIsFalseWithoutRegistration() {
        when(registrationService.findAccessByEventAndUserId(EVENT_ID, OTHER_ID)).thenReturn(Optional.empty());

        assertFalse(security.isConfirmedAttendee(EVENT_ID, other()));
    }

    @Test
    public void testIsConfirmedAttendeeIsFalseWithoutSession() {
        assertFalse(security.isConfirmedAttendee(EVENT_ID, anonymous()));
    }

    // ---- isChatMember() ----

    @Test
    public void testIsChatMemberIsTrueForAnActiveMemberOfTheGroup() {
        when(eventChatService.isMember(EVENT_ID, OTHER_ID)).thenReturn(true);

        assertTrue(security.isChatMember(EVENT_ID, other()));
    }

    @Test
    public void testIsChatMemberIsFalseForSomeoneWhoIsNotAMember() {
        when(eventChatService.isMember(EVENT_ID, OTHER_ID)).thenReturn(false);

        assertFalse(security.isChatMember(EVENT_ID, other()));
    }

    @Test
    public void testIsChatMemberLooksUpTheIdOfThePrincipalNotAnotherUsersId() {
        // El creador es miembro, pero quien pregunta es otra persona: si la regla mirara cualquier
        // otro id, el test daria true.
        lenient().when(eventChatService.isMember(EVENT_ID, CREATOR_ID)).thenReturn(true);
        when(eventChatService.isMember(EVENT_ID, OTHER_ID)).thenReturn(false);

        assertFalse(security.isChatMember(EVENT_ID, other()));
    }

    @Test
    public void testIsChatMemberIsFalseWithoutSession() {
        assertFalse(security.isChatMember(EVENT_ID, anonymous()));
    }

    // ---- canViewImage() ----

    private static final long IMAGE_ID = 90L;

    private static Authentication anonymous() {
        return new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    @Test
    public void testCanViewImageIsFalseWhenTheImageBelongsToNoEvent() {
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.empty());

        assertFalse(security.canViewImage(IMAGE_ID, creator()));
    }

    @Test
    public void testCanViewImageOfAPublicEventIsTrueForAnAnonymousVisitor() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, false, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(true);

        assertTrue(security.canViewImage(IMAGE_ID, anonymous()));
    }

    @Test
    public void testCanViewImageOfAPrivateEventIsFalseForAnAnonymousVisitor() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(false);

        assertFalse(security.canViewImage(IMAGE_ID, anonymous()));
    }

    @Test
    public void testCanViewImageOfAPrivateEventIsTrueWhenTheLoggedUserCanViewIt() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(false);
        when(eventAccessService.canView(access, Long.valueOf(OTHER_ID), false)).thenReturn(true);

        assertTrue(security.canViewImage(IMAGE_ID, other()));
    }

    @Test
    public void testCanViewImageOfAPrivateEventIsFalseWhenTheLoggedUserCannotViewIt() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(false);
        when(eventAccessService.canView(access, Long.valueOf(OTHER_ID), false)).thenReturn(false);

        assertFalse(security.canViewImage(IMAGE_ID, other()));
    }
}
