// Implementa el contrato UserService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private static final String ROLE_USER = "ROLE_USER";

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserServiceImpl(final UserDao userDao, final PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
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
        return userDao.findByEmail(email);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserRegistrationResult register(final String email, final String name, final String rawPassword) {
        final String normalizedEmail = email.trim().toLowerCase();
        final String hash = passwordEncoder.encode(rawPassword);

        final Optional<User> existing = userDao.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            final User user = existing.get();
            if (user.getPassword() != null) {
                return UserRegistrationResult.EMAIL_TAKEN;
            }
            // Usuario fantasma (findOrCreate, nunca tuvo password ni pudo
            // loguearse): registrarse con ese email lo completa, no lo pisa.
            userDao.setPassword(user.getId(), hash);
            userDao.addRole(user.getId(), ROLE_USER);
            return UserRegistrationResult.CREATED;
        }

        final User created = userDao.create(new User(normalizedEmail, name));
        userDao.setPassword(created.getId(), hash);
        userDao.addRole(created.getId(), ROLE_USER);
        return UserRegistrationResult.CREATED;
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
