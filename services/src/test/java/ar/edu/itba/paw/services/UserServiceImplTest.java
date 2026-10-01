package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: "es recomendable NO utilizar el metodo verify,
// pues queremos verificar comportamiento, no la implementacion". Los
// colaboradores void se capturan con doAnswer() sobre el stub, y "nunca se
// llamo" se prueba stubeando para que reviente (lenient(), porque a proposito
// se espera que el stub quede sin usar).
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

    @Mock
    private EventInvitationDao mockInvitationDao;

    @Mock
    private InboxMessageDao mockInboxMessageDao;

    @InjectMocks
    private UserServiceImpl userService;

    private static User someUser() {
        final User user = new User(NORMALIZED_EMAIL, NAME);
        user.setId(USER_ID);
        return user;
    }

    @Test
    public void testFindByIdReturnsThePresentUser() {
        // Arrange
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.findById(USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        // Arrange
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<User> result = userService.findById(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindOrCreateReturnsTheExistingUserAndNormalizesTheEmail() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        // Act
        final User result = userService.findOrCreate(EMAIL, NAME);

        // Assert
        assertEquals(USER_ID, result.getId());
        assertEquals(NORMALIZED_EMAIL, result.getEmail());
    }

    @Test
    public void testFindOrCreateCreatesANewUserWhenNoneExists() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());

        // Act
        final User result = userService.findOrCreate(EMAIL, NAME);

        // Assert
        assertEquals(USER_ID, result.getId());
    }

    // ---- findByEmail() ----

    @Test
    public void testFindByEmailIgnoresCaseAndSpaces() {
        // Arrange
        // Es lo que usa el login (UserDetailsServiceImpl): "  Test@Mail.com  " tiene que entrar.
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.findByEmail(EMAIL);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId());
    }

    // ---- register() ----

    private Object[] captureSetPassword() {
        final Object[] captured = new Object[2];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setPassword(anyLong(), anyString());
        return captured;
    }

    private Object[] captureAddRole() {
        final Object[] captured = new Object[2];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).addRole(anyLong(), anyString());
        return captured;
    }

    private Object[] captureSetVerified() {
        final Object[] captured = new Object[2];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setVerified(anyLong(), anyBoolean());
        return captured;
    }

    private Object[] captureNotifyAccountVerification() {
        final Object[] captured = new Object[3];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            captured[2] = invocation.getArgument(2);
            return null;
        }).when(mockNotificationService).notifyAccountVerification(any(User.class), anyString(), any());
        return captured;
    }

    private Object[] captureLinkUserIdInvitation() {
        final Object[] captured = new Object[2];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            return null;
        }).when(mockInvitationDao).linkUserId(anyString(), anyLong());
        return captured;
    }

    private Object[] captureLinkUserIdInbox() {
        final Object[] captured = new Object[2];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            return null;
        }).when(mockInboxMessageDao).linkUserId(anyString(), anyLong());
        return captured;
    }

    @Test
    public void testRegisterCreatesANewUserWhenNoneExists() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        final Object[] setPasswordArgs = captureSetPassword();
        final Object[] addRoleArgs = captureAddRole();
        final Object[] setVerifiedArgs = captureSetVerified();
        final Object[] notifyArgs = captureNotifyAccountVerification();
        final Object[] linkInvitationArgs = captureLinkUserIdInvitation();
        final Object[] linkInboxArgs = captureLinkUserIdInbox();

        // Act
        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD, null);

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
        assertEquals(USER_ID, setPasswordArgs[0]);
        assertEquals(HASHED_PASSWORD, setPasswordArgs[1]);
        assertEquals(USER_ID, addRoleArgs[0]);
        assertEquals("ROLE_USER", addRoleArgs[1]);
        // Cuenta nueva: no puede loguearse hasta confirmar el mail.
        assertEquals(USER_ID, setVerifiedArgs[0]);
        assertEquals(false, setVerifiedArgs[1]);
        assertTrue(notifyArgs[0] != null);
        // Reconciliacion Grupo B: invitaciones/avisos que la esperaban sin cuenta.
        assertEquals(NORMALIZED_EMAIL, linkInvitationArgs[0]);
        assertEquals(USER_ID, linkInvitationArgs[1]);
        assertEquals(NORMALIZED_EMAIL, linkInboxArgs[0]);
        assertEquals(USER_ID, linkInboxArgs[1]);
    }

    @Test
    public void testRegisterPassesTheRedirectToTheVerificationMail() {
        // Arrange
        // Venia de una invitacion a un evento privado: el link del mail lo tiene que llevar.
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockUserDao.create(any(User.class))).thenReturn(someUser());
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        final Object[] notifyArgs = captureNotifyAccountVerification();

        // Act
        userService.register(EMAIL, NAME, RAW_PASSWORD, "/eventos/7");

        // Assert
        assertEquals("/eventos/7", notifyArgs[2]);
    }

    @Test
    public void testRegisterClaimsAGhostUserThatHasNoPassword() {
        // Arrange
        // Fantasma: creado por findOrCreate (publicar/inscribirse sin login), password null.
        final User ghost = someUser();
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        // Ya existe como fantasma: no deberia crear una fila nueva.
        lenient().when(mockUserDao.create(any(User.class)))
                .thenThrow(new AssertionError("no deberia crear un usuario nuevo para un fantasma existente"));
        final Object[] setPasswordArgs = captureSetPassword();
        final Object[] addRoleArgs = captureAddRole();
        final Object[] setVerifiedArgs = captureSetVerified();
        final Object[] linkInvitationArgs = captureLinkUserIdInvitation();
        final Object[] linkInboxArgs = captureLinkUserIdInbox();

        // Act
        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD, null);

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
        assertEquals(USER_ID, setPasswordArgs[0]);
        assertEquals(HASHED_PASSWORD, setPasswordArgs[1]);
        assertEquals("ROLE_USER", addRoleArgs[1]);
        assertEquals(false, setVerifiedArgs[1]);
        assertEquals(NORMALIZED_EMAIL, linkInvitationArgs[0]);
        assertEquals(NORMALIZED_EMAIL, linkInboxArgs[0]);
    }

    @Test
    public void testRegisterSavesTheCurrentPageLocaleWhenTheAccountHadNone() {
        // Arrange
        // El idioma con el que se estaba navegando en el momento de registrarse
        // (cookie de LocaleResolver, ver WebConfig) queda guardado: asi el mail
        // de confirmacion que sigue ya sale en ese idioma (ver
        // NotificationServiceImpl.notifyAccountVerification()).
        org.springframework.context.i18n.LocaleContextHolder.setLocale(new Locale("en"));
        try {
            when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
            when(mockUserDao.create(any(User.class))).thenReturn(someUser());
            when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
            final Object[] setLocaleArgs = new Object[2];
            doAnswer(invocation -> {
                setLocaleArgs[0] = invocation.getArgument(0);
                setLocaleArgs[1] = invocation.getArgument(1);
                return null;
            }).when(mockUserDao).setLocale(anyLong(), anyString());

            // Act
            userService.register(EMAIL, NAME, RAW_PASSWORD, null);

            // Assert
            assertEquals(USER_ID, setLocaleArgs[0]);
            assertEquals("en", setLocaleArgs[1]);
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    public void testRegisterDoesNotOverwriteALocaleTheGhostAccountAlreadyHad() {
        // Arrange
        // Fantasma que ya habia elegido idioma antes de registrarse de verdad
        // (por ejemplo, alguien lo invito y de paso cambio el idioma del sitio):
        // register() no lo pisa.
        final User ghostWithLocale = someUser();
        ghostWithLocale.setLocale("es");
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghostWithLocale));
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        lenient().doThrow(new AssertionError("no deberia pisar el idioma que el fantasma ya tenia"))
                .when(mockUserDao).setLocale(anyLong(), anyString());

        // Act
        userService.register(EMAIL, NAME, RAW_PASSWORD, null);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    @Test
    public void testRegisterRejectsAnEmailThatAlreadyHasAPassword() {
        // Arrange
        final User existing = someUser();
        existing.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        // Act
        final UserRegistrationResult result = userService.register(EMAIL, NAME, RAW_PASSWORD, null);

        // Assert
        assertEquals(UserRegistrationResult.EMAIL_TAKEN, result);
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileUpdatesWhenTheEmailIsFreeOrItsOwn() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        final Object[] updateProfileArgs = new Object[3];
        doAnswer(invocation -> {
            updateProfileArgs[0] = invocation.getArgument(0);
            updateProfileArgs[1] = invocation.getArgument(1);
            updateProfileArgs[2] = invocation.getArgument(2);
            return null;
        }).when(mockUserDao).updateProfile(anyLong(), anyString(), anyString());

        // Act
        final ProfileUpdateResult result = userService.updateProfile(USER_ID, "Nuevo Nombre", EMAIL);

        // Assert
        assertEquals(ProfileUpdateResult.UPDATED, result);
        assertEquals(USER_ID, updateProfileArgs[0]);
        assertEquals("Nuevo Nombre", updateProfileArgs[1]);
        assertEquals(NORMALIZED_EMAIL, updateProfileArgs[2]);
    }

    @Test
    public void testUpdateProfileAllowsKeepingYourOwnEmail() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));
        final Object[] updateProfileArgs = new Object[3];
        doAnswer(invocation -> {
            updateProfileArgs[0] = invocation.getArgument(0);
            updateProfileArgs[1] = invocation.getArgument(1);
            updateProfileArgs[2] = invocation.getArgument(2);
            return null;
        }).when(mockUserDao).updateProfile(anyLong(), anyString(), anyString());

        // Act
        final ProfileUpdateResult result = userService.updateProfile(USER_ID, NAME, EMAIL);

        // Assert
        assertEquals(ProfileUpdateResult.UPDATED, result);
        assertEquals(NAME, updateProfileArgs[1]);
        assertEquals(NORMALIZED_EMAIL, updateProfileArgs[2]);
    }

    @Test
    public void testUpdateProfileRejectsAnEmailThatBelongsToSomeoneElse() {
        // Arrange
        final User someoneElse = someUser();
        someoneElse.setId(USER_ID + 1);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someoneElse));

        // Act
        final ProfileUpdateResult result = userService.updateProfile(USER_ID, NAME, EMAIL);

        // Assert
        assertEquals(ProfileUpdateResult.EMAIL_TAKEN, result);
    }

    // ---- changePassword() ----

    @Test
    public void testChangePasswordSucceedsWhenTheCurrentPasswordMatches() {
        // Arrange
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mockPasswordEncoder.matches("old-password", HASHED_PASSWORD)).thenReturn(true);
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn("new-hashed-password");
        final Object[] setPasswordArgs = captureSetPassword();

        // Act
        final boolean result = userService.changePassword(USER_ID, "old-password", RAW_PASSWORD);

        // Assert
        assertTrue(result);
        assertEquals("new-hashed-password", setPasswordArgs[1]);
    }

    @Test
    public void testChangePasswordFailsWhenTheCurrentPasswordIsWrong() {
        // Arrange
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(user));
        when(mockPasswordEncoder.matches("wrong-password", HASHED_PASSWORD)).thenReturn(false);

        // Act
        final boolean result = userService.changePassword(USER_ID, "wrong-password", RAW_PASSWORD);

        // Assert
        assertFalse(result);
    }

    // ---- updateLocale() ----

    @Test
    public void testUpdateLocaleDelegatesToTheDao() {
        // Arrange
        final Object[] setLocaleArgs = new Object[2];
        doAnswer(invocation -> {
            setLocaleArgs[0] = invocation.getArgument(0);
            setLocaleArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setLocale(anyLong(), anyString());

        // Act
        userService.updateLocale(USER_ID, "en");

        // Assert
        assertEquals(USER_ID, setLocaleArgs[0]);
        assertEquals("en", setLocaleArgs[1]);
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
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        final Object[] setVerifiedArgs = captureSetVerified();
        final String[] deletedToken = new String[1];
        doAnswer(invocation -> {
            deletedToken[0] = invocation.getArgument(0);
            return null;
        }).when(mockTokenDao).delete(anyString());

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId());
        assertEquals(USER_ID, setVerifiedArgs[0]);
        assertEquals(true, setVerifiedArgs[1]);
        assertEquals("some-token", deletedToken[0]);
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenDoesNotExist() {
        // Arrange
        when(mockTokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenExpired() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, oneHourAgo())));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenIsForTheWrongPurpose() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- resendVerificationEmail() ----

    @Test
    public void testResendVerificationEmailSendsANewOneWhenUnverified() {
        // Arrange
        final User unverified = someUser();
        unverified.setPassword(HASHED_PASSWORD);
        unverified.setVerified(false);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverified));
        final Object[] deleteByUserAndPurposeArgs = new Object[2];
        doAnswer(invocation -> {
            deleteByUserAndPurposeArgs[0] = invocation.getArgument(0);
            deleteByUserAndPurposeArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockTokenDao).deleteByUserAndPurpose(anyLong(), any(TokenPurpose.class));
        final Object[] notifyArgs = captureNotifyAccountVerification();

        // Act
        userService.resendVerificationEmail(EMAIL);

        // Assert
        assertEquals(USER_ID, deleteByUserAndPurposeArgs[0]);
        assertEquals(TokenPurpose.VERIFY_EMAIL, deleteByUserAndPurposeArgs[1]);
        assertTrue(notifyArgs[0] != null);
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenAlreadyVerified() {
        // Arrange
        final User verified = someUser();
        verified.setPassword(HASHED_PASSWORD);
        verified.setVerified(true);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(verified));
        lenient().doThrow(new AssertionError("ya esta verificado, no deberia volver a avisarle"))
                .when(mockNotificationService).notifyAccountVerification(any(), any(), any());

        // Act
        userService.resendVerificationEmail(EMAIL);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenTheAccountIsAGhost() {
        // Arrange
        final User ghost = someUser();
        ghost.setVerified(false);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));
        lenient().doThrow(new AssertionError("un fantasma sin password no deberia recibir este mail"))
                .when(mockNotificationService).notifyAccountVerification(any(), any(), any());

        // Act
        userService.resendVerificationEmail(EMAIL);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    @Test
    public void testResendVerificationEmailDoesNothingWhenTheEmailDoesNotExist() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        lenient().doThrow(new AssertionError("el email no existe, no deberia mandar nada"))
                .when(mockNotificationService).notifyAccountVerification(any(), any(), any());

        // Act
        userService.resendVerificationEmail(EMAIL);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    // ---- sendPasswordResetEmail() ----

    @Test
    public void testSendPasswordResetEmailSendsItWhenTheAccountHasAPassword() {
        // Arrange
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(user));
        final Object[] deleteByUserAndPurposeArgs = new Object[2];
        doAnswer(invocation -> {
            deleteByUserAndPurposeArgs[0] = invocation.getArgument(0);
            deleteByUserAndPurposeArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockTokenDao).deleteByUserAndPurpose(anyLong(), any(TokenPurpose.class));
        final boolean[] notified = {false};
        doAnswer(invocation -> {
            notified[0] = true;
            return null;
        }).when(mockNotificationService).notifyPasswordReset(any(User.class), anyString());

        // Act
        userService.sendPasswordResetEmail(EMAIL);

        // Assert
        assertEquals(USER_ID, deleteByUserAndPurposeArgs[0]);
        assertEquals(TokenPurpose.RESET_PASSWORD, deleteByUserAndPurposeArgs[1]);
        assertTrue(notified[0]);
    }

    @Test
    public void testSendPasswordResetEmailDoesNothingWhenTheAccountIsAGhost() {
        // Arrange
        final User ghost = someUser();
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));
        lenient().doThrow(new AssertionError("un fantasma sin password no deberia recibir este mail"))
                .when(mockNotificationService).notifyPasswordReset(any(), any());

        // Act
        userService.sendPasswordResetEmail(EMAIL);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    @Test
    public void testSendPasswordResetEmailDoesNothingWhenTheEmailDoesNotExist() {
        // Arrange
        when(mockUserDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        lenient().doThrow(new AssertionError("el email no existe, no deberia mandar nada"))
                .when(mockNotificationService).notifyPasswordReset(any(), any());

        // Act
        userService.sendPasswordResetEmail(EMAIL);

        // Assert: que no reviente el stub de arriba ES el assert.
    }

    // ---- isResetTokenValid() ----

    @Test
    public void testIsResetTokenValidReturnsTrueForAFreshToken() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenExpired() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenItDoesNotExist() {
        // Arrange
        when(mockTokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertFalse(result);
    }

    // ---- resetPassword() ----

    @Test
    public void testResetPasswordChangesThePasswordAndConsumesTheToken() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));
        when(mockPasswordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        // OK es un literal al final de un unico camino recto (setPassword +
        // delete, sin ninguna otra rama ni llamada stubeada aparte): el
        // resultado ya implica que las dos pasaron.
        assertEquals(PasswordResetResult.OK, result);
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenExpired() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenIsForTheWrongPurpose() {
        // Arrange
        when(mockTokenDao.findByToken("some-token"))
                .thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
    }

    // ---- suspend()/reactivate() (moderacion) ----

    @Test
    public void testSuspendMarksTheUserSuspendedAndNotifies() {
        // Arrange
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        final Object[] setSuspendedArgs = new Object[2];
        doAnswer(invocation -> {
            setSuspendedArgs[0] = invocation.getArgument(0);
            setSuspendedArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setSuspended(anyLong(), anyBoolean());
        final boolean[] notified = {false};
        doAnswer(invocation -> {
            notified[0] = true;
            return null;
        }).when(mockNotificationService).notifyUserSuspended(any(User.class));
        final boolean[] inboxWritten = {false};
        doAnswer(invocation -> {
            inboxWritten[0] = true;
            return null;
        }).when(mockInboxMessageDao).create(any());

        // Act
        userService.suspend(USER_ID);

        // Assert
        assertEquals(USER_ID, setSuspendedArgs[0]);
        assertEquals(true, setSuspendedArgs[1]);
        assertTrue(notified[0]);
        assertTrue(inboxWritten[0]);
    }

    @Test
    public void testSuspendOnAUserThatDisappearedStillSetsTheFlagButDoesNotNotify() {
        // Arrange
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.empty());
        final Object[] setSuspendedArgs = new Object[2];
        doAnswer(invocation -> {
            setSuspendedArgs[0] = invocation.getArgument(0);
            setSuspendedArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setSuspended(anyLong(), anyBoolean());
        lenient().doThrow(new AssertionError("el usuario ya no existe, no hay a quien notificarle"))
                .when(mockNotificationService).notifyUserSuspended(any());

        // Act
        userService.suspend(USER_ID);

        // Assert
        assertEquals(USER_ID, setSuspendedArgs[0]);
        assertEquals(true, setSuspendedArgs[1]);
    }

    @Test
    public void testReactivateMarksTheUserNotSuspendedAndNotifies() {
        // Arrange
        when(mockUserDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
        final Object[] setSuspendedArgs = new Object[2];
        doAnswer(invocation -> {
            setSuspendedArgs[0] = invocation.getArgument(0);
            setSuspendedArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).setSuspended(anyLong(), anyBoolean());
        final boolean[] notified = {false};
        doAnswer(invocation -> {
            notified[0] = true;
            return null;
        }).when(mockNotificationService).notifyUserReactivated(any(User.class));
        final boolean[] inboxWritten = {false};
        doAnswer(invocation -> {
            inboxWritten[0] = true;
            return null;
        }).when(mockInboxMessageDao).create(any());

        // Act
        userService.reactivate(USER_ID);

        // Assert
        assertEquals(USER_ID, setSuspendedArgs[0]);
        assertEquals(false, setSuspendedArgs[1]);
        assertTrue(notified[0]);
        assertTrue(inboxWritten[0]);
    }

    // ---- grantAdmin()/revokeAdmin() ----

    @Test
    public void testGrantAdminAddsRoleAdmin() {
        // Arrange
        final Object[] addRoleArgs = captureAddRole();

        // Act
        userService.grantAdmin(USER_ID);

        // Assert
        assertEquals(USER_ID, addRoleArgs[0]);
        assertEquals("ROLE_ADMIN", addRoleArgs[1]);
    }

    @Test
    public void testRevokeAdminRemovesRoleAdmin() {
        // Arrange
        final Object[] removeRoleArgs = new Object[2];
        doAnswer(invocation -> {
            removeRoleArgs[0] = invocation.getArgument(0);
            removeRoleArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockUserDao).removeRole(anyLong(), anyString());

        // Act
        userService.revokeAdmin(USER_ID);

        // Assert
        assertEquals(USER_ID, removeRoleArgs[0]);
        assertEquals("ROLE_ADMIN", removeRoleArgs[1]);
    }

    // ---- findAll() ----

    @Test
    public void testFindAllDelegatesToTheDao() {
        // Arrange
        when(mockUserDao.findAll()).thenReturn(java.util.Collections.singletonList(someUser()));

        // Act
        final java.util.List<User> all = userService.findAll();

        // Assert
        assertEquals(1, all.size());
    }
}
