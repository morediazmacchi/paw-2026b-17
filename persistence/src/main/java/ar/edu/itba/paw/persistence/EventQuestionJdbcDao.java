// Implementa el contrato EventQuestionDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventQuestion;
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
public class EventQuestionJdbcDao implements EventQuestionDao {

    // El nombre y el mail de quien pregunto salen del JOIN: no se copian a la tabla,
    // asi si cambia su nombre en el perfil se ve el nuevo.
    private static final String SELECT_BASE =
            "SELECT q.*, u.name AS asker_name, u.email AS asker_email "
                    + "FROM event_question q JOIN users u ON u.id = q.user_id";

    private static final RowMapper<EventQuestion> ROW_MAPPER = (rs, rowNum) -> {
        final EventQuestion question = new EventQuestion();
        question.setId(rs.getLong("id"));
        question.setEventId(rs.getLong("event_id"));
        question.setUserId(rs.getLong("user_id"));
        question.setText(rs.getString("question_text"));
        question.setAnswer(rs.getString("answer_text"));
        question.setCreatedAt(rs.getTimestamp("created_at"));
        question.setAnsweredAt(rs.getTimestamp("answered_at"));
        question.setAskerName(rs.getString("asker_name"));
        question.setAskerEmail(rs.getString("asker_email"));
        return question;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventQuestionJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("event_question")
                .usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "user_id", "question_text");
    }

    @Override
    public EventQuestion create(final EventQuestion question) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", question.getEventId());
        args.put("user_id", question.getUserId());
        args.put("question_text", question.getText());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        question.setId(id.longValue());
        return question;
    }

    @Override
    public Optional<EventQuestion> findById(final long id) {
        return jdbcTemplate.query(SELECT_BASE + " WHERE q.id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    @Override
    public List<EventQuestion> findByEvent(final long eventId, final int page, final int pageSize) {
        // id como desempate, igual que en la bandeja.
        return jdbcTemplate.query(
                SELECT_BASE + " WHERE q.event_id = ? ORDER BY q.created_at DESC, q.id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, eventId, pageSize, (page - 1) * pageSize);
    }

    @Override
    public long countByEvent(final long eventId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_question WHERE event_id = ?", Long.class, eventId);
        return count == null ? 0 : count;
    }

    @Override
    public boolean answer(final long id, final String answer, final Date when) {
        return jdbcTemplate.update(
                "UPDATE event_question SET answer_text = ?, answered_at = ? WHERE id = ? AND answer_text IS NULL",
                answer, new Timestamp(when.getTime()), id) == 1;
    }

    @Override
    public boolean delete(final long id) {
        return jdbcTemplate.update("DELETE FROM event_question WHERE id = ?", id) == 1;
    }
}
