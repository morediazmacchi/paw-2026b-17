// Prueba que la infraestructura de test (TestConfig + HSQLDB) funciona de punta a
// punta: el contexto de Spring levanta y el schema.sql REAL de produccion corre
// sin errores contra HSQLDB en modo sql.syntax_pgs. No prueba ningun DAO todavia
// (eso arranca en el paso 8 del plan, EventJdbcDaoTest) -- este archivo es solo
// el "smoke test" del paso 7.
package ar.edu.itba.paw.persistence;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
@Transactional
public class TestConfigSmokeTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @org.junit.Before
    public void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    public void testContextLoadsAndDataSourceIsUsable() {
        assertNotNull(dataSource);
    }

    @Test
    public void testSchemaSqlCreatedAllFiveTables() {
        final String[] tables = {"category", "users", "image", "event", "registration"};
        for (final String table : tables) {
            final Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            assertEquals(Integer.valueOf(0), count);
        }
    }
}
