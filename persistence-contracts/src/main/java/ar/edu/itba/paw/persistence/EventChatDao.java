// Contrato de persistencia del grupo de chat de un evento. Implementacion: EventChatJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.ChatMessage;

import java.util.List;

public interface EventChatDao {
    // true solo si ESTA llamada lo agrego: si ya era miembro devuelve false.
    // La membresia cuelga de la inscripcion (registrationId): si esa inscripcion se
    // borra, la membresia desaparece sola.
    boolean addMember(long registrationId, long eventId, long userId);

    boolean removeMember(long eventId, long userId);

    // Miembro "activo": tiene fila en el grupo Y su inscripcion sigue CONFIRMED.
    // Si lo rechazaron o se dio de baja, deja de serlo sin tocar nada mas.
    boolean isMember(long eventId, long userId);

    ChatMessage addMessage(long eventId, long userId, String body);

    // Los ultimos `limit` mensajes, del mas viejo al mas nuevo.
    List<ChatMessage> findLatest(long eventId, int limit);

    // Los posteriores a afterId (exclusivo), del mas viejo al mas nuevo.
    List<ChatMessage> findAfter(long eventId, long afterId, int limit);

    // Marca como leido todo lo que hay hasta ahora en ese grupo para esa persona.
    void markRead(long eventId, long userId);

    // Los grupos activos de una persona: primero los de actividad mas reciente.
    List<ChatGroup> findGroupsByUser(long userId);
}
