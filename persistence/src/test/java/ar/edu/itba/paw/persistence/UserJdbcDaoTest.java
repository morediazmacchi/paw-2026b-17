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
import java.util.Set;

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

    // ---- roles (LEFT JOIN en findById/findByEmail) ----

    @Test
    public void testFindByIdReturnsRolesWhenTheUserHasThem() {
        final User created = userDao.create(new User(EMAIL, NAME));
        userDao.addRole(created.getId(), "ROLE_USER");
        userDao.addRole(created.getId(), "ROLE_ADMIN");

        final Optional<User> found = userDao.findById(created.getId());

        assertTrue(found.isPresent());
        final Set<String> roles = found.get().getRoles();
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
    }

    @Test
    public void testAddRoleTwiceIsIdempotent() {
        // No hay ON CONFLICT (no es portable a HSQLDB): addRole() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final User created = userDao.create(new User(EMAIL, NAME));

        userDao.addRole(created.getId(), "ROLE_USER");
        userDao.addRole(created.getId(), "ROLE_USER");

        assertEquals(1, userDao.findRoles(created.getId()).size());
    }

    @Test
    public void testFindByEmailReturnsEmptySetWhenTheUserHasNoRoles() {
        // El LEFT JOIN es lo que hace que este usuario aparezca igual (con
        // set vacio) en vez de desaparecer del resultado por no tener fila
        // en user_role: son los tres usuarios creados antes de que existiera login.
        userDao.create(new User(EMAIL, NAME));

        final Optional<User> found = userDao.findByEmail(EMAIL);

        assertTrue(found.isPresent());
        assertTrue(found.get().getRoles().isEmpty());
    }

    // ---- setPassword() ----

    @Test
    public void testSetPasswordOnAUserWithoutOneSetsIt() {
        // Este es el caso "usuario fantasma" (creado por publicar/inscribirse sin
        // login) que despues se registra de verdad: hasta aca password es null.
        final User created = userDao.create(new User(EMAIL, NAME));

        userDao.setPassword(created.getId(), "hashed-password");

        final Optional<User> found = userDao.findById(created.getId());
        assertTrue(found.isPresent());
        assertEquals("hashed-password", found.get().getPassword());
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileChangesNameAndEmail() {
        final User created = userDao.create(new User(EMAIL, NAME));

        userDao.updateProfile(created.getId(), "Nuevo Nombre", "nuevo@mail.com");

        final Optional<User> found = userDao.findById(created.getId());
        assertTrue(found.isPresent());
        assertEquals("Nuevo Nombre", found.get().getName());
        assertEquals("nuevo@mail.com", found.get().getEmail());
    }

    // ---- verified ----

    @Test
    public void testNewUserIsVerifiedByDefault() {
        // El DEFAULT TRUE de la columna es para las cuentas viejas (backfill):
        // create() no manda verified, asi que tambien cae en ese default. Es
        // UserServiceImpl.register() quien lo pisa a false a mano en las cuentas
        // nuevas de verdad.
        final User created = userDao.create(new User(EMAIL, NAME));

        final Optional<User> found = userDao.findById(created.getId());

        assertTrue(found.isPresent());
        assertTrue(found.get().isVerified());
    }

    @Test
    public void testSetVerifiedFalseThenTrue() {
        final User created = userDao.create(new User(EMAIL, NAME));

        userDao.setVerified(created.getId(), false);
        assertFalse(userDao.findById(created.getId()).get().isVerified());

        userDao.setVerified(created.getId(), true);
        assertTrue(userDao.findById(created.getId()).get().isVerified());
    }

    // ---- locale ----

    @Test
    public void testNewUserHasNoLocaleByDefault() {
        // Nadie eligio idioma todavia: la pagina usa la cookie/navegador y los
        // mails el default (ver NotificationServiceImpl), no esta columna.
        final User created = userDao.create(new User(EMAIL, NAME));

        assertNull(userDao.findById(created.getId()).get().getLocale());
    }

    @Test
    public void testSetLocaleIsVisibleByIdAndByEmail() {
        final User created = userDao.create(new User(EMAIL, NAME));

        userDao.setLocale(created.getId(), "en");

        assertEquals("en", userDao.findById(created.getId()).get().getLocale());
        assertEquals("en", userDao.findByEmail(EMAIL).get().getLocale());
    }
}
