package ar.edu.itba.paw.webapp.security;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.Assert.assertEquals;

// Test de INTEGRACION (levanta un contexto de Spring chico): comprueba que la seguridad por
// metodos funciona de verdad, cosa que los tests de EventSecurity no ven. Si alguien saca
// @EnableGlobalMethodSecurity de WebConfig, los controllers quedan sin proteccion y sin ningun
// error; este test existe para que eso se note. Usa la misma configuracion que WebConfig
// (prePostEnabled + proxyTargetClass, porque el controller no tiene interfaz).
public class MethodSecurityIntegrationTest {

    private static final long EVENT_ID = 7L;
    private static final long OTHER_EVENT_ID = 8L;

    // EventSecurity de mentira: solo es dueno del evento EVENT_ID. Asi, que el metodo anotado
    // corra o no depende de que el id de la URL haya llegado bien a la regla.
    static class FakeEventSecurity extends EventSecurity {
        FakeEventSecurity() {
            super(null, null, null, null);
        }

        @Override
        public boolean isOwner(final long eventId, final Authentication authentication) {
            return eventId == EVENT_ID;
        }
    }

    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true, proxyTargetClass = true)
    static class SecurityConfig {
        @Bean
        public FakeEventSecurity eventSecurity() {
            return new FakeEventSecurity();
        }

        @Bean
        public GuardedController guardedController() {
            return new GuardedController();
        }
    }

    static class GuardedController {
        @PreAuthorize("@eventSecurity.isOwner(#id, authentication)")
        public String edit(final long id) {
            return "ok " + id;
        }
    }

    private AnnotationConfigApplicationContext context;
    private GuardedController controller;

    @Before
    public void setUp() {
        context = new AnnotationConfigApplicationContext(SecurityConfig.class);
        controller = context.getBean(GuardedController.class);
        final TestingAuthenticationToken authentication =
                new TestingAuthenticationToken("user@test.com", "x", "ROLE_USER");
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @After
    public void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test(expected = AccessDeniedException.class)
    public void testAMethodWhoseRuleSaysNoThrowsAccessDenied() {
        // Act
        controller.edit(OTHER_EVENT_ID);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert (ErrorAdvice la
        // convierte en el 404).
    }

    @Test
    public void testAMethodWhoseRuleSaysYesRuns() {
        // Act
        final String result = controller.edit(EVENT_ID);

        // Assert: la regla recibio el id del parametro (#id) y lo reconocio como suyo.
        assertEquals("ok " + EVENT_ID, result);
    }
}
