// Implementa el contrato UserService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserDao userDao;

    @Autowired
    public UserServiceImpl(final UserDao userDao) {
        this.userDao = userDao;
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
}
