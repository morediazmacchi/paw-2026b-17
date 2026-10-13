package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class VerificationTokenJpaDaoTest {

    private static final String TOKEN = "abc-123";

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private DataSource ds;

    @Autowired
    private VerificationTokenDao tokenDao;

    private JdbcTemplate jdbcTemplate;
    private long userId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        userId = insertUser("user@mail.com");
    }

    private long insertUser(final String email) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, "Ana");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertToken(final String token, final long userId, final TokenPurpose purpose, final Date expiresAt) {
        jdbcTemplate.update(
                "INSERT INTO verification_token (token, user_id, purpose, expires_at) VALUES (?, ?, ?, ?)",
                token, userId, purpose.name(), expiresAt);
    }

    private Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    // ---- create() ----

    @Test
    public void testCreateInsertsTheTokenRow() {
        // Arrange
        final Date expiresAt = inOneHour();
        final VerificationToken toCreate = new VerificationToken(TOKEN, em.getReference(User.class, userId), TokenPurpose.VERIFY_EMAIL, expiresAt);

        // Act
        tokenDao.create(toCreate);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, purpose FROM verification_token WHERE token = ?", TOKEN);
        assertEquals(userId, ((Number) row.get("user_id")).longValue());
        assertEquals("VERIFY_EMAIL", row.get("purpose"));
    }

    // ---- findByToken() ----

    @Test
    public void testFindByTokenReturnsTheStoredToken() {
        // Arrange
        insertToken(TOKEN, userId, TokenPurpose.VERIFY_EMAIL, inOneHour());

        // Act
        final Optional<VerificationToken> found = tokenDao.findByToken(TOKEN);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(userId, found.get().getUserId());
        assertEquals(TokenPurpose.VERIFY_EMAIL, found.get().getPurpose());
    }

    @Test
    public void testFindByTokenReturnsEmptyWhenItDoesNotExist() {
        // Arrange: no se inserta ningun token.

        // Act
        final Optional<VerificationToken> found = tokenDao.findByToken(TOKEN);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheToken() {
        // Arrange
        insertToken(TOKEN, userId, TokenPurpose.RESET_PASSWORD, inOneHour());

        // Act
        tokenDao.delete(TOKEN);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "verification_token", "token = '" + TOKEN + "'"));
    }

    @Test
    public void testDeleteWhenItDoesNotExistDoesNothing() {
        // Arrange: no se inserta ningun token.

        // Act
        tokenDao.delete(TOKEN);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "verification_token", "token = '" + TOKEN + "'"));
    }

    // ---- deleteByUserAndPurpose() ----

    @Test
    public void testDeleteByUserAndPurposeOnlyRemovesThatPurpose() {
        // Arrange
        insertToken("verify-token", userId, TokenPurpose.VERIFY_EMAIL, inOneHour());
        insertToken("reset-token", userId, TokenPurpose.RESET_PASSWORD, inOneHour());

        // Act
        tokenDao.deleteByUserAndPurpose(userId, TokenPurpose.VERIFY_EMAIL);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "verification_token", "token = 'verify-token'"));
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "verification_token", "token = 'reset-token'"));
    }

    @Test
    public void testCreateReturnsTheTokenThatWasSaved() {
        // Arrange
        final VerificationToken toCreate = new VerificationToken(TOKEN, em.getReference(User.class, userId), TokenPurpose.VERIFY_EMAIL, inOneHour());

        // Act
        final VerificationToken result = tokenDao.create(toCreate);

        // Assert
        assertEquals(TOKEN, result.getToken());
    }

    @Test
    public void testDeleteReturnsTrueWhenTheTokenExisted() {
        // Arrange
        insertToken(TOKEN, userId, TokenPurpose.RESET_PASSWORD, inOneHour());

        // Act
        final boolean result = tokenDao.delete(TOKEN);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testDeleteReturnsFalseWhenThereIsNoSuchToken() {
        // Arrange
        // (no se inserta ningun token)

        // Act
        final boolean result = tokenDao.delete(TOKEN);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testDeleteByUserAndPurposeReturnsHowManyTokensWereDeleted() {
        // Arrange
        insertToken("verify-token", userId, TokenPurpose.VERIFY_EMAIL, inOneHour());
        insertToken("reset-token", userId, TokenPurpose.RESET_PASSWORD, inOneHour());

        // Act
        final int result = tokenDao.deleteByUserAndPurpose(userId, TokenPurpose.VERIFY_EMAIL);

        // Assert
        assertEquals(1, result);
    }
}
