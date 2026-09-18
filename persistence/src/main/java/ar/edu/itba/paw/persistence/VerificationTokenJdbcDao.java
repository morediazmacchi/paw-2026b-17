// Implementa el contrato VerificationTokenDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.VerificationToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Optional;

@Repository
public class VerificationTokenJdbcDao implements VerificationTokenDao {

    private static final RowMapper<VerificationToken> ROW_MAPPER = (rs, rowNum) -> new VerificationToken(
            rs.getString("token"),
            rs.getLong("user_id"),
            TokenPurpose.valueOf(rs.getString("purpose")),
            rs.getTimestamp("expires_at"));

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public VerificationTokenJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
    }

    @Override
    public void create(final VerificationToken token) {
        jdbcTemplate.update(
                "INSERT INTO verification_token (token, user_id, purpose, expires_at) VALUES (?, ?, ?, ?)",
                token.getToken(), token.getUserId(), token.getPurpose().name(), token.getExpiresAt());
    }

    @Override
    public Optional<VerificationToken> findByToken(final String token) {
        return jdbcTemplate.query("SELECT * FROM verification_token WHERE token = ?", ROW_MAPPER, token)
                .stream().findFirst();
    }

    @Override
    public void delete(final String token) {
        jdbcTemplate.update("DELETE FROM verification_token WHERE token = ?", token);
    }

    @Override
    public void deleteByUserAndPurpose(final long userId, final TokenPurpose purpose) {
        jdbcTemplate.update("DELETE FROM verification_token WHERE user_id = ? AND purpose = ?",
                userId, purpose.name());
    }
}
