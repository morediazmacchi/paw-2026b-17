package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Cubre solo la resolucion de idioma por destinatario (localeFor()/
 * localeForUserId()): es la unica logica no trivial de esta clase, el resto
 * es "armar variables y delegar en MailService".
 */
@RunWith(MockitoJUnitRunner.class)
public class NotificationServiceImplTest {

    private static final long CREATOR_ID = 1;
    private static final long REGISTRANT_ID = 2;

    @Mock
    private MailService mockMailService;

    @Mock
    private MessageSource mockMessageSource;

    @Mock
    private UserDao mockUserDao;

    private NotificationServiceImpl notificationService;

    private void setUp() {
        notificationService = new NotificationServiceImpl(mockMailService, mockMessageSource, mockUserDao);
    }

    private static Event eventOf(final Long creatorId) {
        final Event event = new Event();
        event.setId(1);
        event.setTitle("Asado");
        event.setCreatorEmail("creador@mail.com");
        event.setCreatorId(creatorId);
        return event;
    }

    private static Registration registrationOf(final Long userId) {
        final Registration registration = new Registration();
        registration.setEmail("invitado@mail.com");
        registration.setUserId(userId);
        return registration;
    }

    // send() es void: el locale que recibio es el unico dato que nos interesa
    // de la llamada. Se captura con doAnswer() sobre el stub (no con
    // Mockito.verify()+ArgumentCaptor) y se lee del holder despues del Act.
    private Locale[] captureLocale() {
        final Locale[] captured = new Locale[1];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(5);
            return null;
        }).when(mockMailService).send(anyString(), anyString(), anyString(), any(), any(), any());
        return captured;
    }

    @Test
    public void testNotifyEventCreatedUsesTheCreatorsSavedLocale() {
        // Arrange
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, "en")));
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyEventCreated(eventOf(CREATOR_ID), "Ana");

        // Assert
        assertEquals(new Locale("en"), capturedLocale[0]);
    }

    @Test
    public void testNotifyEventCreatedFallsBackToSpanishWhenTheCreatorNeverChoseALanguage() {
        // Arrange
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, null)));
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyEventCreated(eventOf(CREATOR_ID), "Ana");

        // Assert
        assertEquals(new Locale("es"), capturedLocale[0]);
    }

    @Test
    public void testNotifyEventCreatedFallsBackToSpanishWhenTheEventHasNoCreatorId() {
        // Arrange
        // Evento viejo, de antes del backfill (ver Event.creatorId): no hay a
        // quien buscarle el idioma, no vale la pena reventar el mail por eso.
        // mockUserDao.findById() se stubea para que reviente si se llega a
        // llamar: si el test pasa sin ese error, es la prueba (sin verify())
        // de que nunca se llamo. lenient(): a proposito se espera que este
        // stub quede sin usar -- Mockito por default marca eso como error.
        setUp();
        lenient().when(mockUserDao.findById(anyLong()))
                .thenThrow(new AssertionError("no deberia buscar el idioma de un evento sin creatorId"));
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyEventCreated(eventOf(null), "Ana");

        // Assert
        assertEquals(new Locale("es"), capturedLocale[0]);
    }

    @Test
    public void testNotifyRegistrationConfirmedUsesTheRegistrantsSavedLocale() {
        // Arrange
        setUp();
        when(mockUserDao.findById(REGISTRANT_ID)).thenReturn(Optional.of(userWithLocale(REGISTRANT_ID, "en")));
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyRegistrationConfirmed(eventOf(CREATOR_ID), registrationOf(REGISTRANT_ID));

        // Assert
        assertEquals(new Locale("en"), capturedLocale[0]);
    }

    @Test
    public void testNotifyEventInvitationFallsBackToSpanishWhenTheInviteeHasNoAccountYet() {
        // Arrange
        // Invitar a alguien sin cuenta es un caso normal y permanente (ver
        // EventInvitation.userId): no hay idioma guardado para esa persona.
        setUp();
        final EventInvitation invitation = new EventInvitation();
        invitation.setEmail("sinCuenta@mail.com");
        invitation.setUserId(null);
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyEventInvitation(eventOf(CREATOR_ID), invitation);

        // Assert
        assertEquals(new Locale("es"), capturedLocale[0]);
    }

    @Test
    public void testNotifyAccountVerificationUsesTheUsersSavedLocale() {
        // Arrange
        setUp();
        final User user = userWithLocale(5, "en");
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyAccountVerification(user, "some-token", null);

        // Assert
        assertEquals(new Locale("en"), capturedLocale[0]);
    }

    @Test
    public void testNotifyPasswordResetFallsBackToSpanishWhenTheUserNeverChoseALanguage() {
        // Arrange
        setUp();
        final User user = userWithLocale(5, null);
        final Locale[] capturedLocale = captureLocale();

        // Act
        notificationService.notifyPasswordReset(user, "some-token");

        // Assert
        assertEquals(new Locale("es"), capturedLocale[0]);
    }

    private static User userWithLocale(final long id, final String locale) {
        final User user = new User("user@mail.com", "Ana");
        user.setId(id);
        user.setLocale(locale);
        return user;
    }
}
