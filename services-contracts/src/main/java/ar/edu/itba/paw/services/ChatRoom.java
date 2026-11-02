// La sala de chat de un evento lista para mostrarse: el evento, los ultimos mensajes y si el chat
// esta cerrado (solo lectura). Lo devuelve EventChatService.openRoom().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.ChatMessage;
import ar.edu.itba.paw.model.Event;

import java.util.List;

public class ChatRoom {

    private final Event event;
    private final List<ChatMessage> messages;
    private final boolean closed;

    public ChatRoom(final Event event, final List<ChatMessage> messages, final boolean closed) {
        this.event = event;
        this.messages = messages;
        this.closed = closed;
    }

    public Event getEvent() { return event; }
    public List<ChatMessage> getMessages() { return messages; }

    // Cancelado o ya pasado: se puede leer, no escribir.
    public boolean isClosed() { return closed; }

    // Id del ultimo mensaje (0 si no hay): desde ahi el JS pide los nuevos.
    public long getLastId() {
        return messages.isEmpty() ? 0 : messages.get(messages.size() - 1).getId();
    }
}
