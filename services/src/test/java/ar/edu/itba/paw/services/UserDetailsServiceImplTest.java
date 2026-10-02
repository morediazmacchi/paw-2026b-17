package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
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

    @Mock
    private UserService mockUserService;

    private UserDetailsServiceImpl userDetailsService;

    @Before
    public void setUp() {
        userDetailsService = new UserDetailsServiceImpl(mockUserService);
    }

    private static User verifiedUser() {
        final User user = new User(EMAIL, "Ana");
        user.setPassword(PASSWORD_HASH);
        user.setVerified(true);
        user.setSuspended(false);
        user.setRoles(new HashSet<>(Arrays.asList("ROLE_USER")));
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
    public void testLoadUserByUsernameReturnsTheRolesOfTheUserAsAuthorities() {
        // Arrange
        final User admin = verifiedUser();
        admin.setRoles(new HashSet<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN")));
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
    public void testLoadUserByUsernameReturnsADisabledAccountWhenAnAdminSuspendedIt() {
        // Arrange
        final User suspended = verifiedUser();
        suspended.setSuspended(true);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(suspended));

        // Act
        final UserDetails result = userDetailsService.loadUserByUsername(EMAIL);

        // Assert
        assertFalse(result.isEnabled());
    }

    @Test(expected = UsernameNotFoundException.class)
    public void testLoadUserByUsernameThrowsWhenThereIsNoUserWithThatEmail() {
        // Arrange
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // Act
        userDetailsService.loadUserByUsername(EMAIL);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // Un usuario "fantasma" (creado antes de que existiera el login) existe pero no
    // tiene contrasenia: se distingue de "no existe" para mandarlo a elegir una.
    @Test(expected = PasswordNotSetException.class)
    public void testLoadUserByUsernameThrowsPasswordNotSetForAUserWithoutPassword() {
        // Arrange
        final User ghost = verifiedUser();
        ghost.setPassword(null);
        when(mockUserService.findByEmail(EMAIL)).thenReturn(Optional.of(ghost));

        // Act
        userDetailsService.loadUserByUsername(EMAIL);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }
}
