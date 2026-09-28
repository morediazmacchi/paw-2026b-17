// Implementa el contrato UserDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserJdbcDao implements UserDao {

    // LEFT JOIN: un usuario sin roles todavia (los tres que ya existian antes de
    // login) tiene que seguir apareciendo, con "role" en NULL para esa fila.
    private static final String SELECT_WITH_ROLES =
            "SELECT u.id, u.email, u.name, u.password, u.verified, u.preferred_locale, ur.role "
                    + "FROM users u LEFT JOIN user_role ur ON ur.user_id = u.id";

    // Una query, N filas posibles (una por rol, o una sola con role=NULL si no
    // tiene ninguno): hay que plegarlas en un solo User, por eso ResultSetExtractor
    // en vez de RowMapper (que mapea cada fila por separado, sin acumular estado).
    private static final ResultSetExtractor<Optional<User>> USER_WITH_ROLES_EXTRACTOR = rs -> {
        User user = null;
        final Set<String> roles = new HashSet<>();
        while (rs.next()) {
            if (user == null) {
                user = new User();
                user.setId(rs.getLong("id"));
                user.setEmail(rs.getString("email"));
                user.setName(rs.getString("name"));
                user.setPassword(rs.getString("password"));
                user.setVerified(rs.getBoolean("verified"));
                user.setLocale(rs.getString("preferred_locale"));
            }
            final String role = rs.getString("role");
            if (role != null) {
                roles.add(role);
            }
        }
        if (user == null) {
            return Optional.empty();
        }
        user.setRoles(roles);
        return Optional.of(user);
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public UserJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("users")
                .usingGeneratedKeyColumns("id")
                .usingColumns("email", "name");
    }

    @Override
    public User create(final User user) {
        final Map<String, Object> args = new HashMap<>();
        args.put("email", user.getEmail());
        args.put("name", user.getName());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        user.setId(id.longValue());
        return user;
    }

    @Override
    public Optional<User> findById(final long id) {
        return jdbcTemplate.query(SELECT_WITH_ROLES + " WHERE u.id = ?", USER_WITH_ROLES_EXTRACTOR, id);
    }

    @Override
    public Optional<User> findByEmail(final String email) {
        return jdbcTemplate.query(SELECT_WITH_ROLES + " WHERE u.email = ?", USER_WITH_ROLES_EXTRACTOR, email);
    }

    @Override
    public Set<String> findRoles(final long userId) {
        return new HashSet<>(jdbcTemplate.query(
                "SELECT role FROM user_role WHERE user_id = ?",
                (rs, rowNum) -> rs.getString("role"), userId));
    }

    @Override
    public void addRole(final long userId, final String role) {
        // "ON CONFLICT" es sintaxis de Postgres: no la entiende HSQLDB (los tests
        // de persistence corren ahi). El PRIMARY KEY compuesto (user_id, role) ya
        // garantiza que no se duplique; si el rol ya estaba, dejamos pasar la
        // violacion en vez de checkear antes (mismo patron que el resto del proyecto
        // usa para conflictos esperables, ej. RegistrationServiceImpl).
        try {
            jdbcTemplate.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role);
        } catch (final DataIntegrityViolationException e) {
            // Ya tenia ese rol asignado: idempotente, no hace falta nada mas.
        }
    }

    @Override
    public void setPassword(final long userId, final String passwordHash) {
        jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?", passwordHash, userId);
    }

    @Override
    public void updateProfile(final long userId, final String name, final String email) {
        jdbcTemplate.update("UPDATE users SET name = ?, email = ? WHERE id = ?", name, email, userId);
    }

    @Override
    public void setVerified(final long userId, final boolean verified) {
        jdbcTemplate.update("UPDATE users SET verified = ? WHERE id = ?", verified, userId);
    }

    @Override
    public void setLocale(final long userId, final String locale) {
        jdbcTemplate.update("UPDATE users SET preferred_locale = ? WHERE id = ?", locale, userId);
    }
}
