package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.EventService;
import ar.edu.itba.paw.services.ModerationService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AdminSecurityTest {

    private static final long CALLER_ID = 1L;
    private static final long TARGET_ID = 2L;

    private static final long EVENT_ID = 7L;
    private static final long REPORT_ID = 40L;
    private static final long OTHER_CREATOR_ID = 3L;

    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private ModerationService moderationService;

    private AdminSecurity security;

    @Before
    public void setUp() {
        security = new AdminSecurity(userService, eventService, moderationService);
    }

    private static Event eventOf(final Long creatorId) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        if (creatorId != null) {
            final User creator = new User("creador@test.com", "Creador");
            creator.setId(creatorId);
            event.setCreator(creator);
        }
        return event;
    }

    // El que actua, tal como queda en la sesion despues del login.
    private static Authentication caller(final Role... roles) {
        final String[] authorities = Arrays.stream(roles).map(Role::getAuthority).toArray(String[]::new);
        final PlancitoUserDetails principal = new PlancitoUserDetails(CALLER_ID, "admin@test.com", "Admin", "x", true,
                AuthorityUtils.createAuthorityList(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static User target(final Role... roles) {
        final User user = new User("target@test.com", "Target");
        user.setId(TARGET_ID);
        user.setRoles(new HashSet<>(Arrays.asList(roles)));
        return user;
    }

    // ---- canSuspend() ----

    @Test
    public void testAnAdminCanSuspendANormalUser() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER)));

        assertTrue(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotSuspendHimself() {
        when(userService.findById(CALLER_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertFalse(security.canSuspend(CALLER_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testASuperadminCannotSuspendHimself() {
        when(userService.findById(CALLER_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canSuspend(CALLER_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testAnAdminCannotSuspendAnotherAdmin() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertFalse(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotSuspendASuperadmin() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testASuperadminCanSuspendAnAdmin() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertTrue(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testCannotSuspendAUserWhoDoesNotExist() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertFalse(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotSuspendWithoutSession() {
        final Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(security.canSuspend(TARGET_ID, anonymous));
    }

    // ---- canRevokeAdmin() ----

    @Test
    public void testASuperadminCanRevokeAnotherAdmin() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertTrue(security.canRevokeAdmin(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testASuperadminCannotRevokeHisOwnAdminRole() {
        when(userService.findById(CALLER_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canRevokeAdmin(CALLER_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testCannotRevokeAdminOfAUserWhoDoesNotExist() {
        when(userService.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertFalse(security.canRevokeAdmin(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testCannotRevokeAdminWithoutSession() {
        assertFalse(security.canRevokeAdmin(TARGET_ID, null));
    }

    // ---- canModerateEvent() ----

    @Test
    public void testAnAdminCanModerateAnEventOfSomeoneElse() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventOf(OTHER_CREATOR_ID)));

        assertTrue(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotModerateHisOwnEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventOf(CALLER_ID)));

        assertFalse(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testASuperadminCannotModerateHisOwnEitherEvent() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventOf(CALLER_ID)));

        assertFalse(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testAnAdminCanModerateAnEventWithoutCreator() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(eventOf(null)));

        assertTrue(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotModerateAnEventThatDoesNotExist() {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotModerateAnEventWithoutSession() {
        assertFalse(security.canModerateEvent(EVENT_ID, null));
    }

    // ---- canResolveReport() ----

    @Test
    public void testAnAdminCanResolveAReportAboutAnEventOfSomeoneElse() {
        when(moderationService.findReportTarget(REPORT_ID))
                .thenReturn(Optional.of(new ReportTarget(EVENT_ID, OTHER_CREATOR_ID)));

        assertTrue(security.canResolveReport(REPORT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotResolveAReportAboutHisOwnEvent() {
        when(moderationService.findReportTarget(REPORT_ID))
                .thenReturn(Optional.of(new ReportTarget(EVENT_ID, CALLER_ID)));

        assertFalse(security.canResolveReport(REPORT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCanResolveAReportAboutAnEventWithoutCreator() {
        when(moderationService.findReportTarget(REPORT_ID))
                .thenReturn(Optional.of(new ReportTarget(EVENT_ID, null)));

        assertTrue(security.canResolveReport(REPORT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotResolveAReportThatDoesNotExist() {
        when(moderationService.findReportTarget(REPORT_ID)).thenReturn(Optional.empty());

        assertFalse(security.canResolveReport(REPORT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotResolveAReportWithoutSession() {
        assertFalse(security.canResolveReport(REPORT_ID, null));
    }
}
