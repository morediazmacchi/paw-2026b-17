package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.services.MailLinks;
import ar.edu.itba.paw.webapp.mail.WebMailLinks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
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
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.Locale;
import java.util.Properties;

// Contexto RAIZ de Spring (lo carga el ContextLoaderListener del web.xml): todo lo que NO es
// web. Base de datos, JPA/Hibernate, transacciones, services, DAOs y seguridad.
//
// Antes, WebConfig se cargaba dos veces (como contexto raiz y como contexto del
// DispatcherServlet), y eso duplicaba todo: dos EntityManagerFactory (dos arranques de
// Hibernate) y dos schedulers de recordatorios. Ahora cada cosa vive en UN solo contexto:
//   - RootConfig (este):  persistencia + services + seguridad.
//   - WebConfig:          solo Spring MVC (controllers, vistas, interceptores, validador).
// El contexto de MVC es "hijo" del raiz: ve los beans del raiz (por ejemplo los services),
// pero el raiz no ve los del hijo.
@EnableTransactionManagement
@ComponentScan({
        "ar.edu.itba.paw.services",
        "ar.edu.itba.paw.persistence",
})
// WebAuthConfig esta en este mismo paquete (webapp.config), que el @ComponentScan de arriba
// no barre: se importa a mano. La seguridad tiene que vivir en el contexto raiz porque el
// filtro "springSecurityFilterChain" (un DelegatingFilterProxy declarado en web.xml) lo busca ahi.
@Import(WebAuthConfig.class)
@Configuration
// La conexion a la base vive en db.properties (mismo criterio que mail.properties/MailConfig):
// cambiar de base es editar ese archivo, no tocar codigo. No hace falta un
// PropertySourcesPlaceholderConfigurer propio: el que ya registra MailConfig (via el
// @ComponentScan de arriba) resuelve los ${...} de TODO este contexto.
@PropertySource("classpath:db.properties")
public class RootConfig {

    @Value("${db.url}")
    private String dbUrl;

    @Value("${db.username}")
    private String dbUsername;

    @Value("${db.password}")
    private String dbPassword;

    @Value("classpath:schema.sql")
    private Resource schemaSql;

    // Solo para Postgres real: ver el comentario de postgres-migrations.sql.
    // TestConfig (HSQLDB en memoria, modulo persistence) no carga este script.
    @Value("classpath:postgres-migrations.sql")
    private Resource postgresMigrationsSql;

    @Value("classpath:data.sql")
    private Resource dataSql;

    // Se llama localeResolver (no otro nombre): es el nombre de bean que Spring Web busca por
    // convencion para resolver el idioma de cada pedido (el DispatcherServlet lo encuentra
    // aunque este en el contexto raiz). Vive aca y no en WebConfig porque WebAuthConfig
    // tambien lo usa (para recordar el idioma del usuario al loguearse).
    // CookieLocaleResolver (en vez del default, que solo mira el header del navegador) es lo
    // que permite el boton de idioma de la navbar (ver LocaleController) y que la eleccion
    // sobreviva a cerrar el navegador (PAW20-82).
    @Bean
    public LocaleResolver localeResolver() {
        return cookieLocaleResolver();
    }

    // Los links que llevan los mails (NotificationServiceImpl los pide por lo que son: "mis inscripciones", "el
    // detalle del evento"...). Las rutas de la web las conoce solo esta clase; el prefijo del servidor sale de
    // mail.properties (app.base-url), que cambia entre la maquina de desarrollo y pampero.
    @Bean
    public MailLinks mailLinks(@Value("${app.base-url}") final String baseUrl) {
        return new WebMailLinks(baseUrl);
    }

    // Aparte del @Bean de arriba porque WebConfig (el contexto hijo) tiene que crear el mismo
    // resolver: ver WebConfig.localeResolver().
    static LocaleResolver cookieLocaleResolver() {
        final CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setDefaultLocale(new Locale("es"));
        resolver.setCookieName("paw_locale");
        // 1 anio: no es una cookie de sesion, tiene que sobrevivir a cerrar el navegador.
        resolver.setCookieMaxAge(60 * 60 * 24 * 365);
        resolver.setCookiePath("/");
        return resolver;
    }

    @Bean
    public DataSource dataSource() {
        final SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriverClass(org.postgresql.Driver.class);
        ds.setUrl(dbUrl);
        ds.setUsername(dbUsername);
        ds.setPassword(dbPassword);
        return ds;
    }

    // JPA: construye la configuracion de Hibernate (el "proveedor" de JPA) sobre nuestro
    // DataSource y le dice en que paquete viven las entidades (las clases con @Entity).
    // @DependsOn: que el schema.sql y las migraciones ya hayan corrido cuando Hibernate arranca.
    @Bean
    @DependsOn("dataSourceInitializer")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(final DataSource ds) {
        final LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setPackagesToScan("ar.edu.itba.paw.model");
        factory.setDataSource(ds);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        final Properties properties = new Properties();
        // Dialecto = en que "idioma" de SQL le habla Hibernate a esta base.
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQL92Dialect");
        // Al leer una lista (por ejemplo de eventos), las relaciones lazy (fotos, categorias, dueno)
        // se cargan de a tandas de hasta 16 con un solo SELECT ... IN (...), en vez de una consulta
        // por cada fila (el problema "N+1").
        properties.setProperty("hibernate.default_batch_fetch_size", "16");
        // El esquema lo siguen manejando schema.sql y postgres-migrations.sql (nunca
        // hbm2ddl.auto=update/create: no migra bien y puede perder datos). Por eso no se setea.
        // Para ver el SQL que genera Hibernate: logger "org.hibernate.SQL" en DEBUG (logback.xml).
        factory.setJpaProperties(properties);
        return factory;
    }

    // Las transacciones las maneja JPA. Los @Transactional de los services no cambian.
    @Bean
    public PlatformTransactionManager transactionManager(final EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // Convierte las excepciones de Hibernate (ConstraintViolationException, etc.) en las de
    // Spring (DataIntegrityViolationException, etc.) en cada @Repository. static: es un
    // BeanPostProcessor y tiene que existir antes que el resto de los beans.
    @Bean
    public static PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
        return new PersistenceExceptionTranslationPostProcessor();
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
        dbp.addScript(schemaSql);
        dbp.addScript(postgresMigrationsSql);
        dbp.addScript(dataSql);
        return dbp;
    }
}
