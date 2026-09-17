// Implementa el contrato RegistrationDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class RegistrationJdbcDao implements RegistrationDao {

    private static final RowMapper<Registration> ROW_MAPPER = (rs, rowNum) -> {
        final Registration registration = new Registration();
        registration.setId(rs.getLong("id"));
        registration.setEventId(rs.getLong("event_id"));
        registration.setEmail(rs.getString("email"));
        registration.setName(rs.getString("name"));
        registration.setRegisteredAt(rs.getTimestamp("registered_at"));
        registration.setStatus(rs.getString("status"));
        return registration;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public RegistrationJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("registration")
                .usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "email", "name", "status");
    }

    @Override
    public Registration create(final Registration registration) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", registration.getEventId());
        args.put("email", registration.getEmail());
        args.put("name", registration.getName());
        args.put("status", registration.getStatus());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        registration.setId(id.longValue());
        return registration;
    }

    @Override
    public Optional<Registration> findByEventAndEmail(final long eventId, final String email) {
        return jdbcTemplate.query("SELECT * FROM registration WHERE event_id = ? AND email = ?",
                ROW_MAPPER, eventId, email).stream().findFirst();
    }

    @Override
    public long countConfirmed(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE event_id = ? AND status = ?",
                Long.class, eventId, Registration.STATUS_CONFIRMED);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Registration> findById(final long id) {
        return jdbcTemplate.query("SELECT * FROM registration WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<Registration> findByEmail(final String email) {
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE email = ? ORDER BY registered_at DESC",
                ROW_MAPPER, email);
    }

    @Override
    public void delete(final long id) {
        jdbcTemplate.update("DELETE FROM registration WHERE id = ?", id);
    }
}
