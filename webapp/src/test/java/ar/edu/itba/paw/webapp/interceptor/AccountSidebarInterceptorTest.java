package ar.edu.itba.paw.webapp.interceptor;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.AccountCounts;
import ar.edu.itba.paw.services.AccountSummaryService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AccountSidebarInterceptorTest {

    private static final long USER_ID = 7L;

    @Mock
    private AccountSummaryService accountSummaryService;
    @Mock
    private UserService userService;

    private AccountSidebarInterceptor interceptor;

    @Before
    public void setUp() {
        interceptor = new AccountSidebarInterceptor(accountSummaryService, userService);
    }

    private static MockHttpServletRequest requestOf(final long id, final String... authorities) {
        final PlancitoUserDetails principal = new PlancitoUserDetails(id, "ana@test.com", "", true,
                AuthorityUtils.createAuthorityList(authorities));
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return request;
    }

    private static User accountNamed(final String name) {
        final User user = new User("ana@test.com", name);
        user.setId(USER_ID);
        return user;
    }

    @Test
    public void testAPageOfTheAccountGetsTheCountsOfWhoIsLookingAtIt() {
        // Arrange
        when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(3, 4, 5, 6));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(accountNamed("Ana")));
        final ModelAndView mav = new ModelAndView("auth/favoritos");

        // Act
        interceptor.postHandle(requestOf(USER_ID, Role.USER.getAuthority()), new MockHttpServletResponse(), null, mav);

        // Assert
        assertEquals(4L, ((AccountCounts) mav.getModel().get("accountCounts")).getFavorites());
    }

    @Test
    public void testAPageOfTheAccountGetsTheCurrentNameOfWhoIsLookingAtIt() {
        // Arrange
        lenient().when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(0, 0, 0, 0));
        when(userService.findById(USER_ID)).thenReturn(Optional.of(accountNamed("Ana")));
        final ModelAndView mav = new ModelAndView("auth/favoritos");

        // Act
        interceptor.postHandle(requestOf(USER_ID, Role.USER.getAuthority()), new MockHttpServletResponse(), null, mav);

        // Assert
        assertEquals("Ana", ((User) mav.getModel().get("user")).getName());
    }

    @Test
    public void testAnAdminGetsTheCountsOfAnAdmin() {
        // Arrange: el admin ve todos los favoritos; con el flag equivocado el total daria otro
        when(accountSummaryService.countsFor(USER_ID, true)).thenReturn(new AccountCounts(0, 9, 0, 0));
        lenient().when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(0, 1, 0, 0));
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(accountNamed("Ana")));
        final ModelAndView mav = new ModelAndView("auth/favoritos");

        // Act
        interceptor.postHandle(requestOf(USER_ID, Role.USER.getAuthority(), Role.ADMIN.getAuthority()),
                new MockHttpServletResponse(), null, mav);

        // Assert
        assertEquals(9L, ((AccountCounts) mav.getModel().get("accountCounts")).getFavorites());
    }

    @Test
    public void testTheUserThatTheHandlerAlreadyPutIsNotReplaced() {
        // Arrange: senuelo, con otro nombre, que pisaria al que puso el handler si se lo volviera a leer
        lenient().when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(0, 0, 0, 0));
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(accountNamed("Otro")));
        final ModelAndView mav = new ModelAndView("auth/perfil");
        mav.addObject("user", accountNamed("Ana"));

        // Act
        interceptor.postHandle(requestOf(USER_ID, Role.USER.getAuthority()), new MockHttpServletResponse(), null, mav);

        // Assert
        assertEquals("Ana", ((User) mav.getModel().get("user")).getName());
    }

    @Test
    public void testARedirectGetsNothingBecauseItDrawsNoPage() {
        // Arrange: senuelos que llenarian el model si se consultara igual
        lenient().when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(1, 1, 1, 1));
        lenient().when(userService.findById(USER_ID)).thenReturn(Optional.of(accountNamed("Ana")));
        final ModelAndView mav = new ModelAndView("redirect:/perfil/listas");

        // Act
        interceptor.postHandle(requestOf(USER_ID, Role.USER.getAuthority()), new MockHttpServletResponse(), null, mav);

        // Assert
        assertFalse(mav.getModel().containsKey("accountCounts") || mav.getModel().containsKey("user"));
    }

    @Test
    public void testWithoutSessionGetsNothing() {
        // Arrange
        lenient().when(accountSummaryService.countsFor(USER_ID, false)).thenReturn(new AccountCounts(1, 1, 1, 1));
        final ModelAndView mav = new ModelAndView("auth/favoritos");

        // Act
        interceptor.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), null, mav);

        // Assert
        assertFalse(mav.getModel().containsKey("accountCounts"));
    }
}
