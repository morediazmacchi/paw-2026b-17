package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.UserAccessRevokedEvent;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.session.SessionRegistryImpl;


import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        return new PlancitoUserDetails(id, email, "Nombre", "x", true,
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
}
