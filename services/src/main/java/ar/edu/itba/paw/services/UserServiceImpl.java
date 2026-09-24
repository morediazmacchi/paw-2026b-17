// Implementa el contrato UserService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final String ROLE_USER = "ROLE_USER";

    // Cuanto dura cada tipo de link antes de que haya que pedir uno nuevo.
    private static final long VERIFY_TOKEN_TTL_MILLIS = 24 * 60 * 60 * 1000L; // 24hs
    private static final long RESET_TOKEN_TTL_MILLIS = 60 * 60 * 1000L;       // 1h

    private final UserDao userDao;
    private final VerificationTokenDao tokenDao;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Autowired
    public UserServiceImpl(final UserDao userDao, final VerificationTokenDao tokenDao,
                           final PasswordEncoder passwordEncoder, final NotificationService notificationService) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(final long id) {
        return userDao.findById(id);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public User findOrCreate(final String email, final String name) {
        final String normalizedEmail = email.trim().toLowerCase();
        return userDao.findByEmail(normalizedEmail)
                .orElseGet(() -> userDao.create(new User(normalizedEmail, name)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(final String email) {
        // En la base los emails se guardan en minuscula (register/findOrCreate):
        // sin esto, loguearse con "Sol@gmail.com" no encontraba a "sol@gmail.com".
        return userDao.findByEmail(email.trim().toLowerCase());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserRegistrationResult register(final String email, final String name, final String rawPassword) {
        final String normalizedEmail = email.trim().toLowerCase();

        final Optional<User> existing = userDao.findByEmail(normalizedEmail);
        final long userId;
        if (existing.isPresent()) {
            final User user = existing.get();
            if (user.getPassword() != null) {
                return UserRegistrationResult.EMAIL_TAKEN;
            }
            // Usuario fantasma (findOrCreate, nunca tuvo password ni pudo
            // loguearse): registrarse con ese email lo completa, no lo pisa.
            userId = user.getId();
        } else {
            userId = userDao.create(new User(normalizedEmail, name)).getId();
        }
        // El hash recien aca, despues del chequeo de EMAIL_TAKEN: BCrypt es lento
        // a proposito y no tiene sentido pagarlo si el registro se rechaza.
        userDao.setPassword(userId, passwordEncoder.encode(rawPassword));
        userDao.addRole(userId, ROLE_USER);
        // Toda cuenta nueva (o fantasma recien completada) tiene que confirmar
        // el mail antes de poder loguearse: ver UserDetailsServiceImpl.
        userDao.setVerified(userId, false);
        sendVerificationEmail(userDao.findById(userId).orElseThrow(IllegalStateException::new));
        return UserRegistrationResult.CREATED;
    }

    @Override
    @Transactional
    public boolean confirmEmail(final String token) {
        final Optional<VerificationToken> found = validToken(token, TokenPurpose.VERIFY_EMAIL);
        if (!found.isPresent()) {
            return false;
        }
        userDao.setVerified(found.get().getUserId(), true);
        tokenDao.delete(token);
        return true;
    }

    @Override
    @Transactional
    public void resendVerificationEmail(final String email) {
        userDao.findByEmail(email.trim().toLowerCase())
                .filter(user -> user.getPassword() != null && !user.isVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    @Override
    @Transactional
    public void sendPasswordResetEmail(final String email) {
        userDao.findByEmail(email.trim().toLowerCase())
                .filter(user -> user.getPassword() != null)
                .ifPresent(user -> {
                    tokenDao.deleteByUserAndPurpose(user.getId(), TokenPurpose.RESET_PASSWORD);
                    final String token = newToken(user.getId(), TokenPurpose.RESET_PASSWORD, RESET_TOKEN_TTL_MILLIS);
                    notificationService.notifyPasswordReset(user, token);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isResetTokenValid(final String token) {
        return validToken(token, TokenPurpose.RESET_PASSWORD).isPresent();
    }

    @Override
    @Transactional
    public PasswordResetResult resetPassword(final String token, final String newPassword) {
        final Optional<VerificationToken> found = validToken(token, TokenPurpose.RESET_PASSWORD);
        if (!found.isPresent()) {
            return PasswordResetResult.INVALID_TOKEN;
        }
        userDao.setPassword(found.get().getUserId(), passwordEncoder.encode(newPassword));
        tokenDao.delete(token);
        return PasswordResetResult.OK;
    }

    private void sendVerificationEmail(final User user) {
        tokenDao.deleteByUserAndPurpose(user.getId(), TokenPurpose.VERIFY_EMAIL);
        final String token = newToken(user.getId(), TokenPurpose.VERIFY_EMAIL, VERIFY_TOKEN_TTL_MILLIS);
        notificationService.notifyAccountVerification(user, token);
    }

    private String newToken(final long userId, final TokenPurpose purpose, final long ttlMillis) {
        final String token = UUID.randomUUID().toString();
        final Date expiresAt = new Date(System.currentTimeMillis() + ttlMillis);
        tokenDao.create(new VerificationToken(token, userId, purpose, expiresAt));
        return token;
    }

    private Optional<VerificationToken> validToken(final String token, final TokenPurpose purpose) {
        return tokenDao.findByToken(token)
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.getExpiresAt().after(new Date()));
    }

    @Override
    @Transactional
    public ProfileUpdateResult updateProfile(final long userId, final String name, final String email) {
        final String normalizedEmail = email.trim().toLowerCase();

        final Optional<User> existing = userDao.findByEmail(normalizedEmail);
        if (existing.isPresent() && existing.get().getId() != userId) {
            return ProfileUpdateResult.EMAIL_TAKEN;
        }

        userDao.updateProfile(userId, name, normalizedEmail);
        return ProfileUpdateResult.UPDATED;
    }

    @Override
    @Transactional
    public boolean changePassword(final long userId, final String currentPassword, final String newPassword) {
        final User user = userDao.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inexistente: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }

        userDao.setPassword(userId, passwordEncoder.encode(newPassword));
        return true;
    }
}
