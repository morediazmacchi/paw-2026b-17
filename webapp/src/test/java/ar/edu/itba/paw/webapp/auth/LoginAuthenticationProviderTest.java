package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;

/**
 * El login armado igual que en WebAuthConfig.authenticationProvider() (UserDetailsServiceImpl real + BCrypt +
 * chequeo de estado despues de la clave): con la clave INCORRECTA todas las cuentas dan el mismo
 * BadCredentialsException; solo con la CORRECTA se le dice el estado a la persona.
 */
@RunWith(MockitoJUnitRunner.class)
public class LoginAuthenticationProviderTest {

    private static final String RAW_PASSWORD = "Clave-Correcta-123";

    @Mock
    private UserService userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private AuthenticationProvider provider;
    private String correctHash;

    @Before
    public void setUp() {
        correctHash = encoder.encode(RAW_PASSWORD);
        final DaoAuthenticationProvider dao = new DaoAuthenticationProvider();
        dao.setUserDetailsService(new UserDetailsServiceImpl(userService));
        dao.setPasswordEncoder(encoder);
        dao.setPreAuthenticationChecks(user -> { });
        dao.setPostAuthenticationChecks(new AccountStatusAfterPasswordCheck());
        provider = dao;
    }

    private User account(final String email, final String hash, final boolean verified, final boolean suspended) {
        final User user = new User(email, "Nombre");
        user.setId(1L);
        user.setPassword(hash);
        user.setVerified(verified);
        user.setSuspended(suspended);
        user.setRoles(new HashSet<>(Collections.singletonList(Role.USER)));
        lenient().when(userService.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private Authentication login(final String email, final String password) {
        return provider.authenticate(new UsernamePasswordAuthenticationToken(email, password));
    }

    @Test(expected = BadCredentialsException.class)
    public void testWrongPasswordOnASuspendedAccountIsAGenericError() {
        account("susp@mail.com", correctHash, true, true);

        login("susp@mail.com", "clave-mala");
    }

    @Test(expected = BadCredentialsException.class)
    public void testWrongPasswordOnAnUnverifiedAccountIsAGenericError() {
        account("sinverificar@mail.com", correctHash, false, false);

        login("sinverificar@mail.com", "clave-mala");
    }

    @Test(expected = BadCredentialsException.class)
    public void testAnyPasswordOnAGhostAccountIsAGenericError() {
        account("fantasma@mail.com", null, true, false);

        login("fantasma@mail.com", "clave-mala");
    }

    @Test(expected = BadCredentialsException.class)
    public void testWrongPasswordOnAnAccountThatDoesNotExistIsAGenericError() {
        lenient().when(userService.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        login("nadie@mail.com", "clave-mala");
    }

    @Test(expected = DisabledException.class)
    public void testCorrectPasswordOnASuspendedAccountIsStillBlocked() {
        account("susp@mail.com", correctHash, true, true);

        login("susp@mail.com", RAW_PASSWORD);
    }

    @Test(expected = DisabledException.class)
    public void testCorrectPasswordOnAnUnverifiedAccountIsStillBlocked() {
        account("sinverificar@mail.com", correctHash, false, false);

        login("sinverificar@mail.com", RAW_PASSWORD);
    }

    @Test
    public void testCorrectPasswordOnAnActiveAccountAuthenticates() {
        account("ana@mail.com", correctHash, true, false);

        final Authentication result = login("ana@mail.com", RAW_PASSWORD);

        assertTrue(result.isAuthenticated());
        assertEquals("ana@mail.com", ((PlancitoUserDetails) result.getPrincipal()).getEmail());
    }
}
