package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventAccessService;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ImageService;
import ar.edu.itba.paw.services.RegistrationService;
import ar.edu.itba.paw.services.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private UserService userService;

    private EventSecurity security;
    private User otherUser;

    @Before
    public void setUp() {
        security = new EventSecurity(eventService, registrationService, eventAccessService, imageService, userService);
        lenient().when(userService.findByEmail(CREATOR_EMAIL)).thenReturn(Optional.of(user(CREATOR_ID)));
        otherUser = user(OTHER_ID);
        lenient().when(userService.findByEmail(OTHER_EMAIL)).thenReturn(Optional.of(otherUser));
    }

    private static User user(final long id) {
        final User user = new User();
        user.setId(id);
        return user;
    }

    private static Authentication auth(final String email) {
        final Authentication authentication = new TestingAuthenticationToken(email, "x", "ROLE_USER");
        authentication.setAuthenticated(true);
        return authentication;
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

        assertTrue(security.isOwner(EVENT_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testIsOwnerIsFalseForAnotherUser() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwner(EVENT_ID, auth(OTHER_EMAIL)));
    }

    @Test
    public void testIsOwnerIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwner(EVENT_ID, auth(CREATOR_EMAIL)));
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
    public void testIsOwnerIsFalseWhenTheUserDoesNotExist() {
        when(userService.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertFalse(security.isOwner(EVENT_ID, auth("ghost@test.com")));
    }

    // ---- isOwnerOfPrivate() ----

    @Test
    public void testIsOwnerOfPrivateIsTrueForTheCreatorOfAPrivateEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertTrue(security.isOwnerOfPrivate(EVENT_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testIsOwnerOfPrivateIsFalseForAPublicEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwnerOfPrivate(EVENT_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testIsOwnerOfPrivateIsFalseForAnotherUser() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PRIVATE)));

        assertFalse(security.isOwnerOfPrivate(EVENT_ID, auth(OTHER_EMAIL)));
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

        assertTrue(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationBelongsToAnotherEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        final Registration registration = registrationOfEvent(OTHER_EVENT_ID);
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.of(registration));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheUserDoesNotOwnTheEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, auth(OTHER_EMAIL)));
    }

    @Test
    public void testIsOwnerOfRegistrationEventIsFalseWhenTheRegistrationDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event(EVENT_ID, EventVisibility.PUBLIC)));
        when(registrationService.findById(REGISTRATION_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwnerOfRegistrationEvent(EVENT_ID, REGISTRATION_ID, auth(CREATOR_EMAIL)));
    }

    // ---- canView() ----

    @Test
    public void testCanViewDelegatesToEventAccessService() {
        final Event event = event(EVENT_ID, EventVisibility.PRIVATE);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventAccessService.canView(event, otherUser)).thenReturn(false);

        assertFalse(security.canView(EVENT_ID, auth(OTHER_EMAIL)));
    }

    @Test
    public void testCanViewIsFalseWhenTheEventDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canView(EVENT_ID, auth(OTHER_EMAIL)));
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

        assertFalse(security.canViewImage(IMAGE_ID, auth(CREATOR_EMAIL)));
    }

    @Test
    public void testCanViewImageOfAPublicEventIsTrueForAnAnonymousVisitorWithoutLookingUpAUser() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, false, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(true);

        assertTrue(security.canViewImage(IMAGE_ID, anonymous()));
        verify(userService, never()).findByEmail(anyString());
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
        when(eventAccessService.canView(access, otherUser)).thenReturn(true);

        assertTrue(security.canViewImage(IMAGE_ID, auth(OTHER_EMAIL)));
    }

    @Test
    public void testCanViewImageOfAPrivateEventIsFalseWhenTheLoggedUserCannotViewIt() {
        final EventAccessSummary access = new EventAccessSummary(EVENT_ID, CREATOR_ID, true, true);
        when(imageService.findOwnerEventAccess(IMAGE_ID)).thenReturn(Optional.of(access));
        when(eventAccessService.canView(access, null)).thenReturn(false);
        when(eventAccessService.canView(access, otherUser)).thenReturn(false);

        assertFalse(security.canViewImage(IMAGE_ID, auth(OTHER_EMAIL)));
    }
}
