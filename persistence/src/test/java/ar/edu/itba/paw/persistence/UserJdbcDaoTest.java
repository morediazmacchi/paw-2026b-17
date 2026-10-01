package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Unidad 5 - Unit Testing: el objeto bajo test (userDao) NO se toca en el
// Arrange ni en el Assert, solo en el Act (una unica llamada). Arrange/Assert
// usan JDBC directo (jdbcTemplate/JdbcTestUtils), nunca otro metodo de
// userDao -- si no, un test de find() estaria probando tambien create().
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class UserJdbcDaoTest {

    private static final String EMAIL = "user@mail.com";
    private static final String NAME = "Ana";

    @Autowired
    private DataSource ds;

    @Autowired
    private UserDao userDao;

    private JdbcTemplate jdbcTemplate;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
    }

    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertRole(final long userId, final String role) {
        jdbcTemplate.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role);
    }

    // ---- create() ----

    @Test
    public void testCreatePersistsTheGivenEmailAndNameAndAssignsAnId() {
        // Arrange
        final User toCreate = new User(EMAIL, NAME);

        // Act
        final User created = userDao.create(toCreate);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, name FROM users WHERE id = ?", created.getId());
        assertEquals(EMAIL, row.get("email"));
        assertEquals(NAME, row.get("name"));
    }

    @Test
    public void testCreateWithNullNameSucceeds() {
        // Arrange
        final User toCreate = new User(EMAIL, null);

        // Act
        final User created = userDao.create(toCreate);

        // Assert
        final String name = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, created.getId());
        assertNull(name);
    }

    @Test
    public void testCreateDefaultsVerifiedToTrue() {
        // Arrange
        final User toCreate = new User(EMAIL, NAME);

        // Act
        final User created = userDao.create(toCreate);

        // Assert
        // DEFAULT TRUE es del backfill de cuentas viejas (ver schema.sql);
        // create() no lo toca, asi que cae en ese default tambien.
        final Boolean verified = jdbcTemplate.queryForObject(
                "SELECT verified FROM users WHERE id = ?", Boolean.class, created.getId());
        assertTrue(verified);
    }

    @Test
    public void testCreateDefaultsSuspendedToFalse() {
        // Arrange
        final User toCreate = new User(EMAIL, NAME);

        // Act
        final User created = userDao.create(toCreate);

        // Assert
        final Boolean suspended = jdbcTemplate.queryForObject(
                "SELECT suspended FROM users WHERE id = ?", Boolean.class, created.getId());
        assertFalse(suspended);
    }

    @Test
    public void testCreateLeavesLocaleNull() {
        // Arrange
        final User toCreate = new User(EMAIL, NAME);

        // Act
        final User created = userDao.create(toCreate);

        // Assert
        final String locale = jdbcTemplate.queryForObject(
                "SELECT preferred_locale FROM users WHERE id = ?", String.class, created.getId());
        assertNull(locale);
    }

    @Test(expected = DataAccessException.class)
    public void testCreateWithDuplicateEmailThrowsDataAccessException() {
        // Arrange
        insertUser(EMAIL, NAME);

        // Act
        userDao.create(new User(EMAIL, "Otro Nombre"));

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- findById() ----

    @Test
    public void testFindByIdReturnsTheStoredUser() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final Optional<User> found = userDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(EMAIL, found.get().getEmail());
        assertEquals(NAME, found.get().getName());
    }

    @Test
    public void testFindByIdReturnsEmptyWhenTheUserDoesNotExist() {
        // Arrange: nada que insertar, el id -1 nunca existe.

        // Act
        final Optional<User> found = userDao.findById(-1);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- findByEmail() ----

    @Test
    public void testFindByEmailReturnsTheStoredUser() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final Optional<User> found = userDao.findByEmail(EMAIL);

        // Assert
        assertTrue(found.isPresent());
        assertEquals(id, found.get().getId());
    }

    @Test
    public void testFindByEmailReturnsEmptyWhenTheEmailIsNotRegistered() {
        // Arrange: no se inserta ningun usuario con ese email.

        // Act
        final Optional<User> found = userDao.findByEmail(EMAIL);

        // Assert
        assertFalse(found.isPresent());
    }

    // ---- roles (LEFT JOIN en findById/findByEmail) ----

    @Test
    public void testFindByIdReturnsRolesWhenTheUserHasThem() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");
        insertRole(id, "ROLE_ADMIN");

        // Act
        final Optional<User> found = userDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        final Set<String> roles = found.get().getRoles();
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
    }

    @Test
    public void testFindByEmailReturnsEmptySetWhenTheUserHasNoRoles() {
        // Arrange
        // El LEFT JOIN es lo que hace que este usuario aparezca igual (con
        // set vacio) en vez de desaparecer del resultado por no tener fila
        // en user_role.
        insertUser(EMAIL, NAME);

        // Act
        final Optional<User> found = userDao.findByEmail(EMAIL);

        // Assert
        assertTrue(found.isPresent());
        assertTrue(found.get().getRoles().isEmpty());
    }

    // ---- addRole() ----

    @Test
    public void testAddRoleInsertsTheRoleRow() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.addRole(id, "ROLE_USER");

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
    }

    @Test
    public void testAddRoleTwiceIsIdempotent() {
        // Arrange
        // No hay ON CONFLICT (no es portable a HSQLDB): addRole() depende de
        // que el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final long id = insertUser(EMAIL, NAME);

        // Act
        // Las DOS llamadas son el Act en si (es lo que hace idempotente a
        // "idempotente"), no una contaminando a la otra.
        userDao.addRole(id, "ROLE_USER");
        userDao.addRole(id, "ROLE_USER");

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
    }

    // ---- removeRole() ----

    @Test
    public void testRemoveRoleTakesJustThatRoleAway() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");
        insertRole(id, "ROLE_ADMIN");

        // Act
        userDao.removeRole(id, "ROLE_ADMIN");

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_ADMIN'"));
    }

    @Test
    public void testRemoveRoleOnAUserWithoutThatRoleIsANoop() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");

        // Act
        userDao.removeRole(id, "ROLE_ADMIN");

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
    }

    // ---- findRoles() ----

    @Test
    public void testFindRolesReturnsAllRolesOfThatUser() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");
        insertRole(id, "ROLE_ADMIN");

        // Act
        final Set<String> roles = userDao.findRoles(id);

        // Assert
        assertEquals(2, roles.size());
        assertTrue(roles.contains("ROLE_USER"));
        assertTrue(roles.contains("ROLE_ADMIN"));
    }

    // ---- setPassword() ----

    @Test
    public void testSetPasswordOnAUserWithoutOneSetsIt() {
        // Arrange
        // Caso "usuario fantasma" (creado por publicar/inscribirse sin
        // login): el insert de arriba ya lo deja con password NULL.
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.setPassword(id, "hashed-password");

        // Assert
        final String password = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE id = ?", String.class, id);
        assertEquals("hashed-password", password);
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileChangesNameAndEmail() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.updateProfile(id, "Nuevo Nombre", "nuevo@mail.com");

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, email FROM users WHERE id = ?", id);
        assertEquals("Nuevo Nombre", row.get("name"));
        assertEquals("nuevo@mail.com", row.get("email"));
    }

    // ---- setVerified() ----

    @Test
    public void testSetVerifiedFalseMarksTheUserUnverified() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.setVerified(id, false);

        // Assert
        final Boolean verified = jdbcTemplate.queryForObject(
                "SELECT verified FROM users WHERE id = ?", Boolean.class, id);
        assertFalse(verified);
    }

    @Test
    public void testSetVerifiedTrueMarksTheUserVerified() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET verified = false WHERE id = ?", id);

        // Act
        userDao.setVerified(id, true);

        // Assert
        final Boolean verified = jdbcTemplate.queryForObject(
                "SELECT verified FROM users WHERE id = ?", Boolean.class, id);
        assertTrue(verified);
    }

    // ---- setLocale() ----

    @Test
    public void testSetLocaleUpdatesThePreferredLocaleColumn() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.setLocale(id, "en");

        // Assert
        final String locale = jdbcTemplate.queryForObject(
                "SELECT preferred_locale FROM users WHERE id = ?", String.class, id);
        assertEquals("en", locale);
    }

    // ---- setSuspended() ----

    @Test
    public void testSetSuspendedTrueMarksTheUserSuspended() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.setSuspended(id, true);

        // Assert
        final Boolean suspended = jdbcTemplate.queryForObject(
                "SELECT suspended FROM users WHERE id = ?", Boolean.class, id);
        assertTrue(suspended);
    }

    @Test
    public void testSetSuspendedFalseMarksTheUserNotSuspended() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET suspended = true WHERE id = ?", id);

        // Act
        userDao.setSuspended(id, false);

        // Assert
        final Boolean suspended = jdbcTemplate.queryForObject(
                "SELECT suspended FROM users WHERE id = ?", Boolean.class, id);
        assertFalse(suspended);
    }

    // ---- findAll() ----

    @Test
    public void testFindAllReturnsEveryUserWithTheirOwnRoles() {
        // Arrange
        final long first = insertUser(EMAIL, NAME);
        insertRole(first, "ROLE_USER");
        final long second = insertUser("otro@mail.com", "Beto");
        insertRole(second, "ROLE_USER");
        insertRole(second, "ROLE_ADMIN");

        // Act
        final List<User> all = userDao.findAll();

        // Assert
        assertEquals(2, all.size());
        final User foundFirst = all.stream().filter(u -> u.getId() == first).findFirst().orElseThrow(AssertionError::new);
        final User foundSecond = all.stream().filter(u -> u.getId() == second).findFirst().orElseThrow(AssertionError::new);
        assertEquals(1, foundFirst.getRoles().size());
        assertEquals(2, foundSecond.getRoles().size());
    }

    @Test
    public void testFindAllReturnsEmptyListWhenThereAreNoUsers() {
        // Arrange: no se inserta ningun usuario.

        // Act
        final List<User> all = userDao.findAll();

        // Assert
        assertTrue(all.isEmpty());
    }
}
