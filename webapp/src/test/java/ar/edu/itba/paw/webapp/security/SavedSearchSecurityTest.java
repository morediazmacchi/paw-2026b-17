package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.services.SavedSearchService;
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

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SavedSearchSecurityTest {

    private static final long SEARCH_ID = 5L;
    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Mock
    private SavedSearchService savedSearchService;

    private SavedSearchSecurity security;

    @Before
    public void setUp() {
        security = new SavedSearchSecurity(savedSearchService);
    }

    // Lo que hay en la sesion despues del login: el principal es un PlancitoUserDetails.
    private static Authentication sessionOf(final long id) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, "u" + id + "@test.com", "x", true,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    public void testTheOwnerOfTheSearchIsAllowed() {
        when(savedSearchService.findOwnerId(SEARCH_ID)).thenReturn(Optional.of(OWNER_ID));

        assertTrue(security.isOwner(SEARCH_ID, sessionOf(OWNER_ID)));
    }

    @Test
    public void testSomeoneElseIsDenied() {
        when(savedSearchService.findOwnerId(SEARCH_ID)).thenReturn(Optional.of(OWNER_ID));

        assertFalse(security.isOwner(SEARCH_ID, sessionOf(OTHER_ID)));
    }

    @Test
    public void testASearchThatDoesNotExistIsDenied() {
        when(savedSearchService.findOwnerId(SEARCH_ID)).thenReturn(Optional.empty());

        assertFalse(security.isOwner(SEARCH_ID, sessionOf(OWNER_ID)));
    }

    @Test
    public void testWithoutSessionIsDenied() {
        final Authentication anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertFalse(security.isOwner(SEARCH_ID, anonymous));
    }
}
