package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class UserJdbcDaoTest {

    private static final String EMAIL = "user@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private UserDao userDao;

    // ---- create() + findById() ----

    @Test
    public void testCreateAssignsAGeneratedId() {
        final User created = userDao.create(new User(EMAIL, NAME));

        assertTrue(created.getId() > 0);
    }

    @Test
    public void testCreateAndFindByIdReturnTheSameUser() {
        final User created = userDao.create(new User(EMAIL, NAME));

        final Optional<User> found = userDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertEquals(EMAIL, found.get().getEmail());
        assertEquals(NAME, found.get().getName());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        final Optional<User> found = userDao.findById(-1);

        assertFalse(found.isPresent());
    }

    @Test
    public void testCreateWithNullNameSucceeds() {
        final User created = userDao.create(new User(EMAIL, null));

        final Optional<User> found = userDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertNull(found.get().getName());
    }

    // ---- findByEmail() ----

    @Test
    public void testCreateAndFindByEmailReturnTheSameUser() {
        final User created = userDao.create(new User(EMAIL, NAME));

        final Optional<User> found = userDao.findByEmail(EMAIL);

        assertTrue(found.isPresent());
        assertEquals(created.getId(), found.get().getId());
    }

    @Test
    public void testFindByEmailReturnsEmptyWhenTheEmailIsNotRegistered() {
        final Optional<User> found = userDao.findByEmail(EMAIL);

        assertFalse(found.isPresent());
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEmailThrowsDataAccessException() {
        // UNIQUE(email) en el schema: es la garantia de base detras de
        // "findOrCreate nunca pisa un user existente" (UserServiceImplTest, paso 5).
        userDao.create(new User(EMAIL, NAME));
        userDao.create(new User(EMAIL, "Otro Nombre"));
    }
}
