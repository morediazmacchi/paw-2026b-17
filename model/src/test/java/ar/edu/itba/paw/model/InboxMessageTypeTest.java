package ar.edu.itba.paw.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

// Que pantalla abre el boton "Abrir" de cada aviso de la bandeja (InboxMessageType.getTarget()).
public class InboxMessageTypeTest {

    @Test
    public void testSomeoneRegisteringOrLeavingOpensTheFullListOfRegistrants() {
        assertEquals(InboxMessageType.Target.ATTENDEES, InboxMessageType.NEW_REGISTRATION.getTarget());
    }

    @Test
    public void testSomeoneAskingToJoinOpensThePendingRequests() {
        assertEquals(InboxMessageType.Target.PENDING_REQUESTS, InboxMessageType.NEW_PENDING.getTarget());
    }

    @Test
    public void testOnlyTheOrganizerNoticesOpenARegistrantsScreen() {
        // Act: los tipos que NO abren el detalle del evento (todo el resto, avisos para quien se anota y de
        // moderacion, si lo abre).
        final Set<InboxMessageType> result = Arrays.stream(InboxMessageType.values())
                .filter(type -> type.getTarget() != InboxMessageType.Target.EVENT).collect(Collectors.toSet());

        // Assert
        assertEquals(EnumSet.of(InboxMessageType.NEW_REGISTRATION, InboxMessageType.REGISTRATION_CANCELLED,
                InboxMessageType.NEW_PENDING, InboxMessageType.EVENT_TOMORROW), result);
    }
}
