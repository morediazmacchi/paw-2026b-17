// Implementa el contrato ReviewDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Review;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
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
    public List<Review> findByEventsAndUser(final List<Long> eventIds, final long userId) {
        if (eventIds.isEmpty()) {
            return Collections.emptyList();
        }
        final List<Object> args = new ArrayList<>(eventIds);
        args.add(userId);
        return jdbcTemplate.query(
                "SELECT * FROM review WHERE event_id IN (" + SqlPlaceholders.of(eventIds.size()) + ") AND user_id = ?",
                ROW_MAPPER, args.toArray());
    }

    @Override
    public List<Review> findByCreatorId(final long creatorId) {
        return jdbcTemplate.query(
                "SELECT r.* FROM review r JOIN event e ON e.id = r.event_id "
                        + "WHERE e.creator_id = ? ORDER BY r.created_at DESC",
                ROW_MAPPER, creatorId);
    }

    @Override
    public double findAverageRatingByCreatorId(final long creatorId) {
        // Sin reseñas, AVG da NULL: COALESCE lo convierte en 0. El CAST es porque
        // rating es INTEGER y HSQLDB (la base de los tests) hace AVG entero:
        // 5 y 4 darian 4 en vez de 4.5. Postgres no lo necesita, pero no le molesta.
        final Double average = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(CAST(r.rating AS DOUBLE PRECISION)), 0) FROM review r JOIN event e ON e.id = r.event_id "
                        + "WHERE e.creator_id = ?",
                Double.class, creatorId);
        return average == null ? 0 : average;
    }
}
