// Implementa el contrato FavoriteDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;

@Repository
public class FavoriteJdbcDao implements FavoriteDao {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FavoriteJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
    }

    @Override
    public void add(final long userId, final long eventId) {
        // Mismo patron que UserJdbcDao.addRole: el PRIMARY KEY compuesto ya
        // garantiza que no se duplique, dejamos pasar la violacion en vez de
        // checkear antes (y "ON CONFLICT" no es portable a HSQLDB).
        try {
            jdbcTemplate.update("INSERT INTO favorite (user_id, event_id) VALUES (?, ?)", userId, eventId);
        } catch (final DataIntegrityViolationException e) {
            // Ya era favorito: idempotente, no hace falta nada mas.
        }
    }

    @Override
    public void remove(final long userId, final long eventId) {
        jdbcTemplate.update("DELETE FROM favorite WHERE user_id = ? AND event_id = ?", userId, eventId);
    }

    @Override
    public boolean isFavorite(final long userId, final long eventId) {
        final Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM favorite WHERE user_id = ? AND event_id = ?",
                Integer.class, userId, eventId);
        return count != null && count > 0;
    }

    @Override
    public List<Long> findEventIdsByUser(final long userId) {
        return jdbcTemplate.queryForList(
                "SELECT event_id FROM favorite WHERE user_id = ? ORDER BY created_at DESC",
                Long.class, userId);
    }
}
