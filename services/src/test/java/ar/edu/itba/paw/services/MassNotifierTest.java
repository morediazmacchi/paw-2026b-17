package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de MassNotifier con Mockito: todas las dependencias son @Mock. Se llama directo a los notify*() (sin
 * el proxy de Spring, asi que corren en el mismo hilo) y se asserta lo que devuelven: a cuantas personas se les aviso.
 * Como el runner es estricto, cada tanda stubeada TIENE que pedirse con ese "despues de este id" (o con esos ids): si
 * el recorrido por tandas estuviera mal, el stub queda sin usar y el test falla. Los mails en si (NotificationService)
 * no se testean aca: son void ("disparar y olvidar").
 */
@RunWith(MockitoJUnitRunner.class)
public class MassNotifierTest {

    private static final long EVENT_ID = 7;
    private static final long ORGANIZER_ID = 3;
    private static final int BATCH = MassNotifier.BATCH_SIZE;

    @Mock
    private EventService eventService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private FollowService followService;
    @Mock
    private NotificationService notificationService;

    private MassNotifier massNotifier;

    @Before
    public void setUp() {
        massNotifier = new MassNotifier(eventService, registrationService, followService, notificationService);
    }

    // Inscripciones confirmadas con ids de "from" a "to" (inclusive).
    private static List<Registration> registrations(final long from, final long to) {
        final List<Registration> result = new ArrayList<>();
        for (long id = from; id <= to; id++) {
            final Registration registration = new Registration(ModelFixtures.event(EVENT_ID), null,
                    "persona" + id + "@mail.com", "Persona " + id, RegistrationStatus.CONFIRMED);
            registration.setId(id);
            result.add(registration);
        }
        return result;
    }

    private static List<FollowerContact> followers(final long from, final long to) {
        return LongStream.rangeClosed(from, to)
                .mapToObj(id -> new FollowerContact(id, "fan" + id + "@mail.com", "Fan " + id, null))
                .collect(Collectors.toList());
    }

    private static List<Long> ids(final long from, final long to) {
        return LongStream.rangeClosed(from, to).boxed().collect(Collectors.toList());
    }

