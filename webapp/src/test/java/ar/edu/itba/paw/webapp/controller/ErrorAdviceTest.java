package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.UserNotFoundException;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ErrorAdviceTest {

    private ErrorAdvice errorAdvice;

    @Before
    public void setUp() {
        errorAdvice = new ErrorAdvice();
    }

    @Test
    public void testAccessDeniedForAUserWithSessionShowsTheNotFoundPage() {
        // Arrange
        final Principal principal = () -> "ana@test.com";

        // Act
        final ModelAndView result = errorAdvice.accessDenied(new AccessDeniedException("no"), principal);

        // Assert
        assertEquals("errors/404", result.getViewName());
    }

    @Test
    public void testAUserThatDoesNotExistShowsTheNotFoundPage() {
        // Act
        final ModelAndView result = errorAdvice.userNotFound(new UserNotFoundException(5));

        // Assert
        assertEquals("errors/404", result.getViewName());
    }

    @Test(expected = AccessDeniedException.class)
    public void testAccessDeniedWithoutSessionRethrowsSoSpringSecuritySendsTheVisitorToLogin() {
        // Act
        errorAdvice.accessDenied(new AccessDeniedException("no"), null);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- currentUser: sale del principal de la sesion, sin ir a la base ----

    private static Authentication sessionOf(final long id, final String email, final String name, final String... roles) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, email, name, "", true,
                AuthorityUtils.createAuthorityList(roles));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    public void testCurrentUserCarriesTheIdEmailAndNameOfThePrincipal() {
        // Arrange
        final Authentication session = sessionOf(7, "ana@test.com", "Ana", Role.USER.getAuthority());

        // Act
        final User result = errorAdvice.currentUserFromPrincipal(session);

        // Assert
        assertEquals(7L, result.getId().longValue());
        assertEquals("ana@test.com", result.getEmail());
        assertEquals("Ana", result.getName());
    }

    @Test
    public void testCurrentUserCarriesTheRolesOfThePrincipal() {
        // Arrange
        final Authentication session = sessionOf(1, "root@test.com", "Root",
                Role.USER.getAuthority(), Role.ADMIN.getAuthority(), Role.SUPERADMIN.getAuthority());

        // Act
        final User result = errorAdvice.currentUserFromPrincipal(session);

        // Assert
        assertEquals(new HashSet<>(Arrays.asList(Role.USER, Role.ADMIN, Role.SUPERADMIN)), result.getRoles());
    }

    @Test
    public void testCurrentUserIsNullWithoutSession() {
        // Act
        final User result = errorAdvice.currentUserFromPrincipal(null);

        // Assert
        assertNull(result);
    }

    @Test
    public void testCurrentUserIsNullForAnAnonymousVisitor() {
        // Arrange
        final Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        // Act
        final User result = errorAdvice.currentUserFromPrincipal(anonymous);

        // Assert
        assertNull(result);
    }
}
