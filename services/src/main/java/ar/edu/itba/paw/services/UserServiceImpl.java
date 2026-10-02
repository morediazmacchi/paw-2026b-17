// Implementa el contrato UserService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    // Cuanto dura cada tipo de link antes de que haya que pedir uno nuevo.
    private static final long VERIFY_TOKEN_TTL_MILLIS = 24 * 60 * 60 * 1000L; // 24hs
    private static final long RESET_TOKEN_TTL_MILLIS = 60 * 60 * 1000L;       // 1h

    private final UserDao userDao;
    private final VerificationTokenDao tokenDao;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;
    // Reconciliacion del Grupo B (ver schema.sql): habla directo con los DAOs,
    // no con EventInvitationService/InboxService, para no depender de la capa
    // de servicios (evita un ciclo y esto no necesita nada de su logica de
    // negocio, es un simple UPDATE por email).
    private final EventInvitationDao invitationDao;
    private final InboxMessageDao inboxMessageDao;

    @Autowired
    public UserServiceImpl(final UserDao userDao, final VerificationTokenDao tokenDao,
                           final PasswordEncoder passwordEncoder, final NotificationService notificationService,
                           final EventInvitationDao invitationDao, final InboxMessageDao inboxMessageDao) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.invitationDao = invitationDao;
        this.inboxMessageDao = inboxMessageDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(final long id) {
        return userDao.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByIds(final List<Long> ids) {
        return userDao.findByIds(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userDao.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(final String email) {
        // En la base los emails se guardan en minuscula (ver register()): sin
        // esto, loguearse con "Sol@gmail.com" no encontraba a "sol@gmail.com".
        return userDao.findByEmail(email.trim().toLowerCase());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserRegistrationResult register(final String email, final String name, final String rawPassword,
                                           final String redirectAfterConfirm) {
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
        // Reconciliacion del Grupo B: si esta persona ya venia con invitaciones
        // o avisos de bandeja sin resolver (invitada sin cuenta, ver
        // EventInvitationServiceImpl.invite()/InboxServiceImpl.add()), ahora
        // que tiene un id se los enlazamos. No hace nada si no tenia ninguno.
        invitationDao.linkUserId(normalizedEmail, userId);
        inboxMessageDao.linkUserId(normalizedEmail, userId);
        // El hash recien aca, despues del chequeo de EMAIL_TAKEN: BCrypt es lento
        // a proposito y no tiene sentido pagarlo si el registro se rechaza.
        userDao.setPassword(userId, passwordEncoder.encode(rawPassword));
        userDao.addRole(userId, ROLE_USER);
        // Toda cuenta nueva (o fantasma recien completada) tiene que confirmar
        // el mail antes de poder loguearse: ver UserDetailsServiceImpl.
        userDao.setVerified(userId, false);
        // Si todavia no habia elegido un idioma a mano (cuenta nueva, o fantasma
        // que nunca toco el boton de idioma), le guardamos el que ya tenia puesto
        // el sitio en esta misma request (cookie de LocaleResolver, ver WebConfig):
        // asi el mail de confirmacion que sigue ya sale en ese idioma, no en el
        // default (ver NotificationServiceImpl.notifyAccountVerification()).
        if (!existing.map(User::getLocale).isPresent()) {
            userDao.setLocale(userId, LocaleContextHolder.getLocale().getLanguage());
        }
        sendVerificationEmail(userDao.findById(userId).orElseThrow(IllegalStateException::new), redirectAfterConfirm);
        return UserRegistrationResult.CREATED;
    }

    @Override
    @Transactional
    public Optional<User> confirmEmail(final String token) {
        final Optional<VerificationToken> found = validToken(token, TokenPurpose.VERIFY_EMAIL);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        final long userId = found.get().getUserId();
        userDao.setVerified(userId, true);
        tokenDao.delete(token);
        return userDao.findById(userId);
    }

    @Override
    @Transactional
    public boolean resendVerificationEmail(final String email) {
        return userDao.findByEmail(email.trim().toLowerCase())
                .filter(user -> user.getPassword() != null && !user.isVerified())
                // Reenvio desde el login: ya no sabemos de donde venia, sin redirect.
                .map(user -> {
                    sendVerificationEmail(user, null);
                    return true;
                })
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean sendPasswordResetEmail(final String email) {
        return userDao.findByEmail(email.trim().toLowerCase())
                .filter(user -> user.getPassword() != null)
                .map(user -> {
                    tokenDao.deleteByUserAndPurpose(user.getId(), TokenPurpose.RESET_PASSWORD);
                    final String token = newToken(user.getId(), TokenPurpose.RESET_PASSWORD, RESET_TOKEN_TTL_MILLIS);
                    notificationService.notifyPasswordReset(user, token);
                    return true;
                })
                .orElse(false);
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

    private void sendVerificationEmail(final User user, final String redirectAfterConfirm) {
        tokenDao.deleteByUserAndPurpose(user.getId(), TokenPurpose.VERIFY_EMAIL);
        final String token = newToken(user.getId(), TokenPurpose.VERIFY_EMAIL, VERIFY_TOKEN_TTL_MILLIS);
        notificationService.notifyAccountVerification(user, token, redirectAfterConfirm);
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

    @Override
    @Transactional
    public boolean updateLocale(final long userId, final String locale) {
        return userDao.setLocale(userId, locale);
    }

    @Override
    @Transactional
    public boolean suspend(final long userId) {
        final boolean changed = userDao.setSuspended(userId, true);
        userDao.findById(userId).ifPresent(user -> {
            notificationService.notifyUserSuspended(user);
            addInboxMessage(user, InboxMessageType.ACCOUNT_SUSPENDED, null);
        });
        return changed;
    }

    @Override
    @Transactional
    public boolean reactivate(final long userId) {
        final boolean changed = userDao.setSuspended(userId, false);
        userDao.findById(userId).ifPresent(user -> {
            notificationService.notifyUserReactivated(user);
            addInboxMessage(user, InboxMessageType.ACCOUNT_REACTIVATED, null);
        });
        return changed;
    }

    @Override
    @Transactional
    public boolean grantAdmin(final long userId) {
        return userDao.addRole(userId, ROLE_ADMIN);
    }

    @Override
    @Transactional
    public boolean revokeAdmin(final long userId) {
        return userDao.removeRole(userId, ROLE_ADMIN);
    }

    // Igual que InboxServiceImpl.add(), pero sin pasar por ese service (evitaria
    // una dependencia circular: InboxServiceImpl ya depende de UserService para
    // resolver el userId por email, algo que aca ni hace falta -- ya tenemos el
    // User entero). eventId siempre null: son avisos de cuenta, no de un evento.
    private void addInboxMessage(final User user, final InboxMessageType type, final String body) {
        final InboxMessage message = new InboxMessage(user.getEmail(), type, null, null, body);
        message.setUserId(user.getId());
        inboxMessageDao.create(message);
    }
}
