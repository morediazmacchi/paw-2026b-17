package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.UserAccessRevokedEvent;
import ar.edu.itba.paw.services.UserCredentialsChangedEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SessionExpiringListenerTest {

    private static final long TARGET_ID = 7L;
    private static final long OTHER_ID = 8L;

    private SessionRegistryImpl sessionRegistry;
    private SessionExpiringListener listener;

    @Before
    public void setUp() {
        sessionRegistry = new SessionRegistryImpl();
        listener = new SessionExpiringListener(sessionRegistry);
    }

    private static PlancitoUserDetails principal(final long id, final String email) {
        return new PlancitoUserDetails(id, email, "x", true,
                AuthorityUtils.createAuthorityList(Role.USER.getAuthority(), Role.ADMIN.getAuthority()));
    }

    @Test
    public void testTheSessionOfTheTargetUserIsExpired() {
        // Arrange
        sessionRegistry.registerNewSession("session-target", principal(TARGET_ID, "target@test.com"));

        // Act
        listener.onAccessRevoked(new UserAccessRevokedEvent(TARGET_ID));

        // Assert
        assertTrue(sessionRegistry.getSessionInformation("session-target").isExpired());
    }

    @Test
    public void testASessionOfAnotherUserIsNotExpired() {
        // Arrange: el objetivo y un senuelo (otro usuario) con sesion abierta
        sessionRegistry.registerNewSession("session-target", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-decoy", principal(OTHER_ID, "otro@test.com"));

        // Act
        listener.onAccessRevoked(new UserAccessRevokedEvent(TARGET_ID));

        // Assert
        assertTrue(sessionRegistry.getSessionInformation("session-target").isExpired());
        assertFalse(sessionRegistry.getSessionInformation("session-decoy").isExpired());
    }

    @Test
    public void testAllTheSessionsOfTheTargetUserAreExpired() {
        // Arrange: el mismo usuario logueado en dos navegadores
        sessionRegistry.registerNewSession("session-browser-1", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-browser-2", principal(TARGET_ID, "target@test.com"));

        // Act
        listener.onAccessRevoked(new UserAccessRevokedEvent(TARGET_ID));

        // Assert
        assertTrue(sessionRegistry.getSessionInformation("session-browser-1").isExpired());
        assertTrue(sessionRegistry.getSessionInformation("session-browser-2").isExpired());
    }

    // Se busca por id y no por el equals del principal (que compara solo el username): otro usuario con
    // el mismo email en el registro no cuenta como el objetivo.
    @Test
    public void testItMatchesByUserIdAndNotByUsername() {
        // Arrange
        sessionRegistry.registerNewSession("session-other-same-email", principal(OTHER_ID, "mismo@test.com"));

        // Act
        listener.onAccessRevoked(new UserAccessRevokedEvent(TARGET_ID));

        // Assert
        assertFalse(sessionRegistry.getSessionInformation("session-other-same-email").isExpired());
    }

    @Test
    public void testItDoesNothingWhenTheUserHasNoOpenSessions() {
        // Arrange
        sessionRegistry.registerNewSession("session-decoy", principal(OTHER_ID, "otro@test.com"));

        // Act
        listener.onAccessRevoked(new UserAccessRevokedEvent(TARGET_ID));

        // Assert
        assertFalse(sessionRegistry.getSessionInformation("session-decoy").isExpired());
    }

    // ---- cambio de contraseña (UserCredentialsChangedEvent) ----

    @After
    public void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    // El pedido en curso (el hilo que hace el commit) es el de la sesion "current-session".
    private static void currentRequestHasSession(final String sessionId) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(request.getSession(false)).thenReturn(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    public void testChangingThePasswordExpiresTheOtherSessionsButKeepsTheCurrentOne() {
        // Arrange: el mismo usuario en dos navegadores + un senuelo de otro usuario
        sessionRegistry.registerNewSession("current-session", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("stolen-session", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-decoy", principal(OTHER_ID, "otro@test.com"));
        currentRequestHasSession("current-session");

        // Act
        listener.onCredentialsChanged(new UserCredentialsChangedEvent(TARGET_ID, true));

        // Assert
        assertFalse(sessionRegistry.getSessionInformation("current-session").isExpired());
        assertTrue(sessionRegistry.getSessionInformation("stolen-session").isExpired());
        assertFalse(sessionRegistry.getSessionInformation("session-decoy").isExpired());
    }

    @Test
    public void testResettingThePasswordExpiresAllTheSessionsOfTheUser() {
        // Arrange: el pedido que restablece la clave puede traer una sesion anonima cualquiera
        sessionRegistry.registerNewSession("session-1", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-2", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-decoy", principal(OTHER_ID, "otro@test.com"));
        currentRequestHasSession("session-1");

        // Act
        listener.onCredentialsChanged(new UserCredentialsChangedEvent(TARGET_ID, false));

        // Assert
        assertTrue(sessionRegistry.getSessionInformation("session-1").isExpired());
        assertTrue(sessionRegistry.getSessionInformation("session-2").isExpired());
        assertFalse(sessionRegistry.getSessionInformation("session-decoy").isExpired());
    }

    @Test
    public void testWithoutARequestInProgressAllTheSessionsAreExpired() {
        // Arrange: p. ej. el commit corre en otro hilo; no hay cual conservar
        sessionRegistry.registerNewSession("session-1", principal(TARGET_ID, "target@test.com"));
        sessionRegistry.registerNewSession("session-2", principal(TARGET_ID, "target@test.com"));

        // Act
        listener.onCredentialsChanged(new UserCredentialsChangedEvent(TARGET_ID, true));

        // Assert
        assertTrue(sessionRegistry.getSessionInformation("session-1").isExpired());
        assertTrue(sessionRegistry.getSessionInformation("session-2").isExpired());
    }
}
