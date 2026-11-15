package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.UserAccess;
import ar.edu.itba.paw.model.EventAccessSummary;
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

    // Lo que devuelve la consulta de 4 columnas del evento (id, organizador, privado, aprobado).
    private static EventAccessSummary eventOf(final Long creatorId) {
        return new EventAccessSummary(EVENT_ID, creatorId, false, true);
    }

    // El que actua, tal como queda en la sesion despues del login.
    private static Authentication caller(final Role... roles) {
        final String[] authorities = Arrays.stream(roles).map(Role::getAuthority).toArray(String[]::new);
        final PlancitoUserDetails principal = new PlancitoUserDetails(CALLER_ID, "admin@test.com", "x", true,
                AuthorityUtils.createAuthorityList(authorities));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    // Lo que devuelve la consulta de id y roles del usuario objetivo.
    private static UserAccess target(final Role... roles) {
        return new UserAccess(TARGET_ID, new HashSet<>(Arrays.asList(roles)));
    }

    // ---- canSuspend() ----

    @Test
    public void testAnAdminCanSuspendANormalUser() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER)));

        assertTrue(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testNobodyCanSuspendThemselves() {
        when(userService.findAccessById(CALLER_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canSuspend(CALLER_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testAnAdminCannotSuspendAnotherAdmin() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertFalse(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotSuspendASuperadmin() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testASuperadminCanSuspendAnAdmin() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertTrue(security.canSuspend(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testCannotSuspendAUserWhoDoesNotExist() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.empty());

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
    public void testASuperadminCanRevokeTheAdminRoleOfAnotherUser() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN)));

        assertTrue(security.canRevokeAdmin(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testNobodyCanRevokeTheirOwnAdminRole() {
        when(userService.findAccessById(CALLER_ID)).thenReturn(Optional.of(target(Role.USER, Role.ADMIN, Role.SUPERADMIN)));

        assertFalse(security.canRevokeAdmin(CALLER_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testCannotRevokeTheAdminRoleOfAUserWhoDoesNotExist() {
        when(userService.findAccessById(TARGET_ID)).thenReturn(Optional.empty());

        assertFalse(security.canRevokeAdmin(TARGET_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    // ---- canModerateEvent() ----

    @Test
    public void testAnAdminCanModerateAnEventOfSomeoneElse() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(eventOf(OTHER_CREATOR_ID)));

        assertTrue(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testAnAdminCannotModerateHisOwnEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(eventOf(CALLER_ID)));

        assertFalse(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testASuperadminCannotModerateHisOwnEitherEvent() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(eventOf(CALLER_ID)));

        assertFalse(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN, Role.SUPERADMIN)));
    }

    @Test
    public void testAnAdminCanModerateAnEventWithoutCreator() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.of(eventOf(null)));

        assertTrue(security.canModerateEvent(EVENT_ID, caller(Role.USER, Role.ADMIN)));
    }

    @Test
    public void testCannotModerateAnEventThatDoesNotExist() {
        when(eventService.findAccessById(EVENT_ID)).thenReturn(Optional.empty());

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
