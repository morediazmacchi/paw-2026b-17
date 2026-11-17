package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.model.Role;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.UserAccess;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
        // Act
        final User created = userDao.create(EMAIL, NAME);

        // Assert
        assertTrue(created.getId() > 0);
        final Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT email, name FROM users WHERE id = ?", created.getId());
        assertEquals(EMAIL, row.get("email"));
        assertEquals(NAME, row.get("name"));
    }

    @Test
    public void testCreateWithNullNameSucceeds() {
        // Act
        final User created = userDao.create(EMAIL, null);

        // Assert
        final String name = jdbcTemplate.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, created.getId());
        assertNull(name);
    }

    @Test
    public void testCreateDefaultsVerifiedToTrue() {
        // Act
        final User created = userDao.create(EMAIL, NAME);

        // Assert
        // DEFAULT TRUE es del backfill de cuentas viejas (ver schema.sql);
        // create() no lo toca, asi que cae en ese default tambien.
        final Boolean verified = jdbcTemplate.queryForObject(
                "SELECT verified FROM users WHERE id = ?", Boolean.class, created.getId());
        assertTrue(verified);
    }

    @Test
    public void testCreateDefaultsSuspendedToFalse() {
        // Act
        final User created = userDao.create(EMAIL, NAME);

        // Assert
        final Boolean suspended = jdbcTemplate.queryForObject(
                "SELECT suspended FROM users WHERE id = ?", Boolean.class, created.getId());
        assertFalse(suspended);
    }

    @Test
    public void testCreateLeavesLocaleNull() {
        // Act
        final User created = userDao.create(EMAIL, NAME);

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
        userDao.create(EMAIL, "Otro Nombre");

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
        insertRole(id, "USER");
        insertRole(id, "ADMIN");

        // Act
        final Optional<User> found = userDao.findById(id);

        // Assert
        assertTrue(found.isPresent());
        final Set<Role> roles = found.get().getRoles();
        assertEquals(2, roles.size());
        assertTrue(roles.contains(Role.USER));
        assertTrue(roles.contains(Role.ADMIN));
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
    public void testAddRoleInsertsTheRoleRowAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.addRole(id, Role.USER);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'USER'"));
    }

    @Test
    public void testAddRoleWhenTheUserAlreadyHasItKeepsOneRowAndReturnsFalse() {
        // Arrange
        // No hay ON CONFLICT (no es portable a HSQLDB): addRole() depende de
        // que el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "USER");

        // Act
        final boolean result = userDao.addRole(id, Role.USER);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'USER'"));
    }

    // ---- removeRole() ----

    @Test
    public void testRemoveRoleTakesJustThatRoleAwayAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "USER");
        insertRole(id, "ADMIN");

        // Act
        final boolean result = userDao.removeRole(id, Role.ADMIN);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'USER'"));
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'ADMIN'"));
    }

    @Test
    public void testRemoveRoleOnAUserWithoutThatRoleChangesNothingAndReturnsFalse() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        insertRole(id, "USER");

        // Act
        final boolean result = userDao.removeRole(id, Role.ADMIN);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "user_role", "user_id = " + id + " AND role = 'USER'"));
    }

    // ---- setPassword() ----

    @Test
    public void testSetPasswordOnAUserWithoutOneSetsItAndReturnsTrue() {
        // Arrange
        // Caso "usuario fantasma" (creado por publicar/inscribirse sin
        // login): el insert de arriba ya lo deja con password NULL.
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setPassword(id, "hashed-password");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND password = 'hashed-password'"));
    }

    // ---- updateProfile() ----

    @Test
    public void testUpdateProfileChangesTheNameAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateProfile(id, "Nuevo Nombre");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND name = 'Nuevo Nombre' AND email = '" + EMAIL + "'"));
    }

    // ---- updateBio() ----

    @Test
    public void testUpdateBioWritesTheBiographyAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateBio(id, "Organizo ferias de barrio");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND bio = 'Organizo ferias de barrio'"));
    }

    @Test
    public void testUpdateBioWithNullClearsTheBiography() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET bio = 'algo' WHERE id = ?", id);

        // Act
        final boolean result = userDao.updateBio(id, null);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "id = " + id + " AND bio IS NULL"));
    }

    @Test
    public void testUpdateBioOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar sin biografia.
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateBio(999_999L, "Hola");
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "bio = 'Hola'"));
    }

    // ---- setVerified() ----

    @Test
    public void testSetVerifiedFalseMarksTheUserUnverifiedAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setVerified(id, false);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "id = " + id + " AND verified = false"));
    }

    @Test
    public void testSetVerifiedTrueMarksTheUserVerifiedAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET verified = false WHERE id = ?", id);

        // Act
        final boolean result = userDao.setVerified(id, true);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "id = " + id + " AND verified = true"));
    }

    // ---- setLocale() ----

    @Test
    public void testSetLocaleUpdatesThePreferredLocaleColumnAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setLocale(id, "en");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND preferred_locale = 'en'"));
    }

    // ---- setSuspended() ----

    @Test
    public void testSetSuspendedTrueMarksTheUserSuspendedAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setSuspended(id, true);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "id = " + id + " AND suspended = true"));
    }

    @Test
    public void testSetSuspendedFalseMarksTheUserNotSuspendedAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET suspended = true WHERE id = ?", id);

        // Act
        final boolean result = userDao.setSuspended(id, false);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "id = " + id + " AND suspended = false"));
    }

    // ---- count() / findPage(): sin texto ----

    @Test
    public void testCountWithoutTextCountsEveryUserNotEveryRole() {
        // Arrange
        final long first = insertUser(EMAIL, NAME);
        insertRole(first, "USER");
        insertRole(first, "ADMIN");
        insertUser("otro@mail.com", "Beto");

        // Act
        final long count = userDao.count(null);

        // Assert
        assertEquals(2, count);
    }

    @Test
    public void testCountOfBlankTextCountsEveryUser() {
        // Arrange
        insertUser(EMAIL, NAME);
        insertUser("otro@mail.com", "Beto");

        // Act
        final long count = userDao.count("   ");

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
        final List<User> page = userDao.findPage(null, 2, 1);

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
        insertRole(first, "USER");
        insertRole(first, "ADMIN");
        final long second = insertUser("otro@mail.com", "Beto");

        // Act
        final List<User> page = userDao.findPage(null, 2, 0);

        // Assert
        assertEquals(2, page.size());
        assertEquals(first, page.get(0).getId().longValue());
        assertEquals(2, page.get(0).getRoles().size());
        assertEquals(second, page.get(1).getId().longValue());
    }

    // ---- count() / findPage(): buscando por mail ----

    private static List<String> emailsOf(final List<User> users) {
        return users.stream().map(User::getEmail).collect(Collectors.toList());
    }

    @Test
    public void testFindPageByTextFindsAnEmailThatContainsItIgnoringCase() {
        // Arrange
        insertUser("JUANA@Mail.com", "Juana");
        insertUser("beto@mail.com", "Beto");

        // Act
        final List<User> page = userDao.findPage("uAn", 10, 0);

        // Assert
        assertEquals(Collections.singletonList("JUANA@Mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextOnlyLooksAtTheEmailNotTheName() {
        // Arrange
        insertUser("x@mail.com", "Roberto");

        // Act
        final List<User> page = userDao.findPage("roberto", 10, 0);

        // Assert
        assertTrue(page.isEmpty());
    }

    @Test
    public void testFindPageByTextIgnoresTheSpacesAroundIt() {
        // Arrange
        insertUser("ana@mail.com", "Ana");

        // Act
        final List<User> page = userDao.findPage("  ana  ", 10, 0);

        // Assert
        assertEquals(Collections.singletonList("ana@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextPutsTheExactEmailFirstThenTheOnesThatStartWithItThenTheRest() {
        // Arrange: creados en el orden contrario al que tienen que salir
        insertUser("juana@mail.com", "Juana");
        insertUser("ana@mail.com.ar", "Ana");
        insertUser("ana@mail.com", "Ana");

        // Act
        final List<User> page = userDao.findPage("ana@mail.com", 10, 0);

        // Assert
        assertEquals(Arrays.asList("ana@mail.com", "ana@mail.com.ar", "juana@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextBreaksTiesInsideAGroupByEmail() {
        // Arrange: los dos empiezan con el texto; el de "1" se creo despues
        insertUser("ana2@mail.com", "Ana");
        insertUser("ana1@mail.com", "Ana");

        // Act
        final List<User> page = userDao.findPage("ana", 10, 0);

        // Assert
        assertEquals(Arrays.asList("ana1@mail.com", "ana2@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextTreatsAnUnderscoreAsAnOrdinaryLetter() {
        // Arrange
        insertUser("a_b@mail.com", "Uno");
        insertUser("axb@mail.com", "Dos");

        // Act
        final List<User> page = userDao.findPage("a_b", 10, 0);

        // Assert
        assertEquals(Collections.singletonList("a_b@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextTreatsAPercentAsAnOrdinaryLetter() {
        // Arrange
        insertUser("a@mail.com", "Uno");
        insertUser("100%@mail.com", "Dos");

        // Act
        final List<User> page = userDao.findPage("%", 10, 0);

        // Assert
        assertEquals(Collections.singletonList("100%@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextTreatsTheEscapeCharacterAsAnOrdinaryLetter() {
        // Arrange
        insertUser("a!b@mail.com", "Uno");
        insertUser("ab@mail.com", "Dos");

        // Act
        final List<User> page = userDao.findPage("a!", 10, 0);

        // Assert
        assertEquals(Collections.singletonList("a!b@mail.com"), emailsOf(page));
    }

    @Test
    public void testFindPageByTextPaginatesTheRankedResults() {
        // Arrange: el orden es [ana1, ana2, juana, mariana]
        insertUser("mariana@mail.com", "Uno");
        insertUser("ana2@mail.com", "Dos");
        insertUser("juana@mail.com", "Tres");
        insertUser("ana1@mail.com", "Cuatro");

        // Act
        final List<User> secondPage = userDao.findPage("ana", 2, 2);

        // Assert
        assertEquals(Arrays.asList("juana@mail.com", "mariana@mail.com"), emailsOf(secondPage));
    }

    @Test
    public void testFindPageByTextKeepsTheRolesOfEachUser() {
        // Arrange: dos roles = dos filas del JOIN, pero un solo resultado
        final long ana = insertUser("ana@mail.com", "Ana");
        insertRole(ana, "USER");
        insertRole(ana, "ADMIN");

        // Act
        final List<User> page = userDao.findPage("ana", 10, 0);

        // Assert
        assertEquals(1, page.size());
        assertEquals(2, page.get(0).getRoles().size());
    }

    @Test
    public void testFindPageByTextWithoutMatchesIsEmpty() {
        // Arrange
        insertUser("ana@mail.com", "Ana");

        // Act
        final List<User> page = userDao.findPage("zzz", 10, 0);

        // Assert
        assertTrue(page.isEmpty());
    }

    @Test
    public void testCountByTextCountsTheUsersThatMatchNotTheirRoles() {
        // Arrange
        final long ana = insertUser("ana@mail.com", "Ana");
        insertRole(ana, "USER");
        insertRole(ana, "ADMIN");
        insertUser("juana@mail.com", "Juana");
        insertUser("beto@mail.com", "Beto");

        // Act
        final long count = userDao.count("ANA");

        // Assert
        assertEquals(2, count);
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
        insertRole(id, "USER");
        insertRole(id, "ADMIN");

        // Act
        final List<User> result = userDao.findByIds(Collections.singletonList(id));

        // Assert
        assertEquals(new HashSet<>(Arrays.asList(Role.USER, Role.ADMIN)), result.get(0).getRoles());
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
    public void testSetPasswordOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar como estaba.
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setPassword(999_999L, "hashed-password");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "password = 'hashed-password'"));
    }

    @Test
    public void testUpdateProfileOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar como estaba.
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateProfile(999_999L, "Nuevo Nombre");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "name = 'Nuevo Nombre'"));
    }

    @Test
    public void testSetVerifiedOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar como estaba (sin verificar).
        final long other = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET verified = false WHERE id = ?", other);

        // Act
        final boolean result = userDao.setVerified(999_999L, true);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "verified = true"));
    }

    @Test
    public void testSetLocaleOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar como estaba.
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setLocale(999_999L, "en");
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "preferred_locale = 'en'"));
    }

    @Test
    public void testSetSuspendedOfAUserThatDoesNotExistWritesNothingAndReturnsFalse() {
        // Arrange: otro usuario, que no es el del Act. Tiene que quedar como estaba.
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.setSuspended(999_999L, true);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "suspended = true"));
    }

    // ---- findAccessById() ----

    @Test
    public void testFindAccessByIdJoinsAllTheRolesOfThatUser() {
        // Arrange: un superadmin con tres roles y un senuelo que solo es USER
        final long superadminId = insertUser("super@mail.com", "Super");
        insertRole(superadminId, "USER");
        insertRole(superadminId, "ADMIN");
        insertRole(superadminId, "SUPERADMIN");
        final long decoyId = insertUser("comun@mail.com", "Comun");
        insertRole(decoyId, "USER");

        // Act
        final Optional<UserAccess> result = userDao.findAccessById(superadminId);

        // Assert
        assertEquals(superadminId, result.get().getId());
        assertEquals(EnumSet.of(Role.USER, Role.ADMIN, Role.SUPERADMIN), result.get().getRoles());
        assertTrue(result.get().isAdmin());
        assertTrue(result.get().isSuperadmin());
    }

    @Test
    public void testFindAccessByIdOfAnAdminIsNotSuperadmin() {
        // Arrange
        final long adminId = insertUser("admin@mail.com", "Admin");
        insertRole(adminId, "USER");
        insertRole(adminId, "ADMIN");

        // Act
        final Optional<UserAccess> result = userDao.findAccessById(adminId);

        // Assert
        assertTrue(result.get().isAdmin());
        assertFalse(result.get().isSuperadmin());
    }

    @Test
    public void testFindAccessByIdOfAUserWithoutRolesExistsWithNoRoles() {
        // Arrange
        final long userId = insertUser("sinroles@mail.com", "Sin roles");

        // Act
        final Optional<UserAccess> result = userDao.findAccessById(userId);

        // Assert
        assertEquals(userId, result.get().getId());
        assertTrue(result.get().getRoles().isEmpty());
    }

    @Test
    public void testFindAccessByIdIsEmptyWhenTheUserDoesNotExist() {
        // Act
        final Optional<UserAccess> result = userDao.findAccessById(987654L);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findActivePublicById() ----

    // Una cuenta activa: con clave (insertUser la deja sin), mail confirmado (por defecto si) y sin suspender (idem).
    private long insertActiveUser(final String email, final String name) {
        final long id = insertUser(email, name);
        jdbcTemplate.update("UPDATE users SET password = 'hash' WHERE id = ?", id);
        return id;
    }

    @Test
    public void testFindActivePublicByIdReturnsTheNameBioAndCoverOfAnActiveAccount() {
        // Arrange
        final long id = insertActiveUser("activa@mail.com", "Tomas Rivas");
        final long imageId = insertImage();
        jdbcTemplate.update("UPDATE users SET bio = 'Fotografo', cover_image_id = ? WHERE id = ?", imageId, id);

        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(id);

        // Assert
        assertEquals(id, result.get().getId());
        assertEquals("Tomas Rivas", result.get().getName());
        assertEquals("Fotografo", result.get().getBio());
        assertEquals(Long.valueOf(imageId), result.get().getCoverImageId());
    }

    @Test
    public void testFindActivePublicByIdOfAnAccountWithoutBioNorCoverHasBothNull() {
        // Arrange
        final long id = insertActiveUser("sinextras@mail.com", "Sin extras");

        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(id);

        // Assert
        assertNull(result.get().getBio());
        assertNull(result.get().getCoverImageId());
    }

    @Test
    public void testFindActivePublicByIdIsEmptyForASuspendedAccount() {
        // Arrange
        final long id = insertActiveUser("suspendida@mail.com", "Suspendida");
        jdbcTemplate.update("UPDATE users SET suspended = true WHERE id = ?", id);

        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(id);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindActivePublicByIdIsEmptyForAnAccountThatNeverConfirmedTheMail() {
        // Arrange
        final long id = insertActiveUser("sinconfirmar@mail.com", "Sin confirmar");
        jdbcTemplate.update("UPDATE users SET verified = false WHERE id = ?", id);

        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(id);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindActivePublicByIdIsEmptyForAGhostAccountWithoutPassword() {
        // Arrange: insertUser deja la cuenta sin clave (la fila "fantasma" de antes del registro)
        final long id = insertUser("fantasma@mail.com", "Fantasma");

        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(id);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindActivePublicByIdIsEmptyWhenTheAccountDoesNotExist() {
        // Act
        final Optional<PublicUser> result = userDao.findActivePublicById(987654L);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- updateCoverImage() ----

    private long insertImage() {
        jdbcTemplate.update("INSERT INTO image (data, content_type, file_name, file_size) VALUES (?, 'image/png', 'a.png', 3)",
                new byte[] {1, 2, 3});
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM image", Long.class);
    }

    @Test
    public void testUpdateCoverImageWritesTheImageIdAndReturnsTrue() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        final long imageId = insertImage();

        // Act
        final boolean result = userDao.updateCoverImage(id, imageId);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND cover_image_id = " + imageId));
    }

    @Test
    public void testUpdateCoverImageWithNullClearsTheCover() {
        // Arrange
        final long id = insertUser(EMAIL, NAME);
        jdbcTemplate.update("UPDATE users SET cover_image_id = ? WHERE id = ?", insertImage(), id);

        // Act
        final boolean result = userDao.updateCoverImage(id, null);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(
                jdbcTemplate, "users", "id = " + id + " AND cover_image_id IS NULL"));
    }

    @Test
    public void testUpdateCoverImageOfAUserThatDoesNotExistReturnsFalse() {
        // Arrange: otro usuario, que tiene que quedar sin portada
        insertUser(EMAIL, NAME);

        // Act
        final boolean result = userDao.updateCoverImage(999_999L, insertImage());
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "users", "cover_image_id IS NOT NULL"));
    }
}
