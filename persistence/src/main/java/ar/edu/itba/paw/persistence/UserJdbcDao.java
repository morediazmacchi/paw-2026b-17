// Implementa el contrato UserDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserJdbcDao implements UserDao {

    // LEFT JOIN: un usuario sin roles todavia (los tres que ya existian antes de
    // login) tiene que seguir apareciendo, con "role" en NULL para esa fila.
    private static final String SELECT_WITH_ROLES =
            "SELECT u.id, u.email, u.name, u.password, u.verified, u.preferred_locale, u.suspended, ur.role "
                    + "FROM users u LEFT JOIN user_role ur ON ur.user_id = u.id";

    // Una query, N filas posibles (una por rol, o una sola con role=NULL si no
    // tiene ninguno): hay que plegarlas en un solo User por id, por eso
    // pivotRow() en vez de un RowMapper comun (que mapea cada fila por
    // separado, sin acumular estado). Un solo pivoteo para los 3 finders
    // (antes estaba copiado 3 veces: este extractor, findAll() y findByIds()).
    private static void pivotRow(final ResultSet rs, final Map<Long, User> byId) throws SQLException {
        final long id = rs.getLong("id");
        User user = byId.get(id);
        if (user == null) {
            user = new User();
            user.setId(id);
            user.setEmail(rs.getString("email"));
            user.setName(rs.getString("name"));
            user.setPassword(rs.getString("password"));
            user.setVerified(rs.getBoolean("verified"));
            user.setLocale(rs.getString("preferred_locale"));
            user.setSuspended(rs.getBoolean("suspended"));
            user.setRoles(new HashSet<>());
            byId.put(id, user);
        }
        final String role = rs.getString("role");
        if (role != null) {
            user.getRoles().add(role);
        }
    }

    // findById()/findByEmail(): el WHERE ya garantiza como mucho un usuario, asi
    // que el mapa pivoteado tiene a lo sumo una entrada.
    private static final ResultSetExtractor<Optional<User>> USER_WITH_ROLES_EXTRACTOR = rs -> {
        final Map<Long, User> byId = new LinkedHashMap<>();
        while (rs.next()) {
            pivotRow(rs, byId);
        }
        return byId.values().stream().findFirst();
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
    public List<User> findAll() {
        // LinkedHashMap: conserva el orden del ORDER BY mientras pivotRow() pliega
        // las filas (una por rol) en un solo User por id.
        final Map<Long, User> byId = new LinkedHashMap<>();
        jdbcTemplate.query(SELECT_WITH_ROLES + " ORDER BY u.id", (RowCallbackHandler) rs -> pivotRow(rs, byId));
        return new ArrayList<>(byId.values());
    }

    @Override
    public List<User> findByIds(final List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // Mismo pivoteo que findAll(), filtrado a estos ids.
        final Map<Long, User> byId = new LinkedHashMap<>();
        jdbcTemplate.query(
                SELECT_WITH_ROLES + " WHERE u.id IN (" + SqlPlaceholders.of(ids.size()) + ") ORDER BY u.id",
                (RowCallbackHandler) rs -> pivotRow(rs, byId),
                ids.toArray());
        return new ArrayList<>(byId.values());
    }

    @Override
    public boolean addRole(final long userId, final String role) {
        // "ON CONFLICT" es sintaxis de Postgres: no la entiende HSQLDB (los tests
        // de persistence corren ahi). El PRIMARY KEY compuesto (user_id, role) ya
        // garantiza que no se duplique; si el rol ya estaba, dejamos pasar la
        // violacion en vez de checkear antes (mismo patron que el resto del proyecto
        // usa para conflictos esperables, ej. RegistrationServiceImpl).
        try {
            return jdbcTemplate.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role) == 1;
        } catch (final DataIntegrityViolationException e) {
            // Ya tenia ese rol asignado: idempotente, devuelve false.
            return false;
        }
    }

    @Override
    public boolean removeRole(final long userId, final String role) {
        return jdbcTemplate.update("DELETE FROM user_role WHERE user_id = ? AND role = ?", userId, role) == 1;
    }

    @Override
    public boolean setPassword(final long userId, final String passwordHash) {
        return jdbcTemplate.update("UPDATE users SET password = ? WHERE id = ?", passwordHash, userId) == 1;
    }

    @Override
    public boolean updateProfile(final long userId, final String name, final String email) {
        return jdbcTemplate.update("UPDATE users SET name = ?, email = ? WHERE id = ?", name, email, userId) == 1;
    }

    @Override
    public boolean setVerified(final long userId, final boolean verified) {
        return jdbcTemplate.update("UPDATE users SET verified = ? WHERE id = ?", verified, userId) == 1;
    }

    @Override
    public boolean setLocale(final long userId, final String locale) {
        return jdbcTemplate.update("UPDATE users SET preferred_locale = ? WHERE id = ?", locale, userId) == 1;
    }

    @Override
    public boolean setSuspended(final long userId, final boolean suspended) {
        return jdbcTemplate.update("UPDATE users SET suspended = ? WHERE id = ?", suspended, userId) == 1;
    }
}
