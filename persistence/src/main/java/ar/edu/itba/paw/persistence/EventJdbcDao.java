package ar.edu.itba.paw.persistence;

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
            "SELECT e.*, c.nombre AS categoria_nombre FROM evento e JOIN categoria c ON e.categoria_id = c.id";

    private static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> {
        final Event event = new Event();
        event.setId(rs.getLong("id"));
        event.setTitulo(rs.getString("titulo"));
        event.setDescripcion(rs.getString("descripcion"));
        event.setFechaEvento(rs.getTimestamp("fecha_evento"));
        event.setDireccion(rs.getString("direccion"));
        event.setBarrio(rs.getString("barrio"));
        event.setImagenUrl(rs.getString("imagen_url"));
        event.setPrecio(rs.getDouble("precio"));
        event.setCupoMaximo(rs.getObject("cupo_maximo", Integer.class));
        event.setCreadorEmail(rs.getString("creador_email"));
        event.setCategoriaId(rs.getLong("categoria_id"));
        event.setCategoriaNombre(rs.getString("categoria_nombre"));
        event.setFechaCreacion(rs.getTimestamp("fecha_creacion"));
        return event;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("evento")
                .usingGeneratedKeyColumns("id");
    }

    @Override
    public Event create(final Event event) {
        final Map<String, Object> args = new HashMap<>();
        args.put("titulo", event.getTitulo());
        args.put("descripcion", event.getDescripcion());
        args.put("fecha_evento", new java.sql.Timestamp(event.getFechaEvento().getTime()));
        args.put("direccion", event.getDireccion());
        args.put("barrio", event.getBarrio());
        args.put("imagen_url", event.getImagenUrl());
        args.put("precio", event.getPrecio());
        args.put("cupo_maximo", event.getCupoMaximo());
        args.put("creador_email", event.getCreadorEmail());
        args.put("categoria_id", event.getCategoriaId());

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
        return jdbcTemplate.query(SELECT_BASE + " ORDER BY e.fecha_evento", ROW_MAPPER);
    }

    @Override
    public List<Event> findByCategoria(final long categoriaId) {
        return jdbcTemplate.query(SELECT_BASE + " WHERE e.categoria_id = ? ORDER BY e.fecha_evento",
                ROW_MAPPER, categoriaId);
    }
}
