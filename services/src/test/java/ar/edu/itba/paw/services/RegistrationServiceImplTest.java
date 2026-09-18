package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RegistrationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final String EMAIL = "  Test@Mail.com  ";
    private static final String NORMALIZED_EMAIL = "test@mail.com";
    private static final String NAME = "Ana";

    @Mock
    private RegistrationDao mockRegistrationDao;
    @Mock
    private EventDao mockEventDao;
    @Mock
    private UserService mockUserService;
    // Necesario aunque ningun test lo use: @InjectMocks arma el objeto por
    // constructor, y sin este mock le pasaria null -> NPE al notificar.
    @Mock
    private NotificationService mockNotificationService;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    private static Event eventWithCapacity(final Integer maxCapacity) {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setMaxCapacity(maxCapacity);
        return event;
    }

    // ---- register() ----

    @Test
    public void testRegisterReturnsAlreadyRegisteredWhenTheEmailIsAlreadyInThatEvent() {
        final Registration existing = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(existing));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.ALREADY_REGISTERED, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRegisterWithNonexistentEventThrowsIllegalArgumentException() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.empty());

        registrationService.register(EVENT_ID, EMAIL, NAME);
    }

    @Test(expected = IllegalStateException.class)
    public void testRegisterWhenTheEventIsFullThrowsIllegalStateException() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        registrationService.register(EVENT_ID, EMAIL, NAME);
    }

    @Test
    public void testRegisterWithFreeSpotReturnsConfirmed() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(0L);

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    @Test
    public void testRegisterWithUnlimitedCapacityNeverThrowsEvenIfManyAreConfirmed() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());
        when(mockEventDao.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        final RegistrationResult result = registrationService.register(EVENT_ID, EMAIL, NAME);

        assertEquals(RegistrationResult.CONFIRMED, result);
    }

    // ---- isFull() ----

    @Test
    public void testIsFullReturnsTrueWhenConfirmedCountReachesCapacity() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(2L);

        final boolean result = registrationService.isFull(EVENT_ID);

        assertTrue(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenThereIsStillRoom() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(2)));
        when(mockRegistrationDao.countConfirmed(EVENT_ID)).thenReturn(1L);

        final boolean result = registrationService.isFull(EVENT_ID);

        assertFalse(result);
    }

    @Test
    public void testIsFullReturnsFalseWhenCapacityIsUnlimited() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.of(eventWithCapacity(null)));

        final boolean result = registrationService.isFull(EVENT_ID);

        assertFalse(result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIsFullWithNonexistentEventThrowsIllegalArgumentException() {
        when(mockEventDao.findById(EVENT_ID)).thenReturn(Optional.empty());

        registrationService.isFull(EVENT_ID);
    }

    // ---- findByEmail() ----

    @Test
    public void testFindByEmailDelegatesToTheDao() {
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED));
        when(mockRegistrationDao.findByEmail(NORMALIZED_EMAIL)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEmail(NORMALIZED_EMAIL);

        assertEquals(registrations, result);
    }

    // ---- cancel() ----

    @Test
    public void testCancelDeletesWhenTheEmailOwnsTheRegistration() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertTrue(result);
        verify(mockRegistrationDao).delete(5);
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationDoesNotExist() {
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.empty());

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).delete(5);
    }

    @Test
    public void testCancelReturnsFalseWhenTheRegistrationBelongsToSomeoneElse() {
        final Registration registration = new Registration(EVENT_ID, "otro@mail.com", NAME, Registration.STATUS_CONFIRMED);
        registration.setId(5);
        when(mockRegistrationDao.findById(5)).thenReturn(Optional.of(registration));

        final boolean result = registrationService.cancel(5, NORMALIZED_EMAIL);

        assertFalse(result);
        verify(mockRegistrationDao, never()).delete(5);
    }

    // ---- findByEventAndEmail() ----

    @Test
    public void testFindByEventAndEmailNormalizesTheEmail() {
        final Registration registration = new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED);
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.of(registration));

        final Optional<Registration> result = registrationService.findByEventAndEmail(EVENT_ID, EMAIL);

        assertTrue(result.isPresent());
        assertEquals(NORMALIZED_EMAIL, result.get().getEmail());
    }

    @Test
    public void testFindByEventAndEmailReturnsEmptyWhenNotRegistered() {
        when(mockRegistrationDao.findByEventAndEmail(EVENT_ID, NORMALIZED_EMAIL)).thenReturn(Optional.empty());

        final Optional<Registration> result = registrationService.findByEventAndEmail(EVENT_ID, EMAIL);

        assertFalse(result.isPresent());
    }

    // ---- findByEventId() ----

    @Test
    public void testFindByEventIdDelegatesToTheDao() {
        final List<Registration> registrations = Collections.singletonList(
                new Registration(EVENT_ID, NORMALIZED_EMAIL, NAME, Registration.STATUS_CONFIRMED));
        when(mockRegistrationDao.findByEventId(EVENT_ID)).thenReturn(registrations);

        final List<Registration> result = registrationService.findByEventId(EVENT_ID);

        assertEquals(registrations, result);
    }
}
