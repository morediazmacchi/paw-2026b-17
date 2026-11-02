package ar.edu.itba.paw.webapp.interceptor;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UnreadInboxInterceptorTest {

    private static final long USER_ID = 7L;
    private static final long OTHER_USER_ID = 8L;

    @Mock
    private InboxService inboxService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private UnreadInboxInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = new UnreadInboxInterceptor(inboxService);
    }

    private static Principal sessionOf(final long id) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, "ana@test.com", "Ana", "", true,
                AuthorityUtils.createAuthorityList(Role.USER.getAuthority()));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    public void testTheCountComesFromTheIdOfThePrincipal() {
        // Arrange: el conteo de otro usuario es un senuelo
        lenient().when(inboxService.countUnread(OTHER_USER_ID)).thenReturn(99L);
        when(inboxService.countUnread(USER_ID)).thenReturn(3L);
        when(request.getUserPrincipal()).thenReturn(sessionOf(USER_ID));
        final ModelAndView mav = new ModelAndView("events/list");

        // Act
        interceptor.postHandle(request, response, new Object(), mav);

        // Assert
        assertEquals(3L, mav.getModel().get("unreadInboxCount"));
    }

    @Test
    public void testThereIsNoCountWithoutSession() {
        // Arrange
        when(request.getUserPrincipal()).thenReturn(null);
        final ModelAndView mav = new ModelAndView("events/list");

        // Act
        interceptor.postHandle(request, response, new Object(), mav);

        // Assert
        assertFalse(mav.getModel().containsKey("unreadInboxCount"));
    }

    @Test
    public void testThereIsNoCountForAnAnonymousVisitor() {
        // Arrange
        when(request.getUserPrincipal()).thenReturn(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        final ModelAndView mav = new ModelAndView("events/list");

        // Act
        interceptor.postHandle(request, response, new Object(), mav);

        // Assert
        assertFalse(mav.getModel().containsKey("unreadInboxCount"));
    }

    @Test
    public void testThereIsNoCountOnARedirect() {
        // Arrange
        lenient().when(request.getUserPrincipal()).thenReturn(sessionOf(USER_ID));
        final ModelAndView mav = new ModelAndView("redirect:/eventos");

        // Act
        interceptor.postHandle(request, response, new Object(), mav);

        // Assert
        assertFalse(mav.getModel().containsKey("unreadInboxCount"));
    }
}
