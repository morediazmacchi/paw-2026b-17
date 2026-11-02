package ar.edu.itba.paw.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

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
    private static final String FROM = "no-responder@plancito.com";

    @Mock
    private JavaMailSender mockMailSender;

    private MailServiceImpl mailService;

    @Before
    public void setUp() {
        // "from" en produccion sale de mail.properties (@Value en el constructor); aca se pasa
        // como cualquier otro parametro. Estos tests son del envio crudo (send con el HTML ya
        // armado), que no usa la plantilla ni los textos: por eso esos dos van en null.
        mailService = new MailServiceImpl(mockMailSender, null, null, FROM);
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
