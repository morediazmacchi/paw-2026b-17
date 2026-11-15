package ar.edu.itba.paw.webapp.auth;

import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.services.UserService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de UserDetailsServiceImpl: el UserService es un mock solo para la
 * consulta findByEmail.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserDetailsServiceImplTest {

    private static final String EMAIL = "ana@mail.com";
    private static final String PASSWORD_HASH = "$2a$hash";
    private static final long USER_ID = 7L;

    @Mock
    private UserService mockUserService;

    private UserDetailsServiceImpl userDetailsService;

    @Before
    public void setUp() {
        userDetailsService = new UserDetailsServiceImpl(mockUserService);
    }

    private static User verifiedUser() {
        final User user = new User(EMAIL, "Ana");
        user.setId(USER_ID);
        user.setPassword(PASSWORD_HASH);
        user.setVerified(true);
        user.setSuspended(false);
        user.setRoles(new HashSet<>(Arrays.asList(Role.USER)));
        return user;
    }

    @Test
    public void testLoadUserByUsernameReturnsTheUsernameAndPasswordHash() {
        // Arrange
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser()));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertEquals(EMAIL, result.getUsername());
        assertEquals(PASSWORD_HASH, result.getPassword());
    }

    @Test
    public void testLoadUserByUsernameReturnsAPlancitoUserDetailsWithTheIdAndEmail() {
        // Arrange
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser()));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        final PlancitoUserDetails principal = (PlancitoUserDetails) result;
        assertEquals(USER_ID, principal.getId());
        assertEquals(EMAIL, principal.getEmail());
    }

    @Test
    public void testLoadUserByUsernameReturnsTheRolesOfTheUserAsAuthorities() {
        // Arrange
        final User admin = verifiedUser();
        admin.setRoles(new HashSet<>(Arrays.asList(Role.USER, Role.ADMIN)));
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(admin));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        final Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN")), authorities);
    }

    @Test
    public void testLoadUserByUsernameReturnsAnEnabledAccountForAVerifiedUserThatIsNotSuspended() {
        // Arrange
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(verifiedUser()));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertTrue(result.isEnabled());
    }

    @Test
    public void testLoadUserByUsernameReturnsADisabledAccountWhenTheMailWasNotConfirmed() {
        // Arrange
        final User unverified = verifiedUser();
        unverified.setVerified(false);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertFalse(result.isEnabled());
    }

    @Test
    public void testLoadUserByUsernameReturnsALockedAccountWhenAnAdminSuspendedIt() {
        // Arrange
        final User suspended = verifiedUser();
        suspended.setSuspended(true);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(suspended));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert: suspendida = bloqueada (no "deshabilitada"): el login las distingue por el tipo de excepcion
        assertFalse(result.isAccountNonLocked());
        assertTrue(result.isEnabled());
    }

    @Test
    public void testLoadUserByUsernameReturnsAnUnlockedAccountWhenTheMailWasNotConfirmed() {
        // Arrange
        final User unverified = verifiedUser();
        unverified.setVerified(false);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(unverified));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertTrue(result.isAccountNonLocked());
    }

    @Test
    public void testTheDetailsAreActiveExactlyWhenTheUserIsActive() {
        // Arrange: las 4 combinaciones de verificado/suspendido
        for (final boolean verified : new boolean[] {true, false}) {
            for (final boolean suspended : new boolean[] {true, false}) {
                final User user = verifiedUser();
                user.setVerified(verified);
                user.setSuspended(suspended);
                when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(user));

                // Act
                final boolean detailsActive = userDetailsService.loadUserByUsername(EMAIL).isActive();

                // Assert
                assertEquals("verified=" + verified + " suspended=" + suspended, user.isActive(), detailsActive);
            }
        }
    }

    @Test(expected = UsernameNotFoundException.class)
    public void testLoadUserByUsernameThrowsWhenThereIsNoUserWithThatEmail() {
        // Arrange
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act
        userDetailsService.loadUserByUsername(EMAIL);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // Un usuario "fantasma" (creado antes de que existiera el login) NO se distingue de una cuenta con otra
    // clave: se devuelve deshabilitado y con un hash que ninguna contraseña verifica, asi que el login falla
    // con el error generico (no revela que la cuenta existe).
    @Test
    public void testLoadUserByUsernameOfAUserWithoutPasswordReturnsADisabledAccountWithAnUnusablePassword() {
        // Arrange
        final User ghost = verifiedUser();
        ghost.setPassword(null);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(ghost));

        // Act
        final UserDetails details = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertFalse(details.isEnabled());
        assertTrue(details.getPassword().startsWith("$2a$10$"));
    }
}
