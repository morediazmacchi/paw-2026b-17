package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
    private VerificationTokenDao mockTokenDao;

    @Mock
    private PasswordEncoder mockPasswordEncoder;

    @Mock
    private NotificationService mockNotificationService;

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
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD);

        assertEquals(UserRegistrationResult.CREATED, result);
        verify(mockUserDao).setPassword(USER_ID, HASHED_PASSWORD);
        verify(mockUserDao).addRole(USER_ID, "ROLE_USER");
        // Cuenta nueva: no puede loguearse hasta confirmar el mail.
        verify(mockUserDao).setVerified(USER_ID, false);
        verify(mockNotificationService).notifyAccountVerification(any(User.class), any(String.class));
    }

    @Test
    public void testRegisterClaimsAGhostUserThatHasNoPassword() {
        // Fantasma: creado por findOrCreate (publicar/inscribirse sin login), password null.
        final User ghost = someUser();
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD);

        assertEquals(UserRegistrationResult.CREATED, result);
        verify(mockUserDao, never()).create(any(User.class));
        verify(mockUserDao).setPassword(USER_ID, HASHED_PASSWORD);
        verify(mockUserDao).addRole(USER_ID, "ROLE_USER");
        verify(mockUserDao).setVerified(USER_ID, false);
        verify(mockNotificationService).notifyAccountVerification(any(User.class), any(String.class));
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

    // ---- confirmEmail() ----

    private static VerificationToken tokenOf(final TokenPurpose purpose, final Date expiresAt) {
        return new VerificationToken("some-token", USER_ID, purpose, expiresAt);
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    private static Date oneHourAgo() {
        return new Date(System.currentTimeMillis() - 3_600_000L);
    }

    @Test
    public void testConfirmEmailVerifiesTheUserAndConsumesTheToken() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));

        final boolean result = userService.confirmEmail("some-token");

        assertTrue(result);
        verify(mockUserDao).setVerified(USER_ID, true);
        verify(mockTokenDao).delete("some-token");
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenDoesNotExist() {
        when(mockTokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        final boolean result = userService.confirmEmail("some-token");

        assertFalse(result);
        verify(mockUserDao, never()).setVerified(anyLong(), anyBoolean());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenExpired() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, oneHourAgo())));

        final boolean result = userService.confirmEmail("some-token");

        assertFalse(result);
        verify(mockTokenDao, never()).delete(any(String.class));
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenIsForTheWrongPurpose() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        final boolean result = userService.confirmEmail("some-token");

        assertFalse(result);
    }

    // ---- resendVerificationEmail() ----

    @Test
    public void testResendVerificationEmailSendsANewOneWhenUnverified() {
        final User unverified = someUser();
        unverified.setPassword(HASHED_PASSWORD);
        unverified.setVerified(false);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverified));

        userService.resendVerificationEmail(EMAIL);

        verify(mockTokenDao).deleteByUserAndPurpose(USER_ID, TokenPurpose.VERIFY_EMAIL);
        verify(mockNotificationService).notifyAccountVerification(any(User.class), any(String.class));
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenAlreadyVerified() {
        final User verified = someUser();
        verified.setPassword(HASHED_PASSWORD);
        verified.setVerified(true);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(verified));

        userService.resendVerificationEmail(EMAIL);

        verify(mockNotificationService, never()).notifyAccountVerification(any(), any());
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenTheAccountIsAGhost() {
        final User ghost = someUser();
        ghost.setVerified(false);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));

        userService.resendVerificationEmail(EMAIL);

        verify(mockNotificationService, never()).notifyAccountVerification(any(), any());
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenTheEmailDoesNotExist() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        userService.resendVerificationEmail(EMAIL);

        verify(mockNotificationService, never()).notifyAccountVerification(any(), any());
    }

    // ---- sendPasswordResetEmail() ----

    @Test
    public void testSendPasswordResetEmailSendsItWhenTheAccountHasAPassword() {
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));

        userService.sendPasswordResetEmail(EMAIL);

        verify(mockTokenDao).deleteByUserAndPurpose(USER_ID, TokenPurpose.RESET_PASSWORD);
        verify(mockNotificationService).notifyPasswordReset(any(User.class), any(String.class));
    }

    @Test
    public void testSendPasswordResetEmailDoesNothingWhenTheAccountIsAGhost() {
        final User ghost = someUser();
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));

        userService.sendPasswordResetEmail(EMAIL);

        verify(mockNotificationService, never()).notifyPasswordReset(any(), any());
    }

    @Test
    public void testSendPasswordResetEmailDoesNothingWhenTheEmailDoesNotExist() {
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        userService.sendPasswordResetEmail(EMAIL);

        verify(mockNotificationService, never()).notifyPasswordReset(any(), any());
    }

    // ---- isResetTokenValid() ----

    @Test
    public void testIsResetTokenValidReturnsTrueForAFreshToken() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        assertTrue(userService.isResetTokenValid("some-token"));
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenExpired() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        assertFalse(userService.isResetTokenValid("some-token"));
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenItDoesNotExist() {
        when(mockTokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        assertFalse(userService.isResetTokenValid("some-token"));
    }

    // ---- resetPassword() ----

    @Test
    public void testResetPasswordChangesThePasswordAndConsumesTheToken() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        assertEquals(PasswordResetResult.OK, result);
        verify(mockUserDao).setPassword(USER_ID, HASHED_PASSWORD);
        verify(mockTokenDao).delete("some-token");
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenExpired() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
        verify(mockUserDao, never()).setPassword(anyLong(), any());
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenIsForTheWrongPurpose() {
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));

        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
    }
}
