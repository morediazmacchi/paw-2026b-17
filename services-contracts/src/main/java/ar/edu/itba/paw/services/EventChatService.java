// Grupo de chat de un evento: lo forman quienes tienen su inscripcion confirmada y
// eligen unirse. Implementacion: EventChatServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;

import java.util.List;

public interface EventChatService {

    int MAX_MESSAGE_LENGTH = 1000;

    ChatJoinResult join(long eventId, long userId);

    // Salir por voluntad propia. false si no estaba en el grupo.
    boolean leave(long eventId, long userId);

    // Miembro activo: se unio y su inscripcion sigue confirmada. Es la unica regla de
    // acceso al chat (leer y escribir): si lo sacan del evento, deja de serlo solo.
    boolean isMember(long eventId, long userId);

    // Los mensajes del grupo: con afterId <= 0 los ultimos que haya, y si no los posteriores a afterId. Solo lee:
    // marcar el chat como leido es markRead(), un POST aparte (una lectura no escribe). Que sea miembro lo decide
    // @PreAuthorize en el controller (ver EventSecurity.isChatMember).
    List<ChatMessage> findMessages(long eventId, long afterId);

    ChatSendResult send(long eventId, long userId, String text);

    // La pantalla del chat: el evento, los ultimos mensajes y si el chat esta cerrado (cancelado o ya pasado: solo
    // lectura, misma regla que send() y join()). Solo lee (ver findMessages()). Que sea miembro lo decide
    // @PreAuthorize en el controller (ver EventSecurity.isChatMember).
    // Lanza EventNotFoundException si el evento no existe.
    ChatRoom openRoom(long eventId);

    // Marca el chat como leido para esa persona, hasta el ultimo mensaje que hay. Lo pide el JS del chat despues de
    // mostrar mensajes (POST /eventos/{id}/chat/leido). Que sea miembro lo decide @PreAuthorize en el controller.
    void markRead(long eventId, long userId);

    // Para los circulos de la bandeja, de a una pagina. Una pagina mayor a la ultima devuelve la ultima, una menor
    // a 1 la primera; sin grupos, la pagina 1 vacia.
    PageResult<ChatGroup> findGroupsPage(long userId, int page);
}
