package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: los DAOs son @Mock (Mockito). Los comandos del dao ahora devuelven
// boolean/int: el test los stubea con argumentos especificos y eso hace de expectativa (si el
// service no los invoca, Mockito falla por stub sin usar); lo que se asierta es lo que devuelve el
// service. Los avisos por mail son "disparar y olvidar" (void) y no se testean aca: verificar que
// se llamaron seria testear la implementacion de UserServiceImpl, no el contrato de UserService.
@RunWith(MockitoJUnitRunner.class)
public class UserServiceImplTest {

    private static final long USER_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";
    private static final String RAW_PASSWORD = "supersecret";
    private static final String HASHED_PASSWORD = "hashed-password";
    private static final String NEW_HASHED_PASSWORD = "new-hashed-password";

    @Mock
    private UserDao userDao;
    @Mock
    private VerificationTokenDao tokenDao;
    @Mock
    private EventInvitationDao invitationDao;
    @Mock
    private InboxMessageDao inboxMessageDao;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationService notificationService;

    private UserServiceImpl userService;

    @Before
    public void setUp() {
        userService = new UserServiceImpl(
                userDao, tokenDao, passwordEncoder, notificationService, invitationDao, inboxMessageDao,
                event -> { });
    }

    private static User someUser() {
        final User user = new User(NORMALIZED_EMAIL, NAME);
        user.setId(USER_ID);
        return user;
    }

    private static UserRegistrationDTO registrationData() {
        final UserRegistrationDTO data = new UserRegistrationDTO();
        data.setEmail(EMAIL);
        data.setName(NAME);
        data.setRawPassword(RAW_PASSWORD);
        return data;
    }

    // Un usuario que ya se registro de verdad: tiene password.
    private static User registeredUser() {
        final User user = someUser();
        user.setPassword(HASHED_PASSWORD);
        return user;
    }

    private static User unverifiedUser() {
        final User user = registeredUser();
        user.setVerified(false);
        return user;
    }

    private static VerificationToken tokenOf(final TokenPurpose purpose, final Date expiresAt) {
        return new VerificationToken("some-token", ModelFixtures.user(USER_ID), purpose, expiresAt);
    }

    private static Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    private static Date oneHourAgo() {
        return new Date(System.currentTimeMillis() - 3_600_000L);
    }

    // ---- findById() ----

    @Test
    public void testFindAccessByIdReturnsTheIdAndRolesTheDaoFound() {
        // Arrange
        when(userDao.findAccessById(USER_ID))
                .thenReturn(Optional.of(new UserAccess(USER_ID, EnumSet.of(Role.USER, Role.ADMIN))));

        // Act
        final Optional<UserAccess> result = userService.findAccessById(USER_ID);

        // Assert
        assertTrue(result.get().isAdmin());
        assertFalse(result.get().isSuperadmin());
    }

    @Test
    public void testFindByIdReturnsThePresentUser() {
        // Arrange
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.findById(USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId().longValue());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        // Arrange
        when(userDao.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<User> result = userService.findById(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findByEmail() ----

    @Test
    public void testFindByEmailIgnoresCaseAndSpaces() {
        // Arrange
        // Es lo que usa el login (UserDetailsServiceImpl): "  Test@Mail.com  " tiene que entrar.
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.findByEmail(EMAIL);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId().longValue());
    }

    // ---- register() ----

    // Usuario nuevo: no existia y el dao lo crea con id USER_ID (y despues se lo vuelve a leer).
    private void newUserFlow() {
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(userDao.create(NORMALIZED_EMAIL, NAME))
                .thenReturn(someUser());
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
    }

    // Fantasma: fila sin password (puede quedar de datos viejos, ver UserService.register()).
    private void ghostFlow() {
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));
    }

