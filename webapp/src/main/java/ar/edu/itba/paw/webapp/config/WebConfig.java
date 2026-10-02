package ar.edu.itba.paw.webapp.config;

import ar.edu.itba.paw.services.InboxService;
import ar.edu.itba.paw.services.UserService;
import ar.edu.itba.paw.webapp.interceptor.CurrentLocaleInterceptor;
import ar.edu.itba.paw.webapp.interceptor.SuspendedUserInterceptor;
import ar.edu.itba.paw.webapp.interceptor.UnreadInboxInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.DatabasePopulator;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import javax.sql.DataSource;
import java.util.Locale;

@EnableWebMvc
@EnableTransactionManagement
@ComponentScan({
        "ar.edu.itba.paw.services",
        "ar.edu.itba.paw.persistence",
        "ar.edu.itba.paw.webapp.controller",
})
// WebAuthConfig esta en este mismo paquete (webapp.config), que el
// @ComponentScan de arriba no barre (ni siquiera a esta clase: WebConfig se
// carga porque web.xml la nombra explicitamente, no por component-scan).
@Import(WebAuthConfig.class)
@Configuration
// La conexion a la base vive en db.properties (mismo criterio que
// mail.properties/MailConfig): cambiar de base es editar ese archivo, no
// tocar codigo. No hace falta un PropertySourcesPlaceholderConfigurer propio
// aca: el que ya registra MailConfig (via el @ComponentScan de aca abajo)
// resuelve los ${...} de TODO el contexto, no solo los de su propia clase.
@PropertySource("classpath:db.properties")
public class WebConfig implements WebMvcConfigurer {

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

    // Se llama messageSource (no otro nombre): es el nombre de bean que Spring
    // busca por convencion para resolver <spring:message> en los JSP. El de los
    // mails vive aparte, en services/MailConfig, como mailMessageSource.
    @Bean
    public MessageSource messageSource() {
        final ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/messages");
        ms.setDefaultEncoding("UTF-8");
        // Relee los .properties cada 5 segundos en vez de una sola vez al arrancar:
        // conviene en desarrollo, y el costo en produccion es insignificante.
        ms.setCacheSeconds(5);
        return ms;
    }

    // Se llama localeResolver (no otro nombre): igual que messageSource(), es el
    // nombre de bean que Spring Web busca por convencion para resolver el idioma
    // de cada pedido. CookieLocaleResolver (en vez del default,
    // AcceptHeaderLocaleResolver, que solo mira el header del navegador y no se
    // puede cambiar a mano) es lo que permite el boton de idioma de la navbar
    // (ver LocaleController) y que la eleccion sobreviva a cerrar el navegador
    // (PAW20-82: "recordar el idioma para no tener que cambiarlo en cada visita").
    @Bean
    public LocaleResolver localeResolver() {
        final CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setDefaultLocale(new Locale("es"));
        resolver.setCookieName("paw_locale");
        // 1 anio: no es una cookie de sesion, tiene que sobrevivir a cerrar el navegador.
        resolver.setCookieMaxAge(60 * 60 * 24 * 365);
        resolver.setCookiePath("/");
        return resolver;
    }

    @Bean
    public ViewResolver viewResolver() {
        final InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setViewClass(JstlView.class);
        viewResolver.setPrefix("/WEB-INF/jsp/");
        viewResolver.setSuffix(".jsp");
        return viewResolver;
    }

    // El servidor (Tomcat/Jetty) acepta el archivo gracias al <multipart-config> del web.xml.
    // Este bean es la otra mitad: hace que Spring lo convierta en un MultipartFile usable.
    // OJO: el metodo TIENE que llamarse multipartResolver, Spring lo busca por nombre.
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
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

    @Bean
    public PlatformTransactionManager transactionManager(final DataSource ds) {
        return new DataSourceTransactionManager(ds);
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

    // @Lazy: esta clase define el DataSource que el propio InboxService necesita
    // (a traves de su DAO). Sin @Lazy, Spring tendria que crear el service mientras
    // todavia esta armando esta config. Con @Lazy inyecta un "representante" y
    // resuelve el service de verdad la primera vez que se usa (en un request).
    @Autowired
    @Lazy
    private InboxService inboxService;

    @Autowired
    @Lazy
    private UserService userService;

    // El numerito de la bandeja en la navbar de todas las paginas (ver UnreadInboxInterceptor)
    // y el corte de sesion de las cuentas suspendidas (ver SuspendedUserInterceptor).
    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(new SuspendedUserInterceptor(userService));
        registry.addInterceptor(new UnreadInboxInterceptor(inboxService, userService));
        registry.addInterceptor(new CurrentLocaleInterceptor());
    }

    // Esto es lo que mostro Sotuyo en clase: le dice a Spring que las carpetas
    // /css y /js NO tienen que pasar por el Front Controller (DispatcherServlet).
    // Son archivos estaticos, se sirven directo, no son una ruta nuestra (@RequestMapping).
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("/js/");
        registry.addResourceHandler("/favicon.svg").addResourceLocations("/");
    }
}
