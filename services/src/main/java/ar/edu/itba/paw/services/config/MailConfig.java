package ar.edu.itba.paw.services.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.MessageSource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Properties;

/**
 * Configuracion del envio de mails.
 *
 * Vive en el modulo services (y no en WebConfig) porque la catedra pide que
 * los mails se manden SIEMPRE desde la capa de servicios: la configuracion
 * queda al lado de quien la usa.
 *
 * WebConfig no necesita ningun cambio: su @ComponentScan("ar.edu.itba.paw.services")
 * tambien escanea los subpaquetes, y una clase @Configuration es un @Component,
 * asi que Spring encuentra esta clase sola.
 */
@Configuration
@PropertySource("classpath:mail.properties")
public class MailConfig {

    // Cada @Value("${...}") lee una clave del mail.properties.
    // Spring convierte solo el texto al tipo que pongamos (int, boolean, etc).
    @Value("${mail.host}")
    private String host;

    @Value("${mail.port}")
    private int port;

    @Value("${mail.username}")
    private String username;

    @Value("${mail.password}")
    private String password;

    @Value("${mail.auth}")
    private boolean auth;

    @Value("${mail.starttls}")
    private boolean starttls;

    /**
     * JavaMailSender es la interfaz de Spring para mandar mails.
     * JavaMailSenderImpl es la implementacion; por dentro usa javax.mail,
     * que es la libreria que realmente habla el protocolo SMTP.
     *
     * Nosotros devolvemos el tipo interfaz (JavaMailSender), igual que hacemos
     * con ImageService: quien lo reciba no se entera de la implementacion.
     */
    @Bean
    public JavaMailSender javaMailSender() {
        final JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8"); // para que los acentos y la n con virgulilla no se rompan

        // Estas son opciones de javax.mail, no de Spring. Van como Properties.
        final Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        // auth: si el servidor pide usuario y contrasena. Gmail si, el falso local no.
        props.put("mail.smtp.auth", String.valueOf(auth));
        // starttls: si la conexion se cifra. Gmail lo exige, el falso local no lo soporta.
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));

        return sender;
    }

    /**
     * De donde salen los TEXTOS de los mails.
     *
     * El basename no lleva ni el idioma ni el .properties: Spring le agrega
     * "_es" o "_en" segun el Locale que le pasemos, y busca
     * i18n/mail-messages_es.properties o i18n/mail-messages_en.properties.
     *
     * Se llama mailMessageSource y no messageSource a proposito: ese nombre
     * exacto es especial para Spring, y ademas mas adelante el sitio va a
     * tener el suyo para los JSP. Que no se pisen.
     */
    @Bean
    public MessageSource mailMessageSource() {
        final ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasename("classpath:i18n/mail-messages");
        ms.setDefaultEncoding("UTF-8");
        // Sin esto, si falta el idioma pedido usaria el del sistema operativo
        // del servidor, que en pampero puede ser cualquier cosa.
        ms.setFallbackToSystemLocale(false);
        return ms;
    }

    /**
     * El motor de plantillas: recibe una plantilla + unos datos y devuelve el HTML final.
     *
     * OJO: esto NO reemplaza a los JSP. Las paginas del sitio siguen siendo JSP.
     * Thymeleaf lo usamos solo para los mails, porque un JSP necesita un servidor
     * web que lo ejecute y un mail se arma sin que haya ninguna pagina de por medio.
     */
    @Bean
    public TemplateEngine mailTemplateEngine() {
        final ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        // Con este prefijo, pedir la plantilla "event-created" busca
        // el archivo mails/event-created.html adentro del jar.
        resolver.setPrefix("mails/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(true);

        final TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        // Aca enchufamos el puente: los #{...} de las plantillas van a parar
        // al MessageSource de arriba.
        engine.setMessageResolver(new SpringMessageResolver(mailMessageSource()));
        return engine;
    }

    /**
     * Sin este bean, Spring no reemplaza los ${...} de los @Value de arriba:
     * los dejaria como el texto literal "${mail.host}" y explotaria.
     *
     * Tiene que ser static por una cuestion de orden de arranque: este objeto
     * se necesita ANTES de que Spring termine de construir la propia MailConfig,
     * y un metodo static se puede llamar sin tener la instancia todavia.
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
}