    @Test
    public void testRegisterCreatesANewUserWhenNoneExists() {
        // Arrange
        newUserFlow();

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    @Test
    public void testRegisterSavesTheHashOfThePassword() {
        // Arrange
        newUserFlow();
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userDao.setPassword(USER_ID, HASHED_PASSWORD)).thenReturn(true);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    @Test
    public void testRegisterGivesTheNewUserTheUserRole() {
        // Arrange
        newUserFlow();
        when(userDao.addRole(USER_ID, Role.USER)).thenReturn(true);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    // Cuenta nueva: no puede loguearse hasta confirmar el mail.
    @Test
    public void testRegisterLeavesTheNewAccountUnverified() {
        // Arrange
        newUserFlow();
        when(userDao.setVerified(USER_ID, false)).thenReturn(true);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    // Reconciliacion Grupo B: invitaciones que la esperaban sin cuenta.
    @Test
    public void testRegisterLinksTheInvitationsThatWereWaitingForTheEmail() {
        // Arrange
        newUserFlow();
        when(invitationDao.linkUserId(NORMALIZED_EMAIL, USER_ID)).thenReturn(1);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    // Reconciliacion Grupo B: avisos de la bandeja que la esperaban sin cuenta.
    @Test
    public void testRegisterLinksTheInboxMessagesThatWereWaitingForTheEmail() {
        // Arrange
        newUserFlow();
        when(inboxMessageDao.linkUserId(NORMALIZED_EMAIL, USER_ID)).thenReturn(2);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    @Test
    public void testRegisterClaimsAGhostUserThatHasNoPassword() {
        // Arrange
        // Lo completa (mismo id), no crea una fila nueva: el id sale del fantasma.
        ghostFlow();
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(HASHED_PASSWORD);
        when(userDao.setPassword(USER_ID, HASHED_PASSWORD)).thenReturn(true);
        when(userDao.addRole(USER_ID, Role.USER)).thenReturn(true);

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.CREATED, result);
    }

    // El idioma con el que se estaba navegando en el momento de registrarse (cookie de
    // LocaleResolver, ver WebConfig) queda guardado: asi el mail de confirmacion que sigue ya
    // sale en ese idioma (ver NotificationServiceImpl.notifyAccountVerification()).
    @Test
    public void testRegisterSavesTheCurrentPageLocaleWhenTheAccountHadNone() {
        // Arrange
        newUserFlow();
        when(userDao.setLocale(USER_ID, "en")).thenReturn(true);
        LocaleContextHolder.setLocale(new Locale("en"));
        try {
            // Act
            final UserRegistrationResult result = userService.register(registrationData());

            // Assert
            assertEquals(UserRegistrationResult.CREATED, result);
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    @Test
    public void testRegisterRejectsAnEmailThatAlreadyHasAPassword() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(registeredUser()));

        // Act
        final UserRegistrationResult result = userService.register(registrationData());

        // Assert
        assertEquals(UserRegistrationResult.EMAIL_TAKEN, result);
    }

    // ---- updateProfile() ----
    // El email ya no se recibe aca (ver UserService.updateProfile): el unico
    // comportamiento es delegar el nombre nuevo al DAO.

    @Test
    public void testUpdateProfileUpdatesTheName() {
        // Arrange
        when(userDao.updateProfile(USER_ID, "Nuevo Nombre")).thenReturn(true);

        // Act
        final boolean result = userService.updateProfile(USER_ID, "Nuevo Nombre");

        // Assert
        assertTrue(result);
    }

    // ---- changePassword() ----

    @Test
    public void testChangePasswordSucceedsWhenTheCurrentPasswordMatches() {
        // Arrange
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(registeredUser()));
        when(passwordEncoder.matches("old-password", HASHED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(NEW_HASHED_PASSWORD);
        when(userDao.setPassword(USER_ID, NEW_HASHED_PASSWORD)).thenReturn(true);

        // Act
        final boolean result = userService.changePassword(USER_ID, "old-password", RAW_PASSWORD);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testChangePasswordFailsWhenTheCurrentPasswordIsWrong() {
        // Arrange
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(registeredUser()));
        when(passwordEncoder.matches("wrong-password", HASHED_PASSWORD)).thenReturn(false);

        // Act
        final boolean result = userService.changePassword(USER_ID, "wrong-password", RAW_PASSWORD);

        // Assert
        assertFalse(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testChangePasswordOfAUserThatDoesNotExistThrowsIllegalArgumentException() {
        // Arrange
        when(userDao.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        userService.changePassword(USER_ID, "old-password", RAW_PASSWORD);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- updateLocale() ----

    @Test
    public void testUpdateLocaleReturnsTrueWhenTheLanguageWasSaved() {
        // Arrange
        when(userDao.setLocale(USER_ID, "en")).thenReturn(true);

        // Act
        final boolean result = userService.updateLocale(USER_ID, "en");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUpdateLocaleReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        when(userDao.setLocale(USER_ID, "en")).thenReturn(false);

        // Act
        final boolean result = userService.updateLocale(USER_ID, "en");

        // Assert
        assertFalse(result);
    }

    // ---- confirmEmail() ----

    @Test
    public void testConfirmEmailVerifiesTheUser() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));
        when(userDao.setVerified(USER_ID, true)).thenReturn(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertTrue(result.isPresent());
        assertEquals(USER_ID, result.get().getId().longValue());
    }

    @Test
    public void testConfirmEmailConsumesTheToken() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));
        when(tokenDao.delete("some-token")).thenReturn(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertTrue(result.isPresent());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenDoesNotExist() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenExpired() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, oneHourAgo())));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testConfirmEmailFailsWhenTheTokenIsForTheWrongPurpose() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        // Act
        final Optional<User> result = userService.confirmEmail("some-token");

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- resendVerificationEmail() ----

    @Test
    public void testResendVerificationEmailReturnsTrueAndSendsANewMailWhenUnverified() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverifiedUser()));

        // Act
        final boolean result = userService.resendVerificationEmail(EMAIL);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testResendVerificationEmailInvalidatesThePreviousTokens() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(unverifiedUser()));
        when(tokenDao.deleteByUserAndPurpose(USER_ID, TokenPurpose.VERIFY_EMAIL)).thenReturn(1);

