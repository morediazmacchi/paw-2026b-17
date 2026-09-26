// Implementa el contrato InboxMessageDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
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

@Repository
public class InboxMessageJdbcDao implements InboxMessageDao {

    private static final RowMapper<InboxMessage> ROW_MAPPER = (rs, rowNum) -> {
        final InboxMessage message = new InboxMessage();
        message.setId(rs.getLong("id"));
        message.setRecipientEmail(rs.getString("recipient_email"));
        final Integer userId = rs.getObject("user_id", Integer.class);
        message.setUserId(userId == null ? null : userId.longValue());
        message.setType(InboxMessageType.valueOf(rs.getString("type")));
        final long eventId = rs.getLong("event_id");
        message.setEventId(rs.wasNull() ? null : eventId);
        message.setActorName(rs.getString("actor_name"));
        message.setBody(rs.getString("body"));
        message.setCreatedAt(rs.getTimestamp("created_at"));
        message.setReadAt(rs.getTimestamp("read_at"));
        return message;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public InboxMessageJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("inbox_message")
                .usingGeneratedKeyColumns("id")
                .usingColumns("recipient_email", "type", "event_id", "actor_name", "body", "user_id");
    }

    @Override
    public InboxMessage create(final InboxMessage message) {
        final Map<String, Object> args = new HashMap<>();
        args.put("recipient_email", message.getRecipientEmail());
        args.put("user_id", message.getUserId());
        args.put("type", message.getType().name());
        args.put("event_id", message.getEventId());
        args.put("actor_name", message.getActorName());
        args.put("body", message.getBody());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        message.setId(id.longValue());
        return message;
    }

    @Override
    public List<InboxMessage> findByRecipient(final String email, final int page, final int pageSize) {
        // id como desempate: dos avisos creados en la misma transaccion tienen el
        // mismo created_at, y sin esto podrian cambiar de pagina entre consultas.
        return jdbcTemplate.query(
                "SELECT * FROM inbox_message WHERE recipient_email = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, email, pageSize, (page - 1) * pageSize);
    }

    @Override
    public long countByRecipient(final String email) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE recipient_email = ?", Long.class, email);
        return count == null ? 0 : count;
    }

    @Override
    public long countUnread(final String email) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE recipient_email = ? AND read_at IS NULL",
                Long.class, email);
        return count == null ? 0 : count;
    }

    @Override
    public void markAllRead(final String email, final Date when) {
        jdbcTemplate.update(
                "UPDATE inbox_message SET read_at = ? WHERE recipient_email = ? AND read_at IS NULL",
                new Timestamp(when.getTime()), email);
    }

    @Override
    public List<InboxMessage> findByRecipientUserId(final long userId, final int page, final int pageSize) {
        return jdbcTemplate.query(
                "SELECT * FROM inbox_message WHERE user_id = ? "
                        + "ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, userId, pageSize, (page - 1) * pageSize);
    }

    @Override
    public long countByUserId(final long userId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public long countUnreadByUserId(final long userId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbox_message WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public void markAllReadByUserId(final long userId, final Date when) {
        jdbcTemplate.update(
                "UPDATE inbox_message SET read_at = ? WHERE user_id = ? AND read_at IS NULL",
                new Timestamp(when.getTime()), userId);
    }

    @Override
    public void linkUserId(final String email, final long userId) {
        jdbcTemplate.update(
                "UPDATE inbox_message SET user_id = ? WHERE recipient_email = ? AND user_id IS NULL",
                userId, email);
    }
}
