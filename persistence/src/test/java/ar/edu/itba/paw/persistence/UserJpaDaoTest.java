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

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
public class UserJpaDaoTest {

    private static final String EMAIL = "user@mail.com";
    private static final String NAME = "Ana";

    @PersistenceContext
    private EntityManager em;

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
        assertEquals(id, found.get().getId().longValue());
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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
    }

    @Test
    public void testAddRoleWhenTheUserAlreadyHasItIsIdempotent() {
        // Arrange
        // No hay ON CONFLICT (no es portable a HSQLDB): addRole() depende de
        // que el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");

        // Act
        userDao.addRole(id, "ROLE_USER");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ROLE_USER'"));
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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        final String password = jdbcTemplate.queryForObject(
                "SELECT password FROM users WHERE id = ?", String.class, id);
        assertEquals("hashed-password", password);
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileChangesTheName() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.updateProfile(id, "Nuevo Nombre");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, email FROM users WHERE id = ?", id);
        assertEquals("Nuevo Nombre", row.get("name"));
        assertEquals(EMAIL, row.get("email"));
    }

    // ---- setVerified() ----

    @Test
    public void testSetVerifiedFalseMarksTheUserUnverified() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        userDao.setVerified(id, false);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

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
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        final Boolean suspended = jdbcTemplate.queryForObject(
                "SELECT suspended FROM users WHERE id = ?", Boolean.class, id);
        assertFalse(suspended);
    }

    // ---- countAll() / findPage() ----

    @Test
    public void testCountAllCountsEveryUserNotEveryRole() {
        // Arrange
        final long first = insertUser(EMAIL, NAME);
        insertRole(first, "ROLE_USER");
        insertRole(first, "ROLE_ADMIN");
        insertUser("otro@mail.com", "Beto");

        // Act
        final long count = userDao.countAll();

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testFindPageReturnsTheRequestedSliceOrderedById() {
        // Arrange
        final long first = insertUser(EMAIL, NAME);
        final long second = insertUser("otro@mail.com", "Beto");
        final long third = insertUser("tercero@mail.com", "Carla");

        // Act
        final List<User> page = userDao.findPage(2, 1);

        // Assert
        assertEquals(2, page.size());
        assertEquals(second, page.get(0).getId().longValue());
        assertEquals(third, page.get(1).getId().longValue());
        assertTrue(page.stream().noneMatch(u -> u.getId() == first));
    }

    @Test
    public void testFindPageCountsAUserWithTwoRolesAsOneRowOfTheLimit() {
        // Arrange: el primero tiene dos roles (dos filas en el JOIN); el limite es de usuarios, no de filas.
        final long first = insertUser(EMAIL, NAME);
        insertRole(first, "ROLE_USER");
        insertRole(first, "ROLE_ADMIN");
        final long second = insertUser("otro@mail.com", "Beto");

        // Act
        final List<User> page = userDao.findPage(2, 0);

        // Assert
        assertEquals(2, page.size());
        assertEquals(first, page.get(0).getId().longValue());
        assertEquals(2, page.get(0).getRoles().size());
        assertEquals(second, page.get(1).getId().longValue());
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

    // ---- findByIds() ----

    @Test
    public void testFindByIdsReturnsOnlyTheRequestedUsers() {
        // Arrange
        final long first = insertUser("uno@mail.com", "Uno");
        insertUser("dos@mail.com", "Dos");
        final long third = insertUser("tres@mail.com", "Tres");

        // Act
        final List<User> result = userDao.findByIds(Arrays.asList(first, third));

        // Assert
        assertEquals(2, result.size());
        assertEquals("uno@mail.com", result.get(0).getEmail());
        assertEquals("tres@mail.com", result.get(1).getEmail());
    }

    @Test
    public void testFindByIdsLoadsTheRolesOfEachUser() {
        // Arrange
        final long id = insertUser("uno@mail.com", "Uno");
        insertRole(id, "ROLE_USER");
        insertRole(id, "ROLE_ADMIN");

        // Act
        final List<User> result = userDao.findByIds(Collections.singletonList(id));

        // Assert
        assertEquals(new HashSet<>(Arrays.asList("ROLE_USER", "ROLE_ADMIN")), result.get(0).getRoles());
    }

    @Test
    public void testFindByIdsWithAnEmptyListReturnsAnEmptyList() {
        // Arrange
        insertUser("uno@mail.com", "Uno");

        // Act
        final List<User> result = userDao.findByIds(Collections.emptyList());

        // Assert
        assertTrue(result.isEmpty());
    }

    @Test
    public void testAddRoleReturnsTrueWhenTheRoleWasAdded() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.addRole(id, "ROLE_USER");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testAddRoleReturnsFalseWhenTheUserAlreadyHadIt() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_USER");

        // Act
        final boolean result = userDao.addRole(id, "ROLE_USER");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testRemoveRoleReturnsTrueWhenTheRoleWasRemoved() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "ROLE_ADMIN");

        // Act
        final boolean result = userDao.removeRole(id, "ROLE_ADMIN");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testRemoveRoleReturnsFalseWhenTheUserDidNotHaveIt() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.removeRole(id, "ROLE_ADMIN");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSetPasswordReturnsTrueWhenTheUserExists() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setPassword(id, "hashed-password");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetPasswordReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        // (no hay ningun usuario guardado)

        // Act
        final boolean result = userDao.setPassword(999_999L, "hashed-password");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testUpdateProfileReturnsTrueWhenTheUserExists() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateProfile(id, "Nuevo Nombre");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testUpdateProfileReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        // (no hay ningun usuario guardado)

        // Act
        final boolean result = userDao.updateProfile(999_999L, "Nuevo Nombre");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSetVerifiedReturnsTrueWhenTheUserExists() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setVerified(id, true);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetVerifiedReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        // (no hay ningun usuario guardado)

        // Act
        final boolean result = userDao.setVerified(999_999L, true);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSetLocaleReturnsTrueWhenTheUserExists() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setLocale(id, "en");

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetLocaleReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        // (no hay ningun usuario guardado)

        // Act
        final boolean result = userDao.setLocale(999_999L, "en");

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSetSuspendedReturnsTrueWhenTheUserExists() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setSuspended(id, true);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSetSuspendedReturnsFalseWhenThereIsNoSuchUser() {
        // Arrange
        // (no hay ningun usuario guardado)

        // Act
        final boolean result = userDao.setSuspended(999_999L, true);

        // Assert
        assertFalse(result);
    }
}
