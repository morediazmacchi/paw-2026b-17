package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CurrentUserAdviceTest {

    private final CurrentUserAdvice advice = new CurrentUserAdvice();

    // ---- currentUser: sale del principal de la sesion, sin ir a la base ----

    private static Authentication sessionOf(final long id, final String email, final String... roles) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, email, "", true,
                AuthorityUtils.createAuthorityList(roles));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    public void testCurrentUserCarriesTheIdAndEmailOfThePrincipalButNoName() {
        // Arrange
        final Authentication session = sessionOf(7, "ana@test.com", Role.USER.getAuthority());

        // Act
        final User result = advice.currentUserFromPrincipal(session);

        // Assert
        assertEquals(7L, result.getId().longValue());
        assertEquals("ana@test.com", result.getEmail());
        assertNull(result.getName());
    }

    @Test
    public void testCurrentUserCarriesTheRolesOfThePrincipal() {
        // Arrange
        final Authentication session = sessionOf(1, "root@test.com",
                Role.USER.getAuthority(), Role.ADMIN.getAuthority(), Role.SUPERADMIN.getAuthority());

        // Act
        final User result = advice.currentUserFromPrincipal(session);

        // Assert
        assertEquals(new HashSet<>(Arrays.asList(Role.USER, Role.ADMIN, Role.SUPERADMIN)), result.getRoles());
    }

    @Test
    public void testCurrentUserIsNullWithoutSession() {
        // Act
        final User result = advice.currentUserFromPrincipal(null);

        // Assert
        assertNull(result);
    }

    @Test
    public void testCurrentUserIsNullForAnAnonymousVisitor() {
        // Arrange
        final Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        // Act
        final User result = advice.currentUserFromPrincipal(anonymous);

        // Assert
        assertNull(result);
    }
}