        // Act
        final boolean result = userService.resendVerificationEmail(EMAIL);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testResendVerificationEmailReturnsFalseWhenAlreadyVerified() {
        // Arrange
        final User verified = registeredUser();
        verified.setVerified(true);
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(verified));

        // Act
        final boolean result = userService.resendVerificationEmail(EMAIL);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testResendVerificationEmailReturnsFalseWhenTheAccountIsAGhost() {
        // Arrange
        final User ghost = someUser();
        ghost.setVerified(false);
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(ghost));

        // Act
        final boolean result = userService.resendVerificationEmail(EMAIL);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testResendVerificationEmailReturnsFalseWhenTheEmailDoesNotExist() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.resendVerificationEmail(EMAIL);

        // Assert
        assertFalse(result);
    }

    // ---- sendPasswordResetEmail() ----

    @Test
    public void testSendPasswordResetEmailReturnsTrueAndSendsItWhenTheAccountHasAPassword() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(registeredUser()));

        // Act
        final boolean result = userService.sendPasswordResetEmail(EMAIL);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSendPasswordResetEmailInvalidatesThePreviousTokens() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(registeredUser()));
        when(tokenDao.deleteByUserAndPurpose(USER_ID, TokenPurpose.RESET_PASSWORD)).thenReturn(1);

        // Act
        final boolean result = userService.sendPasswordResetEmail(EMAIL);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSendPasswordResetEmailReturnsFalseWhenTheAccountIsAGhost() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(someUser()));

        // Act
        final boolean result = userService.sendPasswordResetEmail(EMAIL);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSendPasswordResetEmailReturnsFalseWhenTheEmailDoesNotExist() {
        // Arrange
        when(userDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.sendPasswordResetEmail(EMAIL);

        // Assert
        assertFalse(result);
    }

    // ---- isResetTokenValid() ----

    @Test
    public void testIsResetTokenValidReturnsTrueForAFreshToken() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenExpired() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testIsResetTokenValidReturnsFalseWhenItDoesNotExist() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.isResetTokenValid("some-token");

        // Assert
        assertFalse(result);
    }

    // ---- resetPassword() ----

    @Test
    public void testResetPasswordChangesThePassword() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(NEW_HASHED_PASSWORD);
        when(userDao.setPassword(USER_ID, NEW_HASHED_PASSWORD)).thenReturn(true);

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.OK, result);
    }

    @Test
    public void testResetPasswordConsumesTheToken() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, inOneHour())));
        when(tokenDao.delete("some-token")).thenReturn(true);

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.OK, result);
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenExpired() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.RESET_PASSWORD, oneHourAgo())));

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
    }

    @Test
    public void testResetPasswordFailsWhenTheTokenIsForTheWrongPurpose() {
        // Arrange
        when(tokenDao.findByToken("some-token")).thenReturn(Optional.of(tokenOf(TokenPurpose.VERIFY_EMAIL, inOneHour())));

        // Act
        final PasswordResetResult result = userService.resetPassword("some-token", RAW_PASSWORD);

        // Assert
        assertEquals(PasswordResetResult.INVALID_TOKEN, result);
    }

    // ---- suspend()/reactivate() (moderacion) ----

    @Test
    public void testSuspendReturnsTrueWhenTheUserWasMarkedSuspended() {
        // Arrange
        when(userDao.setSuspended(USER_ID, true)).thenReturn(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final boolean result = userService.suspend(USER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSuspendReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        when(userDao.setSuspended(USER_ID, true)).thenReturn(false);
        when(userDao.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.suspend(USER_ID);

        // Assert
        assertFalse(result);
    }

    // ---- suspend() / reactivate() ----

    @Test
    public void testReactivateReturnsTrueWhenTheUserWasMarkedNotSuspended() {
        // Arrange
        when(userDao.setSuspended(USER_ID, false)).thenReturn(true);
        when(userDao.findById(USER_ID)).thenReturn(Optional.of(someUser()));

        // Act
        final boolean result = userService.reactivate(USER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testReactivateReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        when(userDao.setSuspended(USER_ID, false)).thenReturn(false);
        when(userDao.findById(USER_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = userService.reactivate(USER_ID);

        // Assert
        assertFalse(result);
    }

    // ---- grantAdmin()/revokeAdmin() ----

    @Test
    public void testGrantAdminReturnsTrueWhenTheRoleWasAdded() {
        // Arrange
        when(userDao.addRole(USER_ID, Role.ADMIN)).thenReturn(true);

        // Act
        final boolean result = userService.grantAdmin(USER_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testGrantAdminReturnsFalseWhenTheUserWasAlreadyAdmin() {
        // Arrange
        when(userDao.addRole(USER_ID, Role.ADMIN)).thenReturn(false);

        // Act
        final boolean result = userService.grantAdmin(USER_ID);

        // Assert
        assertFalse(result);
    }

    private static final long CALLER_ID = 99;

    private static User callerUser() {
        final User caller = new User("super@test.com", "Super");
        caller.setId(CALLER_ID);
        return caller;
    }

    private static UserAccess accessOf(final long id, final Role... roles) {
        return new UserAccess(id, EnumSet.copyOf(Arrays.asList(roles)));
    }

    @Test
    public void testRevokeAdminOfAnotherAdminReturnsOk() {
        // Arrange
        when(userDao.findAccessById(USER_ID)).thenReturn(Optional.of(accessOf(USER_ID, Role.USER, Role.ADMIN)));
        when(userDao.removeRole(USER_ID, Role.ADMIN)).thenReturn(true);

        // Act
        final RevokeAdminResult result = userService.revokeAdmin(callerUser(), USER_ID);

        // Assert
        assertEquals(RevokeAdminResult.OK, result);
    }

    // Validar antes de escribir: si se sacara el rol a si mismo, el resultado seria OK (removeRole
    // daria true) si el chequeo no corriera antes.
    @Test
    public void testRevokeAdminOnYourOwnAccountReturnsOwnAccount() {
        // Arrange
        lenient().when(userDao.findAccessById(CALLER_ID)).thenReturn(Optional.of(accessOf(CALLER_ID, Role.USER, Role.ADMIN)));
        lenient().when(userDao.removeRole(CALLER_ID, Role.ADMIN)).thenReturn(true);

        // Act
        final RevokeAdminResult result = userService.revokeAdmin(callerUser(), CALLER_ID);

        // Assert
        assertEquals(RevokeAdminResult.OWN_ACCOUNT, result);
    }

    // Un superadmin conserva ADMIN: si el service igual llamara a removeRole (que da true), el
    // resultado seria OK y no TARGET_SUPERADMIN.
    @Test
    public void testRevokeAdminOfASuperadminReturnsTargetSuperadminAndKeepsTheRole() {
        // Arrange
        when(userDao.findAccessById(USER_ID))
                .thenReturn(Optional.of(accessOf(USER_ID, Role.USER, Role.ADMIN, Role.SUPERADMIN)));
        lenient().when(userDao.removeRole(USER_ID, Role.ADMIN)).thenReturn(true);

        // Act
        final RevokeAdminResult result = userService.revokeAdmin(callerUser(), USER_ID);

        // Assert
        assertEquals(RevokeAdminResult.TARGET_SUPERADMIN, result);
    }

    @Test
    public void testRevokeAdminOfAUserWhoWasNotAdminReturnsOk() {
        // Arrange
        when(userDao.findAccessById(USER_ID)).thenReturn(Optional.of(accessOf(USER_ID, Role.USER)));
        when(userDao.removeRole(USER_ID, Role.ADMIN)).thenReturn(false);

        // Act
        final RevokeAdminResult result = userService.revokeAdmin(callerUser(), USER_ID);

        // Assert
        assertEquals(RevokeAdminResult.OK, result);
    }

    @Test
    public void testRevokeAdminOfAUserWhoDoesNotExistReturnsNotFound() {
        // Arrange
        when(userDao.findAccessById(USER_ID)).thenReturn(Optional.empty());

        // Act
        final RevokeAdminResult result = userService.revokeAdmin(callerUser(), USER_ID);

        // Assert
        assertEquals(RevokeAdminResult.NOT_FOUND, result);
    }

    // ---- suspend(caller, target): el panel de admin ----

    // Validar antes de escribir: si el service suspendiera primero, setSuspended daria true y el
    // resultado seria OK.
    @Test
    public void testSuspendYourOwnAccountReturnsOwnAccount() {
        // Arrange
        lenient().when(userDao.setSuspended(CALLER_ID, true)).thenReturn(true);

        // Act
        final SuspendResult result = userService.suspend(callerUser(), CALLER_ID);

        // Assert
        assertEquals(SuspendResult.OWN_ACCOUNT, result);
    }

    @Test
    public void testSuspendAnotherUserReturnsOk() {
        // Arrange
        when(userDao.setSuspended(USER_ID, true)).thenReturn(true);

        // Act
        final SuspendResult result = userService.suspend(callerUser(), USER_ID);

        // Assert
        assertEquals(SuspendResult.OK, result);
    }

    @Test
    public void testSuspendAUserWhoDoesNotExistReturnsNotFound() {
        // Arrange
        when(userDao.setSuspended(USER_ID, true)).thenReturn(false);

        // Act
        final SuspendResult result = userService.suspend(callerUser(), USER_ID);

        // Assert
        assertEquals(SuspendResult.NOT_FOUND, result);
    }

    // ---- findAll() ----

    @Test
    public void testFindAllReturnsEveryUser() {
        // Arrange
        final List<User> users = Collections.singletonList(someUser());
        when(userDao.findAll()).thenReturn(users);

        // Act
        final List<User> all = userService.findAll();

        // Assert
        assertEquals(users, all);
    }

    // ---- findPage() ----

    private static List<User> usersWithIds(final long... ids) {
        final List<User> users = new ArrayList<>();
        for (final long id : ids) {
            final User user = someUser();
            user.setId(id);
            users.add(user);
        }
        return users;
    }

    @Test
    public void testFindPageReturnsOnlyThatPagesUsers() {
        // Arrange: 45 usuarios, de a 20 por pagina -> la pagina 2 pide 20 desde el offset 20.
        when(userDao.countAll()).thenReturn(45L);
        when(userDao.findPage(UserServiceImpl.PAGE_SIZE, 20)).thenReturn(usersWithIds(21, 22));

        // Act
        final PageResult<User> result = userService.findPage(2);

        // Assert
        assertEquals(Arrays.asList(21L, 22L),
                result.getContent().stream().map(User::getId).collect(Collectors.toList()));
        assertEquals(3, result.getTotalPages());
    }

    @Test
    public void testFindPageBeyondTheLastFallsBackToTheLastPage() {
        // Arrange: 45 usuarios, de a 20 por pagina -> la ultima pagina valida es la 3 (offset 40).
        when(userDao.countAll()).thenReturn(45L);
        when(userDao.findPage(UserServiceImpl.PAGE_SIZE, 40)).thenReturn(usersWithIds(41, 42, 43, 44, 45));

        // Act
        final PageResult<User> result = userService.findPage(99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(Arrays.asList(41L, 42L, 43L, 44L, 45L),
                result.getContent().stream().map(User::getId).collect(Collectors.toList()));
    }

    // ---- findByIds() ----

    @Test
    public void testFindByIdsReturnsTheUsersTheDaoFinds() {
        // Arrange
        final List<User> users = Collections.singletonList(someUser());
        when(userDao.findByIds(Collections.singletonList(USER_ID))).thenReturn(users);

        // Act
        final List<User> result = userService.findByIds(Collections.singletonList(USER_ID));

        // Assert
        assertEquals(users, result);
    }
}
