// Configuracion de Spring solo para los tests de persistence (JUnit 4 + HSQLDB en
// memoria). No se usa en produccion: WebConfig (en webapp) tiene su propia
// configuracion contra Postgres real.
package ar.edu.itba.paw.persistence;

import org.hibernate.dialect.HSQLDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@ComponentScan("ar.edu.itba.paw.persistence")
public class TestConfig {

    @Bean
    public DataSource dataSource() {
        final SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(org.hsqldb.jdbc.JDBCDriver.class);
        ds.setUrl("jdbc:hsqldb:mem:paw;sql.syntax_pgs=true");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean
    public DataSourceInitializer dataSourceInitializer(final DataSource ds) {
        final DataSourceInitializer dsi = new DataSourceInitializer();
        dsi.setDataSource(ds);
        dsi.setDatabasePopulator(databasePopulator());
        return dsi;
    }

    private DatabasePopulator databasePopulator() {
        final ResourceDatabasePopulator dbp = new ResourceDatabasePopulator();
        dbp.addScript(new org.springframework.core.io.ClassPathResource("schema.sql"));
        return dbp;
    }

    // @DependsOn: el schema.sql tiene que haber corrido antes de que Hibernate arranque.
    @Bean
    @DependsOn("dataSourceInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(final DataSource ds) {
        final LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPackagesToScan("ar.edu.itba.paw.model");
        factory.setDataSource(ds);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        final Properties props = new Properties();
        props.setProperty("hibernate.dialect", HSQLDialect.class.getName());
        // Al leer una lista (por ejemplo de eventos), las relaciones lazy (fotos, categorias, dueno)
        // se cargan de a tandas de hasta 16 con un solo SELECT ... IN (...), en vez de una consulta
        // por cada fila (el problema "N+1").
        props.setProperty("hibernate.default_batch_fetch_size", "16");
        factory.setJpaProperties(props);
        return factory;
    }

    // Traduce las excepciones de JPA/Hibernate (ConstraintViolationException, etc.) a las de
    // Spring (DataIntegrityViolationException, ...) en cada @Repository: asi los services no
    // dependen de Hibernate. Static a proposito: es un BeanPostProcessor.
    @Bean
    public static PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
    }

    @Bean
    public PlatformTransactionManager transactionManager(final EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
