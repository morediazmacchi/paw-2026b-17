// Implementa el contrato ReviewDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Review;
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
public class ReviewJdbcDao implements ReviewDao {

    private static final RowMapper<Review> ROW_MAPPER = (rs, rowNum) -> {
        final Review review = new Review();
        review.setId(rs.getLong("id"));
        review.setEventId(rs.getLong("event_id"));
        review.setUserId(rs.getLong("user_id"));
        review.setRating(rs.getInt("rating"));
        review.setComment(rs.getString("comment"));
        review.setCreatedAt(rs.getTimestamp("created_at"));
        return review;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public ReviewJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("review")
                .usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "user_id", "rating", "comment");
    }

    @Override
    public Review create(final Review review) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", review.getEventId());
        args.put("user_id", review.getUserId());
        args.put("rating", review.getRating());
        args.put("comment", review.getComment());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        review.setId(id.longValue());
        return review;
    }

    @Override
    public Optional<Review> findByEventAndUser(final long eventId, final long userId) {
        return jdbcTemplate.query("SELECT * FROM review WHERE event_id = ? AND user_id = ?",
                ROW_MAPPER, eventId, userId).stream().findFirst();
    }

    @Override
    public List<Review> findByCreatorEmail(final String creatorEmail) {
        return jdbcTemplate.query(
                "SELECT r.* FROM review r JOIN event e ON e.id = r.event_id "
                        + "WHERE e.creator_email = ? ORDER BY r.created_at DESC",
                ROW_MAPPER, creatorEmail);
    }
}
