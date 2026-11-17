// Implementa el contrato InboxService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class InboxServiceImpl implements InboxService {

    static final int PAGE_SIZE = 20;

    private final InboxMessageDao inboxMessageDao;

    @Autowired
    public InboxServiceImpl(final InboxMessageDao inboxMessageDao) {
        this.inboxMessageDao = inboxMessageDao;
    }

    // Mismo criterio que el resto de la app: en la base los emails van en minuscula.
    private static String normalize(final String email) {
        return email.trim().toLowerCase();
    }

    @Override
    @Transactional
    public InboxMessage add(final String recipientEmail, final InboxMessageType type, final Long eventId,
                            final String actorName, final String body) {
        // El DAO enlaza el aviso con el evento (por id) y, si el destinatario ya tiene cuenta, con ella; si no, queda
        // sin cuenta y se reconcilia cuando se registre (ver linkAccount()).
        return inboxMessageDao.create(normalize(recipientEmail), type, eventId, actorName, body);
    }

    @Override
    @Transactional
    public int addForRegistrants(final long eventId, final InboxMessageType type,
                                 final Set<RegistrationStatus> statuses) {
        // Sin estados no hay a quien avisar (y un "IN ()" vacio no es una consulta valida).
        if (statuses == null || statuses.isEmpty()) {
            return 0;
        }
        return inboxMessageDao.createForRegistrants(eventId, type, statuses);
    }

    @Override
    @Transactional
    public int addForRegistrations(final InboxMessageType type, final List<Long> registrationIds) {
        // Sin ids no hay a quien avisar (y un "IN ()" vacio no es una consulta valida).
        if (registrationIds == null || registrationIds.isEmpty()) {
            return 0;
        }
        return inboxMessageDao.createForRegistrations(type, registrationIds);
    }

    @Override
    @Transactional
    public int addForOrganizerOfRegistrations(final InboxMessageType type, final List<Long> registrationIds) {
        // Sin ids no hay nada que avisar (y un "IN ()" vacio no es una consulta valida).
        if (registrationIds == null || registrationIds.isEmpty()) {
            return 0;
        }
        return inboxMessageDao.createForOrganizerOfRegistrations(type, registrationIds);
    }

    @Override
    @Transactional
    public int addForFollowersOfCreator(final long eventId, final InboxMessageType type) {
        return inboxMessageDao.createForFollowersOfCreator(eventId, type);
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
        // El evento de cada aviso ya viene con la pagina (el DAO lo trae en la misma consulta): no se busca aparte.
        final Map<Long, Event> eventsById = new HashMap<>();
        for (final InboxMessage message : messages.getContent()) {
            if (message.getEvent() != null) {
                eventsById.put(message.getEventId(), message.getEvent());
            }
        }
        return new InboxPage(messages, eventsById);
    }

    @Override
    @Transactional
    public int linkAccount(final String email, final long userId) {
        return inboxMessageDao.linkUserId(normalize(email), userId);
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
