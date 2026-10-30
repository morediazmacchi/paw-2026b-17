package ar.edu.itba.paw.webapp.controller;

import ar.edu.itba.paw.services.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ErrorAdviceTest {

    @Mock
    private UserService userService;

    private ErrorAdvice errorAdvice;

    @Before
    public void setUp() {
        errorAdvice = new ErrorAdvice(userService);
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

    @Test(expected = AccessDeniedException.class)
    public void testAccessDeniedWithoutSessionRethrowsSoSpringSecuritySendsTheVisitorToLogin() {
        // Act
        errorAdvice.accessDenied(new AccessDeniedException("no"), null);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }
}
