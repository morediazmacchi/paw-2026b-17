package ar.edu.itba.paw.persistence;

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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Unidad 5 - Unit Testing: followDao (el objeto bajo test) solo se toca en el Act. Arrange/Assert
// son siempre SQL directo, nunca otro metodo de followDao (por ejemplo, el Assert de follow() NO
// usa isFollowing()). Misma forma que FavoriteJpaDaoTest (follow es a Favorite lo que seguir un
// organizador es a marcar un evento favorito: clave compuesta, sin id propio).
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
@Rollback
public class FollowJpaDaoTest {

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private DataSource ds;

    @Autowired
    private FollowDao followDao;

    private JdbcTemplate jdbcTemplate;
    private long followerId;
    private long followedId;

    @Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(ds);
        followerId = insertUser("fan@mail.com", "Fan");
        followedId = insertUser("organizador@mail.com", "Organizador");
    }

    private long insertUser(final String email, final String name) {
        jdbcTemplate.update("INSERT INTO users (email, name) VALUES (?, ?)", email, name);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertFollow(final long followerId, final long followedId) {
        jdbcTemplate.update("INSERT INTO follow (follower_id, followed_id) VALUES (?, ?)", followerId, followedId);
    }

    private String followWhere(final long followerId, final long followedId) {
        return "follower_id = " + followerId + " AND followed_id = " + followedId;
    }

    // ---- follow() ----

    @Test
    public void testFollowInsertsTheFollowRowAndReturnsTrue() {
        // Arrange: followerId/followedId ya vienen del setUp, sin seguimiento todavia.

        // Act
        final boolean result = followDao.follow(followerId, followedId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    @Test
    public void testFollowWhenAlreadyFollowingKeepsOneRowAndReturnsFalse() {
        // Arrange: ya lo sigue (insertado por SQL, no con follow()).
        insertFollow(followerId, followedId);

        // Act
        // No hay ON CONFLICT (no es portable a HSQLDB): follow() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final boolean result = followDao.follow(followerId, followedId);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    // ---- unfollow() ----

    @Test
    public void testUnfollowDeletesTheFollowRowAndReturnsTrue() {
        // Arrange
        insertFollow(followerId, followedId);

        // Act
        final boolean result = followDao.unfollow(followerId, followedId);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    @Test
    public void testUnfollowWhenNeverFollowingDeletesNothingAndReturnsFalse() {
        // Arrange: un seguimiento de la misma persona, pero a otro organizador. Tiene que quedar.
        final long otherFollowedId = insertUser("otro-organizador@mail.com", "Otro");
        insertFollow(followerId, otherFollowedId);

        // Act
        final boolean result = followDao.unfollow(followerId, followedId);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, otherFollowedId)));
    }

    // ---- isFollowing() ----

    @Test
    public void testIsFollowingReturnsTrueWhenFollowing() {
        // Arrange
        insertFollow(followerId, followedId);

        // Act
        final boolean result = followDao.isFollowing(followerId, followedId);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsFollowingReturnsFalseWhenNotFollowing() {
        // Act
        final boolean result = followDao.isFollowing(followerId, followedId);

        // Assert
        assertFalse(result);
    }

    // ---- findFollowers() ----

    @Test
    public void testFindFollowersReturnsOnlyTheFollowersOfThatUser() {
        // Arrange: otro seguidor del mismo organizador, y un seguidor de OTRO organizador (no tiene que salir).
        final long otherFollowerId = insertUser("otro-fan@mail.com", "Otro Fan");
        final long unrelatedFollowedId = insertUser("otro-organizador2@mail.com", "Sin relacion");
        insertFollow(followerId, followedId);
        insertFollow(otherFollowerId, followedId);
        insertFollow(followerId, unrelatedFollowedId);

        // Act
        final List<User> result = followDao.findFollowers(followedId);

        // Assert
        assertEquals(2, result.size());
        final List<Long> ids = result.stream().map(User::getId).collect(Collectors.toList());
        assertTrue(ids.contains(followerId));
        assertTrue(ids.contains(otherFollowerId));
    }

    @Test
    public void testFindFollowersWithNoFollowersIsEmpty() {
        // Act
        final List<User> result = followDao.findFollowers(followedId);

        // Assert
        assertTrue(result.isEmpty());
    }

    // ---- countFollowers() ----

    @Test
    public void testCountFollowersCountsOnlyTheFollowersOfThatUser() {
        // Arrange: un seguidor de OTRO organizador no tiene que contar.
        final long otherFollowerId = insertUser("otro-fan@mail.com", "Otro Fan");
        final long unrelatedFollowedId = insertUser("otro-organizador2@mail.com", "Sin relacion");
        insertFollow(followerId, followedId);
        insertFollow(otherFollowerId, followedId);
        insertFollow(followerId, unrelatedFollowedId);

        // Act
        final long result = followDao.countFollowers(followedId);

        // Assert
        assertEquals(2, result);
    }

    @Test
    public void testCountFollowersWithNoFollowersIsZero() {
        // Act
        final long result = followDao.countFollowers(followedId);

        // Assert
        assertEquals(0, result);
    }
}
