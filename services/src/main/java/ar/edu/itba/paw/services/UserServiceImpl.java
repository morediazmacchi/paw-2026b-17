// Implementa el contrato UserService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.dto.UserRegistrationDTO;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.SupportedLocale;
import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.EventInvitationDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import ar.edu.itba.paw.persistence.UserDao;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    static final int PAGE_SIZE = 20;

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
    // Para anunciar que a alguien se le quito acceso (ver UserAccessRevokedEvent).
    private final ApplicationEventPublisher eventPublisher;
    // Para guardar (y validar) la foto de portada del perfil.
    private final ImageService imageService;

    @Autowired
    public UserServiceImpl(final UserDao userDao, final VerificationTokenDao tokenDao,
                           final PasswordEncoder passwordEncoder, final NotificationService notificationService,
                           final EventInvitationDao invitationDao, final InboxMessageDao inboxMessageDao,
                           final ApplicationEventPublisher eventPublisher, final ImageService imageService) {
        this.userDao = userDao;
        this.tokenDao = tokenDao;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
        this.invitationDao = invitationDao;
        this.inboxMessageDao = inboxMessageDao;
        this.eventPublisher = eventPublisher;
        this.imageService = imageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(final long id) {
        return userDao.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAccess> findAccessById(final long id) {
        return userDao.findAccessById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByIds(final List<Long> ids) {
        return userDao.findByIds(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> findPage(final int page) {
        final long totalElements = userDao.countAll();
        final int totalPages = (int) Math.ceil((double) totalElements / PAGE_SIZE);
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        final List<User> content = userDao.findPage(PAGE_SIZE, (effectivePage - 1) * PAGE_SIZE);
        return new PageResult<>(content, totalElements, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(final String email) {
        // En la base los emails se guardan en minuscula (ver register()): sin
        // esto, loguearse con "Sol@gmail.com" no encontraba a "sol@gmail.com".
        return userDao.findByEmail(email.trim().toLowerCase());
    }

    @Override
    @Transactional
    public UserRegistrationResult register(final UserRegistrationDTO data) {
        // Se valida ANTES de escribir. El form ya lo chequea para dar feedback; esto es la defensa del service.
        if (isWeak(data.getRawPassword())) {
            return UserRegistrationResult.WEAK_PASSWORD;
        }
        final String normalizedEmail = data.getEmail().trim().toLowerCase();

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
            try {
                userId = userDao.create(normalizedEmail, data.getName()).getId();
            } catch (final DataIntegrityViolationException e) {
                // Dos registros al mismo tiempo con el mismo email nuevo: el
                // findByEmail de arriba no vio nada, pero el otro ya inserto
                // justo antes y choca contra el UNIQUE(email). Mismo criterio
                // que ReviewServiceImpl.create(): no corresponde dejar escapar
                // esto hacia el controller como un 500.
                // Con JPA, el insert fallido ya dejo la transaccion marcada para rollback: se lo
                // decimos explicitamente, asi Spring hace rollback sin tirar UnexpectedRollbackException.
                RollbackOnly.ifInTransaction();
                return UserRegistrationResult.EMAIL_TAKEN;
            }
        }
        // Reconciliacion del Grupo B: si esta persona ya venia con invitaciones
        // o avisos de bandeja sin resolver (invitada sin cuenta, ver
        // EventInvitationServiceImpl.invite()/InboxServiceImpl.add()), ahora
        // que tiene un id se los enlazamos. No hace nada si no tenia ninguno.
        invitationDao.linkUserId(normalizedEmail, userId);
        inboxMessageDao.linkUserId(normalizedEmail, userId);
        // El hash recien aca, despues del chequeo de EMAIL_TAKEN: BCrypt es lento
        // a proposito y no tiene sentido pagarlo si el registro se rechaza.
        userDao.setPassword(userId, passwordEncoder.encode(data.getRawPassword()));
        userDao.addRole(userId, Role.USER);
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
        sendVerificationEmail(userDao.findById(userId).orElseThrow(IllegalStateException::new),
                data.getRedirectAfterConfirm());
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
                    final String token = newToken(user, TokenPurpose.RESET_PASSWORD, RESET_TOKEN_TTL_MILLIS);
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
        if (isWeak(newPassword)) {
            return PasswordResetResult.WEAK_PASSWORD;
        }
        final long userId = found.get().getUserId();
        userDao.setPassword(userId, passwordEncoder.encode(newPassword));
        tokenDao.delete(token);
        // Cualquier sesion que ya tuviera esa cuenta deja de valer (por eso se pidio restablecer la clave).
        eventPublisher.publishEvent(new UserCredentialsChangedEvent(userId, false));
        return PasswordResetResult.OK;
    }

    // Una contraseña mas corta que el minimo no se guarda (ver UserService.MIN_PASSWORD_LENGTH).
    private static boolean isWeak(final String rawPassword) {
        return rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH;
    }

    private void sendVerificationEmail(final User user, final String redirectAfterConfirm) {
        tokenDao.deleteByUserAndPurpose(user.getId(), TokenPurpose.VERIFY_EMAIL);
        final String token = newToken(user, TokenPurpose.VERIFY_EMAIL, VERIFY_TOKEN_TTL_MILLIS);
        notificationService.notifyAccountVerification(user, token, redirectAfterConfirm);
    }

    private String newToken(final User user, final TokenPurpose purpose, final long ttlMillis) {
        final String token = UUID.randomUUID().toString();
        final Date expiresAt = new Date(System.currentTimeMillis() + ttlMillis);
        tokenDao.create(token, user, purpose, expiresAt);
        return token;
    }

    private Optional<VerificationToken> validToken(final String token, final TokenPurpose purpose) {
        return tokenDao.findByToken(token)
                .filter(t -> t.getPurpose() == purpose)
                .filter(t -> t.getExpiresAt().after(new Date()));
    }

    @Override
    @Transactional
    public UpdateProfileResult updateProfile(final long userId, final String name) {
        if (name == null || name.trim().isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return UpdateProfileResult.INVALID_NAME;
        }
        return userDao.updateProfile(userId, name) ? UpdateProfileResult.OK : UpdateProfileResult.NOT_FOUND;
    }

    @Override
    @Transactional
    public UpdateBioResult updateBio(final long userId, final String bio) {
        final String trimmed = bio == null ? "" : bio.trim();
        if (trimmed.length() > MAX_BIO_LENGTH) {
            return UpdateBioResult.INVALID;
        }
        // Una biografia vacia la borra: en la base queda NULL, no un string vacio.
        return userDao.updateBio(userId, trimmed.isEmpty() ? null : trimmed)
                ? UpdateBioResult.OK : UpdateBioResult.NOT_FOUND;
    }

    @Override
    @Transactional
    public UpdateCoverResult updateCover(final long userId, final byte[] data, final String contentType,
                                         final String fileName) {
        final Optional<User> user = userDao.findById(userId);
        if (!user.isPresent()) {
            return UpdateCoverResult.NOT_FOUND;
        }
        // Primero se valida y se guarda la nueva (lanza InvalidImageException sin tocar nada); recien despues se
        // cambia el puntero y se borra la anterior.
        final Image cover = imageService.create(data, contentType, fileName);
        final Long previous = user.get().getCoverImageId();
        userDao.updateCoverImage(userId, cover.getId());
        if (previous != null) {
            imageService.delete(previous);
        }
        return UpdateCoverResult.OK;
    }

    @Override
    @Transactional
    public UpdateCoverResult removeCover(final long userId) {
        final Optional<User> user = userDao.findById(userId);
        if (!user.isPresent()) {
            return UpdateCoverResult.NOT_FOUND;
        }
        final Long previous = user.get().getCoverImageId();
        userDao.updateCoverImage(userId, null);
        if (previous != null) {
            imageService.delete(previous);
        }
        return UpdateCoverResult.OK;
    }

    @Override
    @Transactional
    public ChangePasswordResult changePassword(final long userId, final String currentPassword,
                                               final String newPassword) {
        final User user = userDao.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario inexistente: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ChangePasswordResult.WRONG_CURRENT_PASSWORD;
        }
        if (isWeak(newPassword)) {
            return ChangePasswordResult.WEAK_PASSWORD;
        }

        userDao.setPassword(userId, passwordEncoder.encode(newPassword));
        // Se cierran las OTRAS sesiones de esta cuenta; la actual (desde donde se cambio la clave) sigue.
        eventPublisher.publishEvent(new UserCredentialsChangedEvent(userId, true));
        return ChangePasswordResult.OK;
    }

    @Override
    @Transactional
    public boolean updateLocale(final long userId, final String locale) {
        // Solo los idiomas soportados: otro valor no se guarda.
        if (!SupportedLocale.fromCode(locale).isPresent()) {
            return false;
        }
        return userDao.setLocale(userId, locale);
    }

    @Override
    @Transactional
    public SuspendResult suspend(final User caller, final long targetId) {
        // Se valida ANTES de escribir.
        if (caller.isSameAccountAs(targetId)) {
            return SuspendResult.OWN_ACCOUNT;
        }
        return suspend(targetId) ? SuspendResult.OK : SuspendResult.NOT_FOUND;
    }

    @Override
    @Transactional
    public boolean suspend(final long userId) {
        final boolean changed = userDao.setSuspended(userId, true);
        userDao.findById(userId).ifPresent(user -> {
            notificationService.notifyUserSuspended(user);
            addInboxMessage(user, InboxMessageType.ACCOUNT_SUSPENDED, null);
        });
        if (changed) {
            // Las sesiones abiertas de esa cuenta dejan de valer: la web las cierra despues del commit.
            eventPublisher.publishEvent(new UserAccessRevokedEvent(userId));
        }
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
        return userDao.addRole(userId, Role.ADMIN);
    }

    @Override
    @Transactional
    public RevokeAdminResult revokeAdmin(final User caller, final long targetId) {
        // Se valida ANTES de escribir. Un superadmin que se sacara el rol quedaria afuera de /admin/**.
        if (caller.isSameAccountAs(targetId)) {
            return RevokeAdminResult.OWN_ACCOUNT;
        }
        final UserAccess target = userDao.findAccessById(targetId).orElse(null);
        if (target == null) {
            return RevokeAdminResult.NOT_FOUND;
        }
        // Tampoco a otro superadmin: no hay herencia de roles, sin ADMIN perderia el acceso a /admin/**.
        if (target.isSuperadmin()) {
            return RevokeAdminResult.TARGET_SUPERADMIN;
        }
        if (userDao.removeRole(targetId, Role.ADMIN)) {
            // Su sesion todavia lleva el rol ADMIN del login: la web la cierra despues del commit.
            eventPublisher.publishEvent(new UserAccessRevokedEvent(targetId));
        }
        // Si ya no era admin el resultado buscado (sin el rol) ya se cumple.
        return RevokeAdminResult.OK;
    }

    // Igual que InboxServiceImpl.add(), pero sin pasar por ese service (evitaria
    // una dependencia circular: InboxServiceImpl ya depende de UserService para
    // resolver el userId por email, algo que aca ni hace falta -- ya tenemos el
    // User entero). eventId siempre null: son avisos de cuenta, no de un evento.
    private void addInboxMessage(final User user, final InboxMessageType type, final String body) {
        final InboxMessage message = new InboxMessage(user.getEmail(), type, null, null, body);
        message.setUser(user);
        inboxMessageDao.create(message);
    }
}
