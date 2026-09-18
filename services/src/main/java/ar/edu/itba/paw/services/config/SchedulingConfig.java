// Habilita las tareas periodicas (@Scheduled). Va aparte de MailConfig, que
// habilita @Async, para que cada archivo diga una sola cosa.
//
// El @ComponentScan de WebConfig apunta a "ar.edu.itba.paw.services" y tambien
// escanea los subpaquetes, asi que esta clase se levanta sola.
package ar.edu.itba.paw.services.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
