// Implementa el contrato EventInvitationDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventInvitation;
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
public class EventInvitationJdbcDao implements EventInvitationDao {

    private static final RowMapper<EventInvitation> ROW_MAPPER = (rs, rowNum) -> {
        final EventInvitation invitation = new EventInvitation();
        invitation.setId(rs.getLong("id"));
        invitation.setEventId(rs.getLong("event_id"));
        invitation.setEmail(rs.getString("email"));
        invitation.setInvitedBy(rs.getString("invited_by"));
        final Integer invitedByUserId = rs.getObject("invited_by_user_id", Integer.class);
        invitation.setInvitedByUserId(invitedByUserId == null ? null : invitedByUserId.longValue());
        final Integer userId = rs.getObject("user_id", Integer.class);
        invitation.setUserId(userId == null ? null : userId.longValue());
        invitation.setCreatedAt(rs.getTimestamp("created_at"));
        return invitation;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventInvitationJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("event_invitation")
                .usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "email", "invited_by", "invited_by_user_id", "user_id");
    }

    @Override
    public EventInvitation create(final EventInvitation invitation) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", invitation.getEventId());
        args.put("email", invitation.getEmail());
        args.put("invited_by", invitation.getInvitedBy());
        args.put("invited_by_user_id", invitation.getInvitedByUserId());
        args.put("user_id", invitation.getUserId());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        invitation.setId(id.longValue());
        return invitation;
    }

    @Override
    public Optional<EventInvitation> findByEventAndEmail(final long eventId, final String email) {
        return jdbcTemplate.query("SELECT * FROM event_invitation WHERE event_id = ? AND email = ?",
                ROW_MAPPER, eventId, email).stream().findFirst();
    }

    @Override
    public Optional<EventInvitation> findByEventAndUserId(final long eventId, final long userId) {
        return jdbcTemplate.query("SELECT * FROM event_invitation WHERE event_id = ? AND user_id = ?",
                ROW_MAPPER, eventId, userId).stream().findFirst();
    }

    @Override
    public long countOutstanding(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_invitation i WHERE i.event_id = ? AND NOT EXISTS ("
                        + "SELECT 1 FROM registration r WHERE r.event_id = i.event_id AND r.email = i.email)",
                Long.class, eventId);
        return count == null ? 0 : count;
    }

    @Override
    public List<EventInvitation> findByEvent(final long eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM event_invitation WHERE event_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, eventId);
    }

    @Override
    public List<EventInvitation> findByUserId(final long userId, final int page, final int pageSize) {
        return jdbcTemplate.query(
                "SELECT * FROM event_invitation WHERE user_id = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, userId, pageSize, (page - 1) * pageSize);
    }

    @Override
    public long countByUserId(final long userId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_invitation WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public int linkUserId(final String email, final long userId) {
        return jdbcTemplate.update(
                "UPDATE event_invitation SET user_id = ? WHERE email = ? AND user_id IS NULL",
                userId, email);
    }
}
