// Implementa el contrato UserDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Repository
public class UserJpaDao implements UserDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public User create(final User user) {
        // IDENTITY: el INSERT corre aca mismo, asi que un email repetido
        // (UNIQUE) sale como DataIntegrityViolationException desde este metodo.
        em.persist(user);
        return user;
    }

    @Override
    public Optional<User> findById(final long id) {
        // Los roles (EAGER) vienen en el mismo find.
        return Optional.ofNullable(em.find(User.class, id));
    }

    @Override
    public Optional<User> findByEmail(final String email) {
        // LEFT JOIN FETCH + DISTINCT: un usuario sin roles igual tiene que aparecer,
        // y los que tienen varios no se repiten por cada rol.
        return em.createQuery(
                        "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email", User.class)
                .setParameter("email", email)
                .getResultList().stream().findFirst();
    }

    @Override
    public List<User> findPage(final int limit, final int offset) {
        // Paginar sobre usuarios y no sobre las filas del JOIN con roles (un usuario con
        // dos roles son dos filas): primero los ids de la pagina, despues los usuarios.
        final List<Long> ids = em.createQuery("SELECT u.id FROM User u ORDER BY u.id", Long.class)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
        return findByIds(ids);
    }

    @Override
    public long countAll() {
        return em.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult();
    }

    @Override
    public List<User> findAll() {
        return em.createQuery("SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles ORDER BY u.id", User.class)
                .getResultList();
    }

    @Override
    public List<User> findByIds(final List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery(
                        "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN :ids ORDER BY u.id",
                        User.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public boolean addRole(final long userId, final String role) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        // Set.add() ya dice si el rol era nuevo: idempotente, false si ya lo tenia.
        // El INSERT en user_role lo hace Hibernate al flush.
        return user.getRoles().add(role);
    }

    @Override
    public boolean removeRole(final long userId, final String role) {
        final User user = em.find(User.class, userId);
        return user != null && user.getRoles().remove(role);
    }

    @Override
    public boolean setPassword(final long userId, final String passwordHash) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setPassword(passwordHash);
        return true;
    }

    @Override
    public boolean updateProfile(final long userId, final String name) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setName(name);
        return true;
    }

    @Override
    public boolean setVerified(final long userId, final boolean verified) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setVerified(verified);
        return true;
    }

    @Override
    public boolean setLocale(final long userId, final String locale) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setLocale(locale);
        return true;
    }

    @Override
    public boolean setSuspended(final long userId, final boolean suspended) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setSuspended(suspended);
        return true;
    }
}
