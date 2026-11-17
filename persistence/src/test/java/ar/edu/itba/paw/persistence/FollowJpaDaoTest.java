package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.FollowerContact;
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
import java.util.Arrays;
import java.util.Collections;
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

    // Un evento organizado por esa persona (por SQL; creator_id es lo unico que importa aca).
    private long insertEventOrganizedBy(final Long organizerId) {
        jdbcTemplate.update(
                "INSERT INTO event (title, event_date, address, creator_email, creator_id) VALUES (?, ?, ?, ?, ?)",
                "Evento " + System.nanoTime(), new java.sql.Timestamp(System.currentTimeMillis()), "Corrientes 1234",
                "organizador@mail.com", organizerId);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM event", Long.class);
    }

    private String followWhere(final long followerId, final long followedId) {
        return "follower_id = " + followerId + " AND followed_id = " + followedId;
    }

    // ---- followOrganizerOf() ----

    @Test
    public void testFollowInsertsTheFollowRowAndReturnsTrue() {
        // Arrange: un evento del organizador; followerId/followedId ya vienen del setUp, sin seguimiento todavia.
        final long eventId = insertEventOrganizedBy(followedId);

        // Act
        final boolean result = followDao.followOrganizerOf(followerId, eventId);
        // Hibernate escribe recien al flush: se fuerza para poder leer la fila por JDBC.
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    @Test
    public void testFollowWhenAlreadyFollowingKeepsOneRowAndReturnsFalse() {
        // Arrange: ya lo sigue (insertado por SQL, no con followOrganizerOf()).
        final long eventId = insertEventOrganizedBy(followedId);
        insertFollow(followerId, followedId);

        // Act
        // No hay ON CONFLICT (no es portable a HSQLDB): followOrganizerOf() depende de que
        // el PRIMARY KEY compuesto absorba el duplicado sin explotar.
        final boolean result = followDao.followOrganizerOf(followerId, eventId);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(1, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    @Test
    public void testFollowOrganizerOfAnEventWithoutOrganizerInsertsNothingAndReturnsFalse() {
        // Arrange: evento viejo, sin organizador cargado
        final long eventId = insertEventOrganizedBy(null);

        // Act
        final boolean result = followDao.followOrganizerOf(followerId, eventId);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", "follower_id = " + followerId));
    }

    @Test
    public void testFollowOrganizerOfAMissingEventInsertsNothingAndReturnsFalse() {
        // Act
        final boolean result = followDao.followOrganizerOf(followerId, 999_999L);
        em.flush();

        // Assert
        assertFalse(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", "follower_id = " + followerId));
    }

    // ---- unfollowOrganizerOf() ----

    @Test
    public void testUnfollowDeletesTheFollowRowAndReturnsTrue() {
        // Arrange
        final long eventId = insertEventOrganizedBy(followedId);
        insertFollow(followerId, followedId);

        // Act
        final boolean result = followDao.unfollowOrganizerOf(followerId, eventId);
        em.flush();

        // Assert
        assertTrue(result);
        assertEquals(0, JdbcTestUtils.countRowsInTableWhere(jdbcTemplate, "follow", followWhere(followerId, followedId)));
    }

    @Test
    public void testUnfollowWhenNeverFollowingDeletesNothingAndReturnsFalse() {
        // Arrange: un seguimiento de la misma persona, pero a otro organizador. Tiene que quedar.
        final long otherFollowedId = insertUser("otro-organizador@mail.com", "Otro");
        final long eventId = insertEventOrganizedBy(followedId);
        insertFollow(followerId, otherFollowedId);

        // Act
        final boolean result = followDao.unfollowOrganizerOf(followerId, eventId);
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

    // ---- findFollowersAfter() ----

    @Test
    public void testFindFollowersAfterReturnsOnlyTheFollowersOfThatUser() {
        // Arrange: otro seguidor del mismo organizador, y un seguidor de OTRO organizador (no tiene que salir).
        final long otherFollowerId = insertUser("otro-fan@mail.com", "Otro Fan");
        final long unrelatedFollowedId = insertUser("otro-organizador2@mail.com", "Sin relacion");
        insertFollow(followerId, followedId);
        insertFollow(otherFollowerId, followedId);
        insertFollow(followerId, unrelatedFollowedId);

        // Act
        final List<FollowerContact> result = followDao.findFollowersAfter(followedId, 0, 100);

        // Assert
        assertEquals(2, result.size());
        final List<Long> ids = result.stream().map(FollowerContact::getId).collect(Collectors.toList());
        assertTrue(ids.contains(followerId));
        assertTrue(ids.contains(otherFollowerId));
    }

    @Test
    public void testFindFollowersAfterBringsTheEmailNameAndLanguageOfTheFollower() {
        // Arrange
        jdbcTemplate.update("UPDATE users SET preferred_locale = 'en' WHERE id = ?", followerId);
        insertFollow(followerId, followedId);

        // Act
        final List<FollowerContact> result = followDao.findFollowersAfter(followedId, 0, 100);

        // Assert
        assertEquals(1, result.size());
        assertEquals("fan@mail.com", result.get(0).getEmail());
        assertEquals("Fan", result.get(0).getName());
        assertEquals("en", result.get(0).getLocale());
    }

    @Test
    public void testFindFollowersAfterReturnsAtMostTheLimitFromTheLowestId() {
        // Arrange: tres seguidores del mismo organizador.
        final long second = insertUser("fan2@mail.com", "Fan 2");
        final long third = insertUser("fan3@mail.com", "Fan 3");
        insertFollow(followerId, followedId);
        insertFollow(second, followedId);
        insertFollow(third, followedId);

        // Act
        final List<FollowerContact> result = followDao.findFollowersAfter(followedId, 0, 2);

        // Assert: la primera tanda, de menor a mayor id.
        assertEquals(Arrays.asList(followerId, second),
                result.stream().map(FollowerContact::getId).collect(Collectors.toList()));
    }

    @Test
    public void testFindFollowersAfterStartsAfterTheGivenId() {
        // Arrange
        final long second = insertUser("fan2@mail.com", "Fan 2");
        final long third = insertUser("fan3@mail.com", "Fan 3");
        insertFollow(followerId, followedId);
        insertFollow(second, followedId);
        insertFollow(third, followedId);

        // Act: la tanda siguiente a la que termino en el segundo.
        final List<FollowerContact> result = followDao.findFollowersAfter(followedId, second, 2);

        // Assert
        assertEquals(Collections.singletonList(third),
                result.stream().map(FollowerContact::getId).collect(Collectors.toList()));
    }

    @Test
    public void testFindFollowersAfterWithNoFollowersIsEmpty() {
        // Act
        final List<FollowerContact> result = followDao.findFollowersAfter(followedId, 0, 100);

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
