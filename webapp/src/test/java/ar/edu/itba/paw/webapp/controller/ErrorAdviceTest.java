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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
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
        final User result = errorAdvice.currentUserFromPrincipal(session);

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

    // ---- un archivo que supera el limite del servidor: vuelve a la pantalla de donde vino ----

    private static MockHttpServletRequest postTo(final String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        // Lo prepara el DispatcherServlet antes de cada pedido.
        request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, new FlashMap());
        return request;
    }

    @Test
    public void testAFileTooLargeWhenPublishingGoesBackToPublish() {
        // Act
        final ModelAndView result = errorAdvice.imageTooLarge(new MaxUploadSizeExceededException(15_728_640L),
                postTo("/eventos/publicar"), sessionOf(7, "ana@test.com", Role.USER.getAuthority()));

        // Assert
        assertEquals("redirect:/eventos/publicar?imageError=true", result.getViewName());
    }

    @Test
    public void testAFileTooLargeWhenEditingAnEventGoesBackToEditingThatEvent() {
        // Act
        final ModelAndView result = errorAdvice.imageTooLarge(new MaxUploadSizeExceededException(15_728_640L),
                postTo("/eventos/42/editar"), sessionOf(7, "ana@test.com", Role.USER.getAuthority()));

        // Assert
        assertEquals("redirect:/eventos/42/editar?imageError=true", result.getViewName());
    }

    @Test
    public void testAFileTooLargeAsACoverGoesBackToTheProfileOfWhoUploadedIt() {
        // Arrange
        final MockHttpServletRequest request = postTo("/perfil/portada");

        // Act
        final ModelAndView result = errorAdvice.imageTooLarge(new MaxUploadSizeExceededException(15_728_640L), request,
                sessionOf(7, "ana@test.com", Role.USER.getAuthority()));

        // Assert
        assertEquals("redirect:/usuarios/7", result.getViewName());
    }

    @Test
    public void testAFileTooLargeAsACoverCarriesTheTooLargeMessageAsAFlashAttribute() {
        // Arrange
        final MockHttpServletRequest request = postTo("/perfil/portada");

        // Act
        errorAdvice.imageTooLarge(new MaxUploadSizeExceededException(15_728_640L), request,
                sessionOf(7, "ana@test.com", Role.USER.getAuthority()));

        // Assert
        final FlashMap flash = (FlashMap) request.getAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE);
        assertEquals("TOO_LARGE", flash.get("coverMessage"));
    }

    @Test
    public void testARequestThatIsNotMultipartIsABadRequestNotATooLargeFile() {
        // Act
        final ModelAndView result = errorAdvice.malformedUpload(new MultipartException("no es multipart"));

        // Assert
        assertEquals("errors/404", result.getViewName());
    }
}
