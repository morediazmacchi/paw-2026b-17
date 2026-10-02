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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    public void testSendReturnsTrueWhenTheMailSenderSucceeds() {
        // Arrange
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        // Act
        final boolean result = mailService.send(TO, SUBJECT, HTML_BODY);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testSendReturnsFalseWhenTheMailSenderFails() {
        // Arrange
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP caido")).when(mockMailSender).send(any(MimeMessage.class));

        // Act
        final boolean result = mailService.send(TO, SUBJECT, HTML_BODY);

        // Assert
        assertFalse(result);
    }
}
