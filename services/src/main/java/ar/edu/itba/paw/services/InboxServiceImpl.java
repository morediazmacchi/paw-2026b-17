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
    private final UserService userService;

    @Autowired
    public InboxServiceImpl(final InboxMessageDao inboxMessageDao, final UserService userService) {
        this.inboxMessageDao = inboxMessageDao;
        this.userService = userService;
    }

    // Mismo criterio que el resto de la app: en la base los emails van en minuscula.
    private static String normalize(final String email) {
        return email.trim().toLowerCase();
    }

    @Override
    @Transactional
    public void add(final String recipientEmail, final InboxMessageType type, final Long eventId,
                    final String actorName, final String body) {
        final String normalizedEmail = normalize(recipientEmail);
        final InboxMessage message = new InboxMessage(normalizedEmail, type, eventId, actorName, body);
        // Optimista (Grupo B, ver schema.sql): si el destinatario ya tiene
        // cuenta, se enlaza de una. Si no, queda en null y se reconcilia solo
        // cuando se registre (ver UserServiceImpl.register()).
        userService.findByEmail(normalizedEmail).ifPresent(recipient -> message.setUserId(recipient.getId()));
        inboxMessageDao.create(message);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InboxMessage> findPage(final long userId, final int page) {
        final int safePage = Math.max(page, 1);
        return new PageResult<>(inboxMessageDao.findByRecipientUserId(userId, safePage, PAGE_SIZE),
                inboxMessageDao.countByUserId(userId), safePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(final long userId) {
        return inboxMessageDao.countUnreadByUserId(userId);
    }

    @Override
    @Transactional
    public void markAllRead(final long userId) {
        inboxMessageDao.markAllReadByUserId(userId, new Date());
    }
}
