package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.webapp.config.WebAuthConfig;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import static org.junit.Assert.assertEquals;

/**
 * A donde manda el login fallido segun el TIPO de excepcion (ya no vuelve a buscar al usuario): sin la clave correcta
 * todo es el mismo "?error"; con la clave correcta, LockedException = suspendida y DisabledException = sin confirmar.
 */
public class LoginFailureHandlerTest {

    private final AuthenticationFailureHandler handler = new WebAuthConfig().loginFailureHandler();

    private String redirectFor(final AuthenticationException exception, final String email) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("email", email);
        final MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationFailure(request, response, exception);
        return response.getRedirectedUrl();
    }

    @Test
    public void testBadCredentialsGoToTheGenericError() throws Exception {
        assertEquals("/login?error", redirectFor(new BadCredentialsException("x"), "ana@mail.com"));
    }

    @Test
    public void testASuspendedAccountWithTheRightPasswordGoesToSuspended() throws Exception {
        assertEquals("/login?suspended&email=ana%40mail.com", redirectFor(new LockedException("x"), "ana@mail.com"));
    }

    @Test
    public void testAnUnverifiedAccountWithTheRightPasswordGoesToUnverified() throws Exception {
        assertEquals("/login?unverified&email=ana%40mail.com", redirectFor(new DisabledException("x"), "ana@mail.com"));
    }

    @Test
    public void testAMissingEmailParameterStillRedirectsWithAnEmptyEmail() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        final MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new LockedException("x"));

        assertEquals("/login?suspended&email=", response.getRedirectedUrl());
    }
}
