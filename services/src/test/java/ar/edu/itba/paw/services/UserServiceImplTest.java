package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplTest {

    private static final long USER_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";
    private static final String RAW_PASSWORD = "supersecret";
    private static final String HASHED_PASSWORD = "hashed-password";

    @Mock
    private UserDao mockUserDao;

    @Mock
    private PasswordEncoder mockPasswordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private static User someUser() {
        final User user = new User(NORMALIZED_EMAIL, NAME);
        user.setId(USER_ID);
        return user;
    }

    @Test
    public void testFindByIdReturnsThePresentUser() {
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        final Optional<User> result = userService.findById(USER_ID);

        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.empty());

        final Optional<User> result = userService.findById(USER_ID);

        assertFalse(result.isPresent());
    }

    @Test
    public void testFindOrCreateReturnsTheExistingUserAndNormalizesTheEmail() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        final User result = userService.findOrCreate(EMAIL, NAME);

        assertEquals(USER_ID, result.getId());
        assertEquals(NORMALIZED_EMAIL, result.getEmail());
    }

    @Test
    public void testFindOrCreateCreatesANewUserWhenNoneExists() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());

        final User result = userService.findOrCreate(EMAIL, NAME);

        assertEquals(USER_ID, result.getId());
    }

    // ---- register() ----

    @Test
    public void testRegisterCreatesANewUserWhenNoneExists() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD);

        assertEquals(UserRegistrationResult.CREATED, result);
        verify(mockUserDao).setPassword(USER_ID, HASHED_PASSWORD);
        verify(mockUserDao).addRole(USER_ID, "ROLE_USER");
    }

    @Test
    public void testRegisterClaimsAGhostUserThatHasNoPassword() {
        // Fantasma: creado por findOrCreate (publicar/inscribirse sin login), password null.
        final User ghost = someUser();
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD);

        assertEquals(UserRegistrationResult.CREATED, result);
        verify(mockUserDao, never()).create(any(User.class));
        verify(mockUserDao).setPassword(USER_ID, HASHED_PASSWORD);
        verify(mockUserDao).addRole(USER_ID, "ROLE_USER");
    }

    @Test
    public void testRegisterRejectsAnEmailThatAlreadyHasAPassword() {
        final User existing = someUser();
        existing.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD);

        assertEquals(UserRegistrationResult.EMAIL_TAKEN, result);
        verify(mockUserDao, never()).setPassword(anyLong(), any());
        verify(mockUserDao, never()).addRole(anyLong(), any());
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileUpdatesWhenTheEmailIsFreeOrItsOwn() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        final ProfileUpdateResult result = userService.updateProfile(USER_ID, "Nuevo Nombre", EMAIL);

        assertEquals(ProfileUpdateResult.UPDATED, result);
        verify(mockUserDao).updateProfile(USER_ID, "Nuevo Nombre", NORMALIZED_EMAIL);
    }

    @Test
    public void testUpdateProfileAllowsKeepingYourOwnEmail() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        final ProfileUpdateResult result = userService.updateProfile(USER_ID, NAME, EMAIL);

        assertEquals(ProfileUpdateResult.UPDATED, result);
        verify(mockUserDao).updateProfile(USER_ID, NAME, NORMALIZED_EMAIL);
    }

    @Test
    public void testUpdateProfileRejectsAnEmailThatBelongsToSomeoneElse() {
        final User someoneElse = someUser();
        someoneElse.setId(USER_ID + 1);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someoneElse));

        final ProfileUpdateResult result = userService.updateProfile(USER_ID, NAME, EMAIL);

        assertEquals(ProfileUpdateResult.EMAIL_TAKEN, result);
        verify(mockUserDao, never()).updateProfile(anyLong(), any(), any());
    }

    // ---- changePassword() ----

    @Test
    public void testChangePasswordSucceedsWhenTheCurrentPasswordMatches() {
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mockPasswordEncoder.matches("old-password", HASHED_PASSWORD)).thenReturn(true);
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn("new-hashed-password");

        final boolean result = userService.changePassword(USER_ID, "old-password", RAW_PASSWORD);

        assertTrue(result);
        verify(mockUserDao).setPassword(USER_ID, "new-hashed-password");
    }

    @Test
    public void testChangePasswordFailsWhenTheCurrentPasswordIsWrong() {
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mockPasswordEncoder.matches("wrong-password", HASHED_PASSWORD)).thenReturn(false);

        final boolean result = userService.changePassword(USER_ID, "wrong-password", RAW_PASSWORD);

        assertFalse(result);
        verify(mockUserDao, never()).setPassword(anyLong(), any());
    }
}
