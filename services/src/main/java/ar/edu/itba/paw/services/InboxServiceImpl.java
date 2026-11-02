// Implementa el contrato InboxService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InboxServiceImpl implements InboxService {

    static final int PAGE_SIZE = 20;

    private final InboxMessageDao inboxMessageDao;
    private final UserService userService;
    private final EventDao eventDao;

    @Autowired
    public InboxServiceImpl(final InboxMessageDao inboxMessageDao, final UserService userService,
                            final EventDao eventDao) {
        this.eventDao = eventDao;
        this.inboxMessageDao = inboxMessageDao;
        this.userService = userService;
    }

    // Mismo criterio que el resto de la app: en la base los emails van en minuscula.
    private static String normalize(final String email) {
        return email.trim().toLowerCase();
    }

    @Override
    @Transactional
    public InboxMessage add(final String recipientEmail, final InboxMessageType type, final Long eventId,
                            final String actorName, final String body) {
        final String normalizedEmail = normalize(recipientEmail);
        // El aviso se relaciona con el evento (objeto, no solo el numero). Dentro de la misma
        // transaccion el evento casi siempre ya esta cargado, asi que findById no va a la base.
        final Event event = eventId == null ? null : eventDao.findById(eventId).orElse(null);
        final InboxMessage message = new InboxMessage(normalizedEmail, type, event, actorName, body);
        // Optimista (Grupo B, ver schema.sql): si el destinatario ya tiene
        // cuenta, se enlaza de una. Si no, queda en null y se reconcilia solo
        // cuando se registre (ver UserServiceImpl.register()).
        userService.findByEmail(normalizedEmail).ifPresent(message::setUser);
        return inboxMessageDao.create(message);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InboxMessage> findPage(final long userId, final int page) {
        final long totalElements = inboxMessageDao.countByUserId(userId);
        final int totalPages = (int) Math.ceil((double) totalElements / PAGE_SIZE);
        final int effectivePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
        return new PageResult<>(inboxMessageDao.findByRecipientUserId(userId, effectivePage, PAGE_SIZE),
                totalElements, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public InboxPage findPageWithEvents(final long userId, final int page) {
        final PageResult<InboxMessage> messages = findPage(userId, page);
        final List<Long> eventIds = messages.getContent().stream()
                .map(InboxMessage::getEventId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        final Map<Long, Event> eventsById = eventDao.findByIds(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        return new InboxPage(messages, eventsById);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(final long userId) {
        return inboxMessageDao.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public int markRead(final long userId, final List<Long> ids) {
        // Sin ids no hay nada que marcar (y un "IN ()" vacio no es una consulta valida).
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return inboxMessageDao.markReadByIds(userId, ids, new Date());
    }
}
