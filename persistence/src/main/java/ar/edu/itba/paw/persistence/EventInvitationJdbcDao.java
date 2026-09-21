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
        invitation.setToken(rs.getString("token"));
        invitation.setInvitedBy(rs.getString("invited_by"));
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
                .usingColumns("event_id", "email", "token", "invited_by");
    }

    @Override
    public EventInvitation create(final EventInvitation invitation) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", invitation.getEventId());
        args.put("email", invitation.getEmail());
        args.put("token", invitation.getToken());
        args.put("invited_by", invitation.getInvitedBy());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        invitation.setId(id.longValue());
        return invitation;
    }

    @Override
    public Optional<EventInvitation> findByToken(final String token) {
        return jdbcTemplate.query("SELECT * FROM event_invitation WHERE token = ?", ROW_MAPPER, token)
                .stream().findFirst();
    }

    @Override
    public Optional<EventInvitation> findByEventAndEmail(final long eventId, final String email) {
        return jdbcTemplate.query("SELECT * FROM event_invitation WHERE event_id = ? AND email = ?",
                ROW_MAPPER, eventId, email).stream().findFirst();
    }

    @Override
    public List<EventInvitation> findByEvent(final long eventId) {
        return jdbcTemplate.query(
                "SELECT * FROM event_invitation WHERE event_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, eventId);
    }
}
