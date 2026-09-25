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
                .usingColumns("event_id", "email", "name", "status");
    }

    @Override
    public Registration create(final Registration registration) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", registration.getEventId());
        args.put("email", registration.getEmail());
        args.put("name", registration.getName());
        args.put("status", registration.getStatus().name());

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
                Long.class, eventId, RegistrationStatus.CONFIRMED.name());
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
    public List<Registration> findByEventId(final long eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM registration WHERE event_id = ? ORDER BY registered_at ASC",
                ROW_MAPPER, eventId);
    }

    @Override
    public List<Registration> findNextWaitlisted(final long eventId, final int limit) {
        // El LEFT JOIN + "i.id IS NULL" es la regla "una oportunidad por persona":
        // quien ya tiene invitacion (pendiente o aceptada) no vuelve a aparecer.
        // Al que rechazo o se le vencio ya se le borro la inscripcion entera, asi
        // que tampoco esta. El orden sale de registered_at: el que llego primero.
        return jdbcTemplate.query(
                "SELECT r.* FROM registration r "
                        + "LEFT JOIN waitlist_invitation i ON i.registration_id = r.id "
                        + "WHERE r.event_id = ? AND r.status = ? AND i.id IS NULL "
                        + "ORDER BY r.registered_at LIMIT ?",
                ROW_MAPPER, eventId, RegistrationStatus.WAITLISTED.name(), limit);
    }

    @Override
    public List<Long> findEventIdsWithWaitlist(final java.util.Date now) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT r.event_id FROM registration r "
                        + "JOIN event e ON e.id = r.event_id "
                        + "WHERE r.status = ? AND e.max_capacity IS NOT NULL AND e.event_date > ? "
                        + "AND e.cancelled_at IS NULL",
                Long.class, RegistrationStatus.WAITLISTED.name(), new java.sql.Timestamp(now.getTime()));
    }

    @Override
    public void updateStatus(final long id, final RegistrationStatus status) {
        jdbcTemplate.update("UPDATE registration SET status = ? WHERE id = ?", status.name(), id);
    }

    @Override
    public void delete(final long id) {
        jdbcTemplate.update("DELETE FROM registration WHERE id = ?", id);
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
