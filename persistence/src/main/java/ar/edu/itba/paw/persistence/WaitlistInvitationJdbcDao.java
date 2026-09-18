// Implementa el contrato WaitlistInvitationDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InvitationStatus;
import ar.edu.itba.paw.model.WaitlistInvitation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class WaitlistInvitationJdbcDao implements WaitlistInvitationDao {

    private static final RowMapper<WaitlistInvitation> ROW_MAPPER = (rs, rowNum) -> {
        final WaitlistInvitation invitation = new WaitlistInvitation();
        invitation.setId(rs.getLong("id"));
        invitation.setRegistrationId(rs.getLong("registration_id"));
        invitation.setToken(rs.getString("token"));
        invitation.setStatus(InvitationStatus.valueOf(rs.getString("status")));
        invitation.setSentAt(rs.getTimestamp("sent_at"));
        invitation.setExpiresAt(rs.getTimestamp("expires_at"));
        return invitation;
    };

    // La tabla no tiene event_id: se llega por la inscripcion. Se repite en dos
    // queries, asi que vive aca en vez de estar copiado.
    private static final String JOIN_EVENT =
            " FROM waitlist_invitation i JOIN registration r ON r.id = i.registration_id ";

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public WaitlistInvitationJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("waitlist_invitation")
                .usingGeneratedKeyColumns("id")
                .usingColumns("registration_id", "token", "status", "expires_at");
    }

    @Override
    public WaitlistInvitation create(final WaitlistInvitation invitation) {
        final Map<String, Object> args = new HashMap<>();
        args.put("registration_id", invitation.getRegistrationId());
        args.put("token", invitation.getToken());
        args.put("status", invitation.getStatus().name());
        args.put("expires_at", new Timestamp(invitation.getExpiresAt().getTime()));

        final Number id = jdbcInsert.executeAndReturnKey(args);
        invitation.setId(id.longValue());
        return invitation;
    }

    @Override
    public Optional<WaitlistInvitation> findByToken(final String token) {
        return jdbcTemplate.query("SELECT * FROM waitlist_invitation WHERE token = ?", ROW_MAPPER, token)
                .stream().findFirst();
    }

    @Override
    public long countPending(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*)" + JOIN_EVENT + "WHERE r.event_id = ? AND i.status = ?",
                Long.class, eventId, InvitationStatus.PENDING.name());
        return count == null ? 0 : count;
    }

    @Override
    public List<WaitlistInvitation> findExpired(final long eventId, final Date now) {
        return jdbcTemplate.query(
                "SELECT i.*" + JOIN_EVENT + "WHERE r.event_id = ? AND i.status = ? AND i.expires_at < ?",
                ROW_MAPPER, eventId, InvitationStatus.PENDING.name(), new Timestamp(now.getTime()));
    }

    @Override
    public void markAccepted(final long id) {
        jdbcTemplate.update("UPDATE waitlist_invitation SET status = ? WHERE id = ?",
                InvitationStatus.ACCEPTED.name(), id);
    }
}
