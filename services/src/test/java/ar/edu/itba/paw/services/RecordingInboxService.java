package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;

import java.util.ArrayList;
import java.util.List;

/**
 * InboxService "de mentira" para los tests de services: anota cada mensaje que le piden
 * dejar en la bandeja (para quien, de que tipo, sobre que evento), en orden.
 * Fake escrito a mano (solo para los avisos, que son "disparar y olvidar").
 */
public class RecordingInboxService implements InboxService {

    /** Un mensaje que el service pidio dejar en la bandeja. */
    public static final class Added {
        public final String recipientEmail;
        public final InboxMessageType type;
        public final Long eventId;
        public final String actorName;
        public final String body;

        Added(final String recipientEmail, final InboxMessageType type, final Long eventId,
              final String actorName, final String body) {
            this.recipientEmail = recipientEmail;
            this.type = type;
            this.eventId = eventId;
            this.actorName = actorName;
            this.body = body;
        }
    }

    private final List<Added> added = new ArrayList<>();

    @Override
    public InboxMessage add(final String recipientEmail, final InboxMessageType type, final Long eventId,
                            final String actorName, final String body) {
        added.add(new Added(recipientEmail, type, eventId, actorName, body));
        return new InboxMessage(recipientEmail, type, eventId, actorName, body);
    }

    @Override
    public PageResult<InboxMessage> findPage(final long userId, final int page) {
        throw new UnsupportedOperationException("este fake solo anota los add()");
    }

    @Override
    public long countUnread(final long userId) {
        throw new UnsupportedOperationException("este fake solo anota los add()");
    }

    @Override
    public int markAllRead(final long userId) {
        throw new UnsupportedOperationException("este fake solo anota los add()");
    }

    /** Todos los mensajes dejados, en orden. */
    public List<Added> all() {
        return added;
    }

    /** Emails destinatarios, en el orden en que se les escribio. */
    public List<String> recipients() {
        final List<String> result = new ArrayList<>();
        for (final Added message : added) {
            result.add(message.recipientEmail);
        }
        return result;
    }

    /** Los mensajes dejados para ese email. */
    public List<Added> to(final String email) {
        final List<Added> result = new ArrayList<>();
        for (final Added message : added) {
            if (email.equals(message.recipientEmail)) {
                result.add(message);
            }
        }
        return result;
    }
}
