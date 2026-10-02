// Implementa el contrato RegistrationDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
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
        final Integer userId = rs.getObject("user_id", Integer.class);
        registration.setUserId(userId == null ? null : userId.longValue());
        registration.setRegisteredAt(rs.getTimestamp("registered_at"));
        registration.setStatus(RegistrationStatus.valueOf(rs.getString("status")));
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
                .usingColumns("event_id", "email", "name", "status", "user_id");
    }

    @Override
    public Registration create(final Registration registration) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", registration.getEventId());
        args.put("email", registration.getEmail());
        args.put("name", registration.getName());
        args.put("status", registration.getStatus().name());
        args.put("user_id", registration.getUserId());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        registration.setId(id.longValue());
        return registration;
    }

    @Override
    public Optional<Registration> findByEventAndUserId(final long eventId, final long userId) {
        return jdbcTemplate.query("SELECT * FROM registration WHERE event_id = ? AND user_id = ?",
                ROW_MAPPER, eventId, userId).stream().findFirst();
    }

    @Override
    public long countConfirmed(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE event_id = ? AND status = ?",
                Long.class, eventId, RegistrationStatus.CONFIRMED.name());
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Registration> findById(final long id) {
        return jdbcTemplate.query("SELECT * FROM registration WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<Registration> findByUserId(final long userId) {
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE user_id = ? ORDER BY registered_at DESC",
                ROW_MAPPER, userId);
    }

    @Override
    public List<Registration> findByEventId(final long eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE event_id = ? ORDER BY registered_at ASC",
                ROW_MAPPER, eventId);
    }

    @Override
    public List<Registration> findNextWaitlisted(final long eventId, final int limit) {
        // El orden sale de registered_at: el que llego primero a la lista.
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE event_id = ? AND status = ? "
                        + "ORDER BY registered_at, id LIMIT ?",
                ROW_MAPPER, eventId, RegistrationStatus.WAITLISTED.name(), limit);
    }

    @Override
    public boolean updateStatus(final long id, final RegistrationStatus status) {
        return jdbcTemplate.update("UPDATE registration SET status = ? WHERE id = ?", status.name(), id) == 1;
    }

    @Override
    public boolean delete(final long id) {
        return jdbcTemplate.update("DELETE FROM registration WHERE id = ?", id) == 1;
    }

    @Override
    public long countPending(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE event_id = ? AND status = ?",
                Long.class, eventId, RegistrationStatus.PENDING.name());
        return count == null ? 0 : count;
    }

    @Override
    public List<Registration> findPendingByEvent(final long eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE event_id = ? AND status = ? ORDER BY registered_at",
                ROW_MAPPER, eventId, RegistrationStatus.PENDING.name());
    }
}
