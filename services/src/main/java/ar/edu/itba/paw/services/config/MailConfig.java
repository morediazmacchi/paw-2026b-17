package ar.edu.itba.paw.services.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

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
