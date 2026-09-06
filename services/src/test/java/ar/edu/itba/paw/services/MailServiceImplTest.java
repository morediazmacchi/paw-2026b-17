package ar.edu.itba.paw.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MailServiceImplTest {

    private static final String TO = "invitado@mail.com";
    private static final String SUBJECT = "Confirmacion de inscripcion";
    private static final String HTML_BODY = "<p>Listo, quedaste confirmado.</p>";

    @Mock
    private JavaMailSender mockMailSender;

    @InjectMocks
    private MailServiceImpl mailService;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        // "from" viene de @Value("${mail.from}") en produccion (lo inyecta Spring
        // al levantar el contexto real). Aca no hay contexto de Spring -- @InjectMocks
        // solo entiende constructores y mocks -- asi que se lo asignamos a mano por
        // reflection, como si fuera el valor que vendria de mail.properties.
        final Field fromField = MailServiceImpl.class.getDeclaredField("from");
        fromField.setAccessible(true);
        fromField.set(mailService, "no-responder@plancito.com");
    }

    @Test
    public void testSendAsksTheMailSenderToSendTheMessage() {
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        mailService.send(TO, SUBJECT, HTML_BODY);

        // send() es void: la unica forma de comprobar que efectivamente se intento
        // mandar el mail es verificar la interaccion con el colaborador. No hay
        // ningun valor de retorno ni estado observable de otra forma (a diferencia
        // de los tests de EventService/RegistrationService, que evitan verify()
        // porque ahi si hay un resultado que revisar).
        verify(mockMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    public void testSendDoesNotPropagateTheExceptionWhenTheMailSenderFails() {
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP caido")).when(mockMailSender).send(any(MimeMessage.class));

        // Si send() propagara la excepcion, este test fallaria solo: JUnit reporta
        // como error cualquier excepcion no capturada durante un @Test. Que el test
        // termine "en verde" ya prueba que MailServiceImpl.send() se la trago.
        mailService.send(TO, SUBJECT, HTML_BODY);
    }
}
