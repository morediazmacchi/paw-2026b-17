// Implementa el contrato EventChatService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventChatDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EventChatServiceImpl implements EventChatService {

    static final int INITIAL_MESSAGES = 100;
    static final int MAX_NEW_MESSAGES = 200;

    private final EventChatDao chatDao;
    private final EventService eventService;
    private final RegistrationService registrationService;

    @Autowired
    public EventChatServiceImpl(final EventChatDao chatDao, final EventService eventService,
                                final RegistrationService registrationService) {
        this.chatDao = chatDao;
        this.eventService = eventService;
        this.registrationService = registrationService;
    }

    // Cancelado (o dado de baja por un admin) o ya pasado: se puede leer, no escribir.
    private static boolean isClosed(final Event event) {
        return event.isCancelled() || event.isFinished();
    }

    @Override
    @Transactional
    public ChatJoinResult join(final long eventId, final User user) {
        final Event event = eventService.findById(eventId).orElse(null);
        if (event == null) {
            return ChatJoinResult.NOT_ALLOWED;
        }
        final Registration registration = registrationService.findByEventAndUserId(eventId, user.getId()).orElse(null);
        if (registration == null || registration.getStatus() != RegistrationStatus.CONFIRMED) {
            return ChatJoinResult.NOT_ALLOWED;
        }
        if (chatDao.isMember(eventId, user.getId())) {
            return ChatJoinResult.ALREADY_MEMBER;
        }
        if (isClosed(event)) {
            return ChatJoinResult.CLOSED;
        }
        if (chatDao.addMember(registration.getId(), eventId, user.getId())) {
            return ChatJoinResult.JOINED;
        }
        // Otro "Unirme" casi simultaneo gano: su insert fallo y dejo esta transaccion marcada
        // solo-rollback; no escribimos nada mas, asi que la descartamos sin quejas.
        RollbackOnly.ifInTransaction();
        return ChatJoinResult.ALREADY_MEMBER;
    }

    @Override
    @Transactional
    public boolean leave(final long eventId, final long userId) {
        return chatDao.removeMember(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isMember(final long eventId, final long userId) {
        return chatDao.isMember(eventId, userId);
    }

    @Override
    @Transactional
    public Optional<List<ChatMessage>> findMessages(final long eventId, final long userId, final long afterId) {
        if (!chatDao.isMember(eventId, userId)) {
            return Optional.empty();
        }
        final List<ChatMessage> messages = afterId <= 0
                ? chatDao.findLatest(eventId, INITIAL_MESSAGES)
                : chatDao.findAfter(eventId, afterId, MAX_NEW_MESSAGES);
        if (afterId <= 0 || !messages.isEmpty()) {
            chatDao.markRead(eventId, userId);
        }
        return Optional.of(messages);
    }

    @Override
    @Transactional
    public ChatSendResult send(final long eventId, final User user, final String text) {
        final Event event = eventService.findById(eventId).orElse(null);
        if (event == null || !chatDao.isMember(eventId, user.getId())) {
            return ChatSendResult.NOT_ALLOWED;
        }
        if (isClosed(event)) {
            return ChatSendResult.CLOSED;
        }
        final String body = text == null ? "" : text.trim();
        if (body.isEmpty() || body.length() > MAX_MESSAGE_LENGTH) {
            return ChatSendResult.INVALID;
        }
        chatDao.addMessage(new ChatMessage(eventId, user.getId(), body));
        // Lo que uno manda ya lo vio: que no le aparezca como "sin leer" a si mismo.
        chatDao.markRead(eventId, user.getId());
        return ChatSendResult.SENT;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatGroup> findGroups(final long userId) {
        return chatDao.findGroupsByUser(userId);
    }
}
