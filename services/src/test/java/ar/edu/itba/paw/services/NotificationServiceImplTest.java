package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.UserDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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

    private ArgumentCaptor<Locale> captureLocale() {
        final ArgumentCaptor<Locale> captor = ArgumentCaptor.forClass(Locale.class);
        verify(mockMailService).send(anyString(), anyString(), anyString(), any(), any(), captor.capture());
        return captor;
    }

    @Test
    public void testNotifyEventCreatedUsesTheCreatorsSavedLocale() {
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, "en")));

        notificationService.notifyEventCreated(eventOf(CREATOR_ID), "Ana");

        assertEquals(new Locale("en"), captureLocale().getValue());
    }

    @Test
    public void testNotifyEventCreatedFallsBackToSpanishWhenTheCreatorNeverChoseALanguage() {
        setUp();
        when(mockUserDao.findById(CREATOR_ID)).thenReturn(Optional.of(userWithLocale(CREATOR_ID, null)));

        notificationService.notifyEventCreated(eventOf(CREATOR_ID), "Ana");

        assertEquals(new Locale("es"), captureLocale().getValue());
    }

    @Test
    public void testNotifyEventCreatedFallsBackToSpanishWhenTheEventHasNoCreatorId() {
        // Evento viejo, de antes del backfill (ver Event.creatorId): no hay a
        // quien buscarle el idioma, no vale la pena reventar el mail por eso.
        setUp();

        notificationService.notifyEventCreated(eventOf(null), "Ana");

        assertEquals(new Locale("es"), captureLocale().getValue());
        verify(mockUserDao, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void testNotifyRegistrationConfirmedUsesTheRegistrantsSavedLocale() {
        setUp();
        when(mockUserDao.findById(REGISTRANT_ID)).thenReturn(Optional.of(userWithLocale(REGISTRANT_ID, "en")));

        notificationService.notifyRegistrationConfirmed(eventOf(CREATOR_ID), registrationOf(REGISTRANT_ID));

        assertEquals(new Locale("en"), captureLocale().getValue());
    }

    @Test
    public void testNotifyEventInvitationFallsBackToSpanishWhenTheInviteeHasNoAccountYet() {
        // Invitar a alguien sin cuenta es un caso normal y permanente (ver
        // EventInvitation.userId): no hay idioma guardado para esa persona.
        setUp();
        final EventInvitation invitation = new EventInvitation();
        invitation.setEmail("sinCuenta@mail.com");
        invitation.setUserId(null);

        notificationService.notifyEventInvitation(eventOf(CREATOR_ID), invitation);

        assertEquals(new Locale("es"), captureLocale().getValue());
    }

    @Test
    public void testNotifyAccountVerificationUsesTheUsersSavedLocale() {
        setUp();
        final User user = userWithLocale(5, "en");

        notificationService.notifyAccountVerification(user, "some-token", null);

        assertEquals(new Locale("en"), captureLocale().getValue());
    }

    @Test
    public void testNotifyPasswordResetFallsBackToSpanishWhenTheUserNeverChoseALanguage() {
        setUp();
        final User user = userWithLocale(5, null);

        notificationService.notifyPasswordReset(user, "some-token");

        assertEquals(new Locale("es"), captureLocale().getValue());
    }

    private static User userWithLocale(final long id, final String locale) {
        final User user = new User("user@mail.com", "Ana");
        user.setId(id);
        user.setLocale(locale);
        return user;
    }
}
