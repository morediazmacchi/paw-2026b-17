package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlancitoUserDetailsTest {

    private static PlancitoUserDetails user(final Role... roles) {
        final String[] authorities = java.util.Arrays.stream(roles).map(Role::getAuthority).toArray(String[]::new);
        return new PlancitoUserDetails(7L, "ana@test.com", "Ana", "x", true, AuthorityUtils.createAuthorityList(authorities));
    }

    @Test
    public void testItExposesTheIdTheEmailAndTheName() {
        final PlancitoUserDetails user = user(Role.USER);

        assertEquals(7L, user.getId());
        assertEquals("ana@test.com", user.getEmail());
        assertEquals("Ana", user.getName());
    }

    @Test
    public void testHasRoleIsTrueForARoleItHas() {
        assertTrue(user(Role.USER, Role.ADMIN).hasRole(Role.ADMIN));
    }

    @Test
    public void testHasRoleIsFalseForARoleItDoesNotHave() {
        assertFalse(user(Role.USER, Role.ADMIN).hasRole(Role.SUPERADMIN));
    }

    @Test
    public void testFromReturnsThePrincipalOfAnAuthenticatedSession() {
        final PlancitoUserDetails principal = user(Role.USER);
        final Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        assertEquals(principal, PlancitoUserDetails.from(authentication).get());
    }

    @Test
    public void testFromIsEmptyWithoutAuthentication() {
        assertFalse(PlancitoUserDetails.from(null).isPresent());
    }

    @Test
    public void testFromIsEmptyForAnAnonymousVisitor() {
        final Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(PlancitoUserDetails.from(anonymous).isPresent());
    }

    @Test
    public void testFromIsEmptyWhenThePrincipalIsNotAPlancitoUserDetails() {
        final Authentication authentication = new UsernamePasswordAuthenticationToken("otro@test.com", null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        assertFalse(PlancitoUserDetails.from(authentication).isPresent());
    }

    @Test
    public void testFromIsEmptyWhenTheAuthenticationIsNotAuthenticated() {
        final Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.isAuthenticated()).thenReturn(false);

        assertFalse(PlancitoUserDetails.from(authentication).isPresent());
    }

    @Test
    public void testAnEnabledAndUnlockedAccountIsActive() {
        assertTrue(new PlancitoUserDetails(7L, "ana@test.com", "Ana", "x", true, true, AuthorityUtils.NO_AUTHORITIES).isActive());
    }

    @Test
    public void testASuspendedAccountIsNotActiveEvenIfEnabled() {
        assertFalse(new PlancitoUserDetails(7L, "ana@test.com", "Ana", "x", true, false, AuthorityUtils.NO_AUTHORITIES).isActive());
    }

    @Test
    public void testAnUnverifiedAccountIsNotActive() {
        assertFalse(new PlancitoUserDetails(7L, "ana@test.com", "Ana", "x", false, true, AuthorityUtils.NO_AUTHORITIES).isActive());
    }
}
