package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.EventQuestion;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.User;
import java.util.ArrayList;
import java.util.List;

/**
 * NotificationService "de mentira" para los tests de services: en vez de mandar mails
 * anota cada aviso que le piden (nombre del metodo + argumentos), en orden.
 * El test despues hace assert sobre esa lista. Es un fake escrito a mano (ver clase de
 * testing, Sotuyo): sin Mockito.
 */
public class RecordingNotificationService implements NotificationService {

    private final List<Object[]> sent = new ArrayList<>();

    @Override
    public void notifyEventCreated(final Event event, final String creatorName) {
        record("notifyEventCreated", event, creatorName);
    }

    @Override
    public void notifyRegistrationConfirmed(final Event event, final Registration registration) {
        record("notifyRegistrationConfirmed", event, registration);
    }

    @Override
    public void notifyNewRegistration(final Event event, final Registration registration) {
        record("notifyNewRegistration", event, registration);
    }

    @Override
    public void notifyWaitlistJoined(final Event event, final Registration registration) {
        record("notifyWaitlistJoined", event, registration);
    }

    @Override
    public void notifyNewWaitlistEntry(final Event event, final Registration registration) {
        record("notifyNewWaitlistEntry", event, registration);
    }

    @Override
    public void notifyEventFull(final Event event) {
        record("notifyEventFull", event);
    }

    @Override
    public void notifyRegistrationCancelled(final Event event, final Registration registration, final boolean offeredToWaitlist) {
        record("notifyRegistrationCancelled", event, registration, offeredToWaitlist);
    }

    @Override
    public void notifyWaitlistPromoted(final Event event, final Registration registration) {
        record("notifyWaitlistPromoted", event, registration);
    }

    @Override
    public void notifyRegistrationRevoked(final Event event, final Registration registration) {
        record("notifyRegistrationRevoked", event, registration);
    }

    @Override
    public void notifyAccountVerification(final User user, final String token, final String redirectAfterConfirm) {
        record("notifyAccountVerification", user, token, redirectAfterConfirm);
    }

    @Override
    public void notifyPasswordReset(final User user, final String token) {
        record("notifyPasswordReset", user, token);
    }

    @Override
    public void notifyRegistrationPending(final Event event, final Registration registration) {
        record("notifyRegistrationPending", event, registration);
    }

    @Override
    public void notifyNewPendingRegistration(final Event event, final Registration registration) {
        record("notifyNewPendingRegistration", event, registration);
    }

    @Override
    public void notifyRegistrationApproved(final Event event, final Registration registration) {
        record("notifyRegistrationApproved", event, registration);
    }

    @Override
    public void notifyRegistrationRejected(final Event event, final Registration registration) {
        record("notifyRegistrationRejected", event, registration);
    }

    @Override
    public void notifyEventInvitation(final Event event, final EventInvitation invitation) {
        record("notifyEventInvitation", event, invitation);
    }

    @Override
    public void notifyEventCancelled(final Event event, final Registration registration) {
        record("notifyEventCancelled", event, registration);
    }

    @Override
    public void notifyEventUpdated(final Event event, final Registration registration) {
        record("notifyEventUpdated", event, registration);
    }

    @Override
    public void notifyEventReminder(final Event event, final Registration registration) {
        record("notifyEventReminder", event, registration);
    }

    @Override
    public void notifyOrganizerEventReminder(final Event event) {
        record("notifyOrganizerEventReminder", event);
    }

    @Override
    public void notifyNewQuestion(final Event event, final EventQuestion question) {
        record("notifyNewQuestion", event, question);
    }

    @Override
    public void notifyQuestionAnswered(final Event event, final EventQuestion question) {
        record("notifyQuestionAnswered", event, question);
    }

    @Override
    public void notifyEventRemovedByAdmin(final Event event, final String reason) {
        record("notifyEventRemovedByAdmin", event, reason);
    }

    @Override
    public void notifyEventChangesRequested(final Event event, final String message) {
        record("notifyEventChangesRequested", event, message);
    }

    @Override
    public void notifyUserSuspended(final User user) {
        record("notifyUserSuspended", user);
    }

    @Override
    public void notifyUserReactivated(final User user) {
        record("notifyUserReactivated", user);
    }

    private void record(final String method, final Object... args) {
        final Object[] entry = new Object[args.length + 1];
        entry[0] = method;
        System.arraycopy(args, 0, entry, 1, args.length);
        sent.add(entry);
    }

    /** Cuantas veces se pidio ese aviso. */
    public int count(final String method) {
        return argsOf(method).size();
    }

    /** Argumentos de cada vez que se pidio ese aviso, en orden de llamada. */
    public List<Object[]> argsOf(final String method) {
        final List<Object[]> result = new ArrayList<>();
        for (final Object[] entry : sent) {
            if (entry[0].equals(method)) {
                final Object[] args = new Object[entry.length - 1];
                System.arraycopy(entry, 1, args, 0, args.length);
                result.add(args);
            }
        }
        return result;
    }

    /** Todos los avisos pedidos (solo el nombre), en orden. */
    public List<String> methods() {
        final List<String> result = new ArrayList<>();
        for (final Object[] entry : sent) {
            result.add((String) entry[0]);
        }
        return result;
    }
}
