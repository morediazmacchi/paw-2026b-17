// Implementa el contrato EventDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
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
public class EventJdbcDao implements EventDao {

    private static final String SELECT_BASE =
            "SELECT e.*, c.name AS category_name FROM event e JOIN category c ON e.category_id = c.id";

    private static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> {
        final Event event = new Event();
        event.setId(rs.getLong("id"));
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setEventDate(rs.getTimestamp("event_date"));
        event.setAddress(rs.getString("address"));
        final String neighborhoodName = rs.getString("neighborhood");
        event.setNeighborhood(neighborhoodName == null ? null : Barrio.valueOf(neighborhoodName));
        // image_id es INTEGER (int4) en la base y Long en Java. El driver de Postgres
        // no convierte int4 -> Long directo, hay que pasar por Integer.
        final Integer rawImageId = rs.getObject("image_id", Integer.class);
        event.setImageId(rawImageId == null ? null : rawImageId.longValue());
        event.setPrice(rs.getDouble("price"));
        event.setMaxCapacity(rs.getObject("max_capacity", Integer.class));
        event.setCreatorEmail(rs.getString("creator_email"));
        event.setCategoryId(rs.getLong("category_id"));
        event.setCategoryName(rs.getString("category_name"));
        event.setCreatedAt(rs.getTimestamp("created_at"));
        return event;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("event")
                .usingGeneratedKeyColumns("id")
                .usingColumns("title", "description", "event_date", "address", "neighborhood",
                        "image_id", "price", "max_capacity", "creator_email", "category_id");
    }

    @Override
    public Event create(final Event event) {
        final Map<String, Object> args = new HashMap<>();
        args.put("title", event.getTitle());
        args.put("description", event.getDescription());
        args.put("event_date", new java.sql.Timestamp(event.getEventDate().getTime()));
        args.put("address", event.getAddress());
        args.put("neighborhood", event.getNeighborhood() == null ? null : event.getNeighborhood().name());
        args.put("image_id", event.getImageId());
        args.put("price", event.getPrice());
        args.put("max_capacity", event.getMaxCapacity());
        args.put("creator_email", event.getCreatorEmail());
        args.put("category_id", event.getCategoryId());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        event.setId(id.longValue());
        return event;
    }

    @Override
    public Optional<Event> findById(final long id) {
        return jdbcTemplate.query(SELECT_BASE + " WHERE e.id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<Event> findAll() {
        return jdbcTemplate.query(SELECT_BASE + " ORDER BY e.event_date", ROW_MAPPER);
    }

    @Override
    public List<Event> findByCategory(final long categoryId) {
        return jdbcTemplate.query(SELECT_BASE + " WHERE e.category_id = ? ORDER BY e.event_date",
                ROW_MAPPER, categoryId);
    }
}