    private void eventExists() {
        final Event event = ModelFixtures.event(EVENT_ID);
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    // ---- notifyEventCancelled() / notifyEventUpdated() / notifyEventReminder(): los confirmados, de a tandas ----

    @Test
    public void testNotifyEventCancelledWalksTheConfirmedInBatchesStartingAfterTheLastIdOfTheBatchBefore() {
        // Arrange: una tanda completa (ids 1..BATCH) y una segunda incompleta (3 mas), que es la ultima.
        eventExists();
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, 0, BATCH)).thenReturn(registrations(1, BATCH));
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, BATCH, BATCH))
                .thenReturn(registrations(BATCH + 1, BATCH + 3));

        // Act
        final int notified = massNotifier.notifyEventCancelled(EVENT_ID);

        // Assert
        assertEquals(BATCH + 3, notified);
    }

    @Test
    public void testABatchThatFailsIsNotCountedAndDoesNotStopTheNextOne() {
        // Arrange: la primera tanda (completa) falla al mandar; la segunda (3 mas) sale bien
        eventExists();
        final List<Registration> firstBatch = registrations(1, BATCH);
        final List<Registration> secondBatch = registrations(BATCH + 1, BATCH + 3);
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, 0, BATCH)).thenReturn(firstBatch);
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, BATCH, BATCH)).thenReturn(secondBatch);
        doThrow(new RuntimeException("smtp caido")).when(notificationService)
                .notifyEventCancelled(any(Event.class), eq(firstBatch));

        // Act
        final int notified = massNotifier.notifyEventCancelled(EVENT_ID);

        // Assert: solo cuentan los que salieron
        assertEquals(3, notified);
    }

    @Test
    public void testNotifyEventUpdatedAsksForOneMoreBatchWhenTheLastOneWasFull() {
        // Arrange: exactamente una tanda completa; la siguiente viene vacia y ahi termina.
        eventExists();
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, 0, BATCH)).thenReturn(registrations(1, BATCH));
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, BATCH, BATCH)).thenReturn(Collections.emptyList());

        // Act
        final int notified = massNotifier.notifyEventUpdated(EVENT_ID);

        // Assert
        assertEquals(BATCH, notified);
    }

    @Test
    public void testNotifyEventReminderWithASingleSmallBatchNotifiesThoseAndStops() {
        // Arrange
        eventExists();
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, 0, BATCH)).thenReturn(registrations(1, 2));

        // Act
        final int notified = massNotifier.notifyEventReminder(EVENT_ID);

        // Assert
        assertEquals(2, notified);
    }

    @Test
    public void testNotifyEventCancelledWithNobodyConfirmedNotifiesNobody() {
        // Arrange
        eventExists();
        when(registrationService.findConfirmedByEventIdAfter(EVENT_ID, 0, BATCH)).thenReturn(Collections.emptyList());

        // Act
        final int notified = massNotifier.notifyEventCancelled(EVENT_ID);

        // Assert
        assertEquals(0, notified);
    }

    @Test
    public void testNotifyEventCancelledOfAnEventThatNoLongerExistsNotifiesNobody() {
        // Arrange: no se stubea ninguna tanda: si se pidiera alguna, el mock devolveria una lista vacia igual, pero
        // el resultado tiene que ser 0 sin buscar a nadie.
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        final int notified = massNotifier.notifyEventCancelled(EVENT_ID);

        // Assert
        assertEquals(0, notified);
    }

    // ---- notifyFollowersOfNewEvent(): los seguidores, de a tandas ----

    @Test
    public void testNotifyFollowersOfNewEventWalksTheFollowersInBatches() {
        // Arrange
        eventExists();
        when(followService.findFollowersAfter(ORGANIZER_ID, 0, BATCH)).thenReturn(followers(1, BATCH));
        when(followService.findFollowersAfter(ORGANIZER_ID, BATCH, BATCH)).thenReturn(followers(BATCH + 1, BATCH + 5));

        // Act
        final int notified = massNotifier.notifyFollowersOfNewEvent(EVENT_ID, ORGANIZER_ID, "Organizadora");

        // Assert
        assertEquals(BATCH + 5, notified);
    }

    @Test
    public void testNotifyFollowersOfNewEventWithNoFollowersNotifiesNobody() {
        // Arrange
        eventExists();
        when(followService.findFollowersAfter(ORGANIZER_ID, 0, BATCH)).thenReturn(Collections.emptyList());

        // Act
        final int notified = massNotifier.notifyFollowersOfNewEvent(EVENT_ID, ORGANIZER_ID, "Organizadora");

        // Assert
        assertEquals(0, notified);
    }

    // ---- notifyRegistrationsMoved(): los que cambiaron de estado, cargados de a una tanda de ids ----

    @Test
    public void testNotifyRegistrationsMovedLoadsTheIdsInBatches() {
        // Arrange: una tanda y media de ids; se cargan de a BATCH.
        eventExists();
        final List<Long> moved = ids(1, BATCH + BATCH / 2);
        when(registrationService.findByIds(ids(1, BATCH))).thenReturn(registrations(1, BATCH));
        when(registrationService.findByIds(ids(BATCH + 1, BATCH + BATCH / 2)))
                .thenReturn(registrations(BATCH + 1, BATCH + BATCH / 2));

        // Act
        final int notified = massNotifier.notifyRegistrationsMoved(EVENT_ID, moved, RegistrationStatus.WAITLISTED);

        // Assert
        assertEquals(BATCH + BATCH / 2, notified);
    }

    @Test
    public void testNotifyRegistrationsMovedToPendingNotifiesEveryoneThatMoved() {
        // Arrange
        eventExists();
        when(registrationService.findByIds(ids(4, 5))).thenReturn(registrations(4, 5));

        // Act
        final int notified = massNotifier.notifyRegistrationsMoved(EVENT_ID, ids(4, 5), RegistrationStatus.PENDING);

        // Assert
        assertEquals(2, notified);
    }

    @Test
    public void testNotifyRegistrationsMovedToConfirmedNotifiesEveryoneThatMoved() {
        // Arrange
        eventExists();
        when(registrationService.findByIds(ids(9, 9))).thenReturn(registrations(9, 9));

        // Act
        final int notified = massNotifier.notifyRegistrationsMoved(EVENT_ID, ids(9, 9), RegistrationStatus.CONFIRMED);

        // Assert
        assertEquals(1, notified);
    }

    @Test
    public void testNotifyRegistrationsMovedToRejectedHasNoMassNoticeAndNotifiesNobody() {
        // Arrange
        eventExists();

        // Act
        final int notified = massNotifier.notifyRegistrationsMoved(EVENT_ID, ids(1, 3), RegistrationStatus.REJECTED);

        // Assert
        assertEquals(0, notified);
    }
}
