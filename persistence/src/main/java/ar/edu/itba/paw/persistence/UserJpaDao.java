// Implementa el contrato UserDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;
import org.hibernate.jpa.QueryHints;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserJpaDao implements UserDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public User create(final String email, final String name) {
        final User user = new User(email, name);
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
    public Optional<UserAccess> findAccessById(final long id) {
        // Proyeccion: id y roles, sin cargar el User (ni la contrasena). Una fila por rol; LEFT JOIN
        // para distinguir "no existe" (sin filas) de "existe sin roles" (una fila con rol nulo).
        // Se juntan TODOS los roles de las filas.
        final List<Object[]> rows = em.createQuery(
                        "SELECT u.id, r FROM User u LEFT JOIN u.roles r WHERE u.id = :id", Object[].class)
                .setParameter("id", id)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        final Set<Role> roles = EnumSet.noneOf(Role.class);
        for (final Object[] row : rows) {
            if (row[1] != null) {
                roles.add((Role) row[1]);
            }
        }
        return Optional.of(new UserAccess(((Number) rows.get(0)[0]).longValue(), roles));
    }

    @Override
    public Optional<PublicUser> findActivePublicById(final long id) {
        // Proyeccion: solo lo que dibuja el perfil publico, sin cargar el User (ni su mail, ni su clave, ni sus roles).
        // El WHERE es User.isActive() (tiene clave, mail confirmado, no suspendida): si cambia una, cambiar la otra.
        final List<Object[]> rows = em.createQuery(
                        "SELECT u.id, u.name, u.bio, u.coverImageId FROM User u WHERE u.id = :id "
                                + "AND u.password IS NOT NULL AND u.verified = true AND u.suspended = false",
                        Object[].class)
                .setParameter("id", id)
                .getResultList();
        return rows.stream()
                .map(row -> new PublicUser(((Number) row[0]).longValue(), (String) row[1], (String) row[2],
                        row[3] == null ? null : ((Number) row[3]).longValue()))
                .findFirst();
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

    // Caracter de escape del LIKE de la busqueda por mail. "!" y no "\" a proposito: una barra invertida dentro de un
    // literal JPQL se presta a confusion. Hace falta escapar porque el "_" es de lo mas comun en un mail.
    private static final String LIKE_ESCAPE = "!";

    @Override
    public List<User> findPage(final String emailText, final int limit, final int offset) {
        final String text = normalized(emailText);
        // Paginar sobre usuarios y no sobre las filas del JOIN con roles (un usuario con
        // dos roles son dos filas): primero los ids de la pagina, despues los usuarios.
        final TypedQuery<Long> idsQuery;
        // El ORDER BY de la pagina: la consulta de ids y la de usuarios usan EXACTAMENTE el mismo (el IN no ordena).
        final String orderBy;
        final Map<String, Object> orderParams = new HashMap<>();
        if (text.isEmpty()) {
            orderBy = "u.id";
            idsQuery = em.createQuery("SELECT u.id FROM User u ORDER BY " + orderBy, Long.class);
        } else {
            // Mismo WHERE que count(). El orden: el mail igual al texto, despues los que empiezan con el, despues el resto.
            final String escaped = escapeLike(text);
            orderBy = "CASE WHEN LOWER(u.email) = :exact THEN 0 "
                    + "WHEN LOWER(u.email) LIKE :prefix ESCAPE '" + LIKE_ESCAPE + "' THEN 1 ELSE 2 END, "
                    + "u.email, u.id";
            idsQuery = em.createQuery(
                            "SELECT u.id FROM User u WHERE LOWER(u.email) LIKE :contains ESCAPE '" + LIKE_ESCAPE + "' "
                                    + "ORDER BY " + orderBy, Long.class)
                    .setParameter("contains", "%" + escaped + "%")
                    .setParameter("prefix", escaped + "%")
                    .setParameter("exact", text);
            orderParams.put("prefix", escaped + "%");
            orderParams.put("exact", text);
        }
        final List<Long> ids = idsQuery.setFirstResult(offset).setMaxResults(limit).getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        final TypedQuery<User> usersQuery = em.createQuery(
                        "SELECT DISTINCT u FROM User u LEFT JOIN FETCH u.roles WHERE u.id IN :ids ORDER BY " + orderBy,
                        User.class)
                .setParameter("ids", ids)
                // DISTINCT solo en memoria (por el JOIN con roles): el ORDER BY usa una expresion que no esta en el
                // SELECT, y con el DISTINCT de SQL Postgres la rechaza.
                .setHint(QueryHints.HINT_PASS_DISTINCT_THROUGH, false);
        orderParams.forEach(usersQuery::setParameter);
        return usersQuery.getResultList();
    }

    @Override
    public long count(final String emailText) {
        final String text = normalized(emailText);
        if (text.isEmpty()) {
            return em.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult();
        }
        return em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) LIKE :contains ESCAPE '" + LIKE_ESCAPE + "'",
                        Long.class)
                .setParameter("contains", "%" + escapeLike(text) + "%")
                .getSingleResult();
    }

    // Sin espacios de los costados y en minuscula (la comparacion es contra LOWER(email)). "" si no hay texto.
    private static String normalized(final String emailText) {
        return emailText == null ? "" : emailText.trim().toLowerCase(Locale.ROOT);
    }

    // El texto como literal de un LIKE: %, _ y el propio caracter de escape pasan a ser letras comunes.
    private static String escapeLike(final String text) {
        return text.replace(LIKE_ESCAPE, LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
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
    public boolean addRole(final long userId, final Role role) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        // Set.add() ya dice si el rol era nuevo: idempotente, false si ya lo tenia.
        // El INSERT en user_role lo hace Hibernate al flush.
        return user.getRoles().add(role);
    }

    @Override
    public boolean removeRole(final long userId, final Role role) {
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
    public boolean updateBio(final long userId, final String bio) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setBio(bio);
        return true;
    }

    @Override
    public boolean updateCoverImage(final long userId, final Long imageId) {
        final User user = em.find(User.class, userId);
        if (user == null) {
            return false;
        }
        user.setCoverImageId(imageId);
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
