// Grupo de chat de un evento: lo forman quienes tienen su inscripcion confirmada y
// eligen unirse. Implementacion: EventChatServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.User;

import java.util.List;

public interface EventChatService {

    int MAX_MESSAGE_LENGTH = 1000;

    ChatJoinResult join(long eventId, User user);

    // Salir por voluntad propia. false si no estaba en el grupo.
    boolean leave(long eventId, long userId);

    // Miembro activo: se unio y su inscripcion sigue confirmada. Es la unica regla de
    // acceso al chat (leer y escribir): si lo sacan del evento, deja de serlo solo.
    boolean isMember(long eventId, long userId);

    // Los mensajes del grupo: con afterId <= 0 los ultimos que haya, y si no los
    // posteriores a afterId. Marca el chat como leido para esa persona. Que sea miembro lo decide
    // @PreAuthorize en el controller (ver EventSecurity.isChatMember).
    List<ChatMessage> findMessages(long eventId, long userId, long afterId);

    ChatSendResult send(long eventId, User user, String text);

    // Para los circulos de la bandeja.
    List<ChatGroup> findGroups(long userId);
}
