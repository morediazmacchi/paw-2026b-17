package ar.edu.itba.paw.webapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import javax.sql.DataSource;

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
public class WebConfig implements WebMvcConfigurer {

    // LOCAL (desarrollo)
    // Para volver a desarrollo local: comentar el bloque PAMPERO de abajo, descomentar este
    private static final String DB_URL = "jdbc:postgresql://localhost/paw2026b";
    private static final String DB_USERNAME = "paw";
    private static final String DB_PASSWORD = "paw";

    // PAMPERO (deploy)
    //private static final String DB_URL = "jdbc:postgresql://10.16.1.110/paw-2026b-17";
    //private static final String DB_USERNAME = "paw-2026b-17";
    //private static final String DB_PASSWORD = "cla65LjaE";

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
        ds.setUrl(DB_URL);
        ds.setUsername(DB_USERNAME);
        ds.setPassword(DB_PASSWORD);
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

    // Esto es lo que mostro Sotuyo en clase: le dice a Spring que las carpetas
    // /css, /js e /images NO tienen que pasar por el Front Controller (DispatcherServlet).
    // Son archivos estaticos, se sirven directo, no son una ruta nuestra (@RequestMapping).
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("/js/");
        registry.addResourceHandler("/images/**").addResourceLocations("/images/");
        registry.addResourceHandler("/favicon.svg").addResourceLocations("/");
    }
}
