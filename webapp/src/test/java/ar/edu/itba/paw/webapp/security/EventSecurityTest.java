package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
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

    private EventSecurity security;

    @Before
    public void setUp() {
        security = new EventSecurity(eventService, registrationService, eventAccessService, imageService);
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

    private static Event event(final long id, final EventVisibility visibility) {
        final Event event = new Event();
        event.setId(id);
        event.setCreator(user(CREATOR_ID));
        event.setVisibility(visibility);
        return event;
    }

    // ---- isOwner() ----

    @Test
    public void testIsOwnerIsTrueForTheCreator() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertTrue(security.isOwner(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerIsFalseForAnotherUser() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwner(EVENT_ID, other()));
    }

    @Test
    public void testIsOwnerIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

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

    // ---- isOwnerOfPrivate() ----

    @Test
    public void testIsOwnerOfPrivateIsTrueForTheCreatorOfAPrivateEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertTrue(security.isOwnerOfPrivate(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerOfPrivateIsFalseForAPublicEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwnerOfPrivate(EVENT_ID, creator()));
    }

    @Test
    public void testIsOwnerOfPrivateIsFalseForAnotherUser() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertFalse(security.isOwnerOfPrivate(EVENT_ID, other()));
    }

    // ---- isOwnerOfRegistrationEvent() ----

    private Registration registrationOfEvent(final long eventId) {
        final Registration registration = mock(Registration.class);
        when(registration.getEventId()).thenReturn(eventId);
        return registration;
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsTrueWhenTheRegistrationBelongsToTheOwnedEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        final Registration registration = registrationOfEvent(EVENT_ID);
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertTrue(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationBelongsToAnotherEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        final Registration registration = registrationOfEvent(OTHER_EVENT_ID);
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheUserDoesNotOwnTheEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, other()));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, creator()));
    }

    // ---- isRegistrant() ----

    private Registration registrationOfUser(final long userId) {
        final Registration registration = mock(Registration.class);
        when(registration.getUserId()).thenReturn(userId);
        return registration;
    }

    @Test
    public void testIsRegistrantIsTrueForTheOwnerOfTheRegistration() {
        final Registration registration = registrationOfUser(CREATOR_ID);
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertTrue(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseForTheRegistrationOfAnotherUser() {
        final Registration registration = registrationOfUser(OTHER_ID);
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseWhenTheRegistrationDoesNotExist() {
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.empty());

        assertFalse(security.isRegistrant(REGISTRATION_ID, creator()));
    }

    @Test
    public void testIsRegistrantIsFalseWithoutAuthentication() {
        assertFalse(security.isRegistrant(REGISTRATION_ID, null));
    }

    // ---- canView() ----

    @Test
    public void testCanViewIsTrueWhenEventAccessServiceAllowsTheIdOfThePrincipal() {
        final Event event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le pregunta por el id del usuario de la sesion y sin ser admin.
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), false)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, other()));
    }

    @Test
    public void testCanViewTellsEventAccessServiceWhenThePrincipalIsAdmin() {
        final Event event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le avisa que es admin (sale de las authorities del principal).
        when(eventAccessService.canView(event, Long.valueOf(OTHER_ID), true)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, auth(OTHER_ID, OTHER_EMAIL, Role.USER.getAuthority(), Role.ADMIN.getAuthority())));
    }

    @Test
    public void testCanViewTreatsAnAnonymousVisitorAsSomeoneWithoutSession() {
        final Event event = event(EVENT_ID, EventVisibility.PUBLIC);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        // Solo responde que si se le pregunta sin usuario y sin admin.
        when(eventAccessService.canView(event, null, false)).thenReturn(true);

        assertTrue(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseForAnAnonymousVisitorWhenEventAccessServiceDenies() {
        final Event event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventAccessService.canView(event, null, false)).thenReturn(false);

        assertFalse(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseForAnAnonymousVisitorWhenTheEventDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canView(EVENT_ID, anonymous()));
    }

    @Test
    public void testCanViewIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canView(EVENT_ID, other()));
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
