// Implementa el contrato InboxService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.persistence.InboxMessageDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

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
    public void add(final String recipientEmail, final InboxMessageType type, final Long eventId,
                    final String actorName, final String body) {
        inboxMessageDao.create(new InboxMessage(normalize(recipientEmail), type, eventId, actorName, body));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InboxMessage> findPage(final String email, final int page) {
        final String normalized = normalize(email);
        final int safePage = Math.max(page, 1);
        return new PageResult<>(inboxMessageDao.findByRecipient(normalized, safePage, PAGE_SIZE),
                inboxMessageDao.countByRecipient(normalized), safePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(final String email) {
        return inboxMessageDao.countUnread(normalize(email));
    }

    @Override
    @Transactional
    public void markAllRead(final String email) {
        inboxMessageDao.markAllRead(normalize(email), new Date());
    }
}
