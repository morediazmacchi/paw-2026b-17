package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Inscripcion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class InscripcionJdbcDao implements InscripcionDao {

    private static final RowMapper<Inscripcion> ROW_MAPPER = (rs, rowNum) -> {
        final Inscripcion inscripcion = new Inscripcion();
        inscripcion.setId(rs.getLong("id"));
        inscripcion.setEventoId(rs.getLong("evento_id"));
        inscripcion.setEmail(rs.getString("email"));
        inscripcion.setNombre(rs.getString("nombre"));
        inscripcion.setFechaInscripcion(rs.getTimestamp("fecha_inscripcion"));
        inscripcion.setEstado(rs.getString("estado"));
        return inscripcion;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public InscripcionJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("inscripcion")
                .usingGeneratedKeyColumns("id")
                .usingColumns("evento_id", "email", "nombre", "estado");
    }

    @Override
    public Inscripcion create(final Inscripcion inscripcion) {
        final Map<String, Object> args = new HashMap<>();
        args.put("evento_id", inscripcion.getEventoId());
        args.put("email", inscripcion.getEmail());
        args.put("nombre", inscripcion.getNombre());
        args.put("estado", inscripcion.getEstado());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        inscripcion.setId(id.longValue());
        return inscripcion;
    }

    @Override
    public Optional<Inscripcion> findByEventoAndEmail(final long eventoId, final String email) {
        return jdbcTemplate.query("SELECT * FROM inscripcion WHERE evento_id = ? AND email = ?",
                ROW_MAPPER, eventoId, email).stream().findFirst();
    }

    @Override
    public long countConfirmadas(final long eventoId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inscripcion WHERE evento_id = ? AND estado = ?",
                Long.class, eventoId, Inscripcion.ESTADO_CONFIRMADA);
        return count == null ? 0 : count;
    }
}
