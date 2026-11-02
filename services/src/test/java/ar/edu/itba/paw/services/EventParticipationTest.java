package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.RegistrationStatus;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventParticipationTest {

    private static EventParticipation withStatus(final RegistrationStatus status) {
        return new EventParticipation(status, false, true);
    }

    @Test
    public void testWithoutRegistrationNothingIsSet() {
        final EventParticipation participation = withStatus(null);

        assertFalse(participation.isRegistered());
        assertFalse(participation.isWaitlisted());
        assertFalse(participation.isPendingApproval());
        assertFalse(participation.isRejected());
        assertFalse(participation.isChatConfirmed());
    }

    @Test
    public void testAConfirmedRegistrationIsRegisteredAndOffersTheChat() {
        final EventParticipation participation = withStatus(RegistrationStatus.CONFIRMED);

        assertTrue(participation.isRegistered());
        assertTrue(participation.isChatConfirmed());
    }

    @Test
    public void testARejectedRegistrationIsNotRegistered() {
        final EventParticipation participation = withStatus(RegistrationStatus.REJECTED);

        assertFalse(participation.isRegistered());
        assertTrue(participation.isRejected());
    }

    @Test
    public void testTheOwnerAndCanRegisterFlagsAreKeptAsGiven() {
        final EventParticipation owner = new EventParticipation(null, true, false);
        final EventParticipation visitor = new EventParticipation(null, false, true);

        assertTrue(owner.isOwner());
        assertFalse(owner.canRegister());
        assertFalse(visitor.isOwner());
        assertTrue(visitor.canRegister());
    }
}
