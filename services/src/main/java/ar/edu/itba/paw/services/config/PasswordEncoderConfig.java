package ar.edu.itba.paw.services.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Vive en services, no en WebAuthConfig (webapp): el hasheo de passwords es
 * logica de negocio (lo usan UserServiceImpl.register() y el DaoAuthenticationProvider
 * para verificar login), no configuracion de filtros HTTP. webapp lo autowirea
 * desde aca en vez de definir su propio bean.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
