package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class VerificationTokenJdbcDaoTest {

    private static final String TOKEN = "abc-123";

    @Autowired
    private VerificationTokenDao tokenDao;

    @Autowired
    private UserDao userDao;

    private long userId;

    @Before
    public void setUp() {
        userId = userDao.create(new User("user@mail.com", "Ana")).getId();
    }

    private Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    // ---- create() + findByToken() ----

    @Test
    public void testCreateAndFindByTokenReturnTheSameToken() {
        final Date expiresAt = inOneHour();
        tokenDao.create(new VerificationToken(TOKEN, userId, TokenPurpose.VERIFY_EMAIL, expiresAt));

        final Optional<VerificationToken> found = tokenDao.findByToken(TOKEN);

        assertTrue(found.isPresent());
        assertEquals(userId, found.get().getUserId());
        assertEquals(TokenPurpose.VERIFY_EMAIL, found.get().getPurpose());
    }

    @Test
    public void testFindByTokenReturnsEmptyWhenItDoesNotExist() {
        final Optional<VerificationToken> found = tokenDao.findByToken(TOKEN);

        assertFalse(found.isPresent());
    }

    // ---- delete() ----

    @Test
    public void testDeleteRemovesTheToken() {
        tokenDao.create(new VerificationToken(TOKEN, userId, TokenPurpose.RESET_PASSWORD, inOneHour()));

        tokenDao.delete(TOKEN);

        assertFalse(tokenDao.findByToken(TOKEN).isPresent());
    }

    @Test
    public void testDeleteWhenItDoesNotExistDoesNothing() {
        tokenDao.delete(TOKEN);

        assertFalse(tokenDao.findByToken(TOKEN).isPresent());
    }

    // ---- deleteByUserAndPurpose() ----

    @Test
    public void testDeleteByUserAndPurposeOnlyRemovesThatPurpose() {
        tokenDao.create(new VerificationToken("verify-token", userId, TokenPurpose.VERIFY_EMAIL, inOneHour()));
        tokenDao.create(new VerificationToken("reset-token", userId, TokenPurpose.RESET_PASSWORD, inOneHour()));

        tokenDao.deleteByUserAndPurpose(userId, TokenPurpose.VERIFY_EMAIL);

        assertFalse(tokenDao.findByToken("verify-token").isPresent());
        assertTrue(tokenDao.findByToken("reset-token").isPresent());
    }
}
