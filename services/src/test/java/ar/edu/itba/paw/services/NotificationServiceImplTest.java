package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Cubre solo la resolucion de idioma por destinatario (localeFor()/
 * localeForUserId()): es la unica logica no trivial de esta clase, el resto
 * es "armar variables y delegar en MailService". Los dos metodos son
 * protected (no private) para que este test, en el mismo paquete, los pueda
 * invocar directo y assertear sobre el Locale que devuelven -- sin eso,
 * habria que pasar por un metodo void (notifyEventApproved(), etc.) y espiar
 * que le llego a MailService, que es exactamente lo que no hay que hacer.
 */
@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceImplTest {

    private static final long CREATOR_ID = 1;

    @Mock
    private MailService mockMailService;

    @Mock
    private MessageSource mockMessageSource;

    @Mock
    private UserDao mockUserDao;
    // Los links de los mails los arma la capa web (MailLinks): aca no importa su valor.
    @Mock
    private MailLinks mockMailLinks;

    private NotificationServiceImpl notificationService;

    private void setUp() {
        notificationService = new NotificationServiceImpl(mockMailService, mockMessageSource, mockUserDao, mockMailLinks);
    }

    private static User userWithLocale(final long id, final String locale) {
        final User user = new User("user@mail.com", "Ana");
        user.setId(id);
        user.setLocale(locale);
        return user;
    }

    // ---- localeForUserId() ----

    @Test
    public void testLocaleForUserIdUsesTheSavedLocale() {
        // Arrange
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, "en")));

        // Act
        final Locale locale = notificationService.localeForUserId(CREATOR_ID);

        // Assert
        assertEquals(new Locale("en"), locale);
    }

    @Test
    public void testLocaleForUserIdFallsBackToSpanishWhenNoLocaleSaved() {
        // Arrange
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, null)));

        // Act
        final Locale locale = notificationService.localeForUserId(CREATOR_ID);

        // Assert
        assertEquals(new Locale("es"), locale);
    }

    @Test
    public void testLocaleForUserIdFallsBackToSpanishWhenUserIdIsNull() {
        // Arrange
        // userId null (evento viejo sin creatorId, o invitado sin cuenta todavia,
        // ver EventInvitation.userId): no hay a quien buscarle el idioma, usa
        // el default directo.
        setUp();

        // Act
        final Locale locale = notificationService.localeForUserId(null);

        // Assert
        assertEquals(new Locale("es"), locale);
    }

    // ---- localeFor(User) ----

    @Test
    public void testLocaleForUsesTheUsersSavedLocale() {
        // Arrange
        setUp();
        final User user = userWithLocale(5, "en");

        // Act
        final Locale locale = notificationService.localeFor(user);

        // Assert
        assertEquals(new Locale("en"), locale);
    }

    @Test
    public void testLocaleForFallsBackToSpanishWhenNoLocaleSaved() {
        // Arrange
        setUp();
        final User user = userWithLocale(5, null);

        // Act
        final Locale locale = notificationService.localeFor(user);

        // Assert
        assertEquals(new Locale("es"), locale);
    }
}
