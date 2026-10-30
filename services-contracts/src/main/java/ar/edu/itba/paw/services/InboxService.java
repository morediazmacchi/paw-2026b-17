// Contrato de servicio para la bandeja de mensajes. Implementacion: InboxServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;

import java.util.List;

public interface InboxService {

    /**
     * Deja un aviso en la bandeja de ese email. Se llama al lado de cada
     * notificationService.notifyX(...), DENTRO de la misma transaccion: si la
     * accion hace rollback, el aviso tampoco queda (igual que el mail, que sale
     * recien despues del COMMIT). eventId, actorName y body pueden ser null.
     */
    InboxMessage add(String recipientEmail, InboxMessageType type, Long eventId, String actorName, String body);

    /**
     * Una pagina de la bandeja de ese usuario logueado, mas nuevos primero. Una
     * pagina invalida se toma como la 1. Por id y no por email: es la propia
     * bandeja de quien pregunta, no la de un tercero que puede no tener cuenta
     * (para eso esta add(), que si recibe un email).
     */
    PageResult<InboxMessage> findPage(long userId, int page);

    long countUnread(long userId);

    /**
     * Marca como leidos los mensajes que el usuario tenia en pantalla (los ids de la pagina de
     * la bandeja que estaba viendo), y solo esos: abrir la bandeja no marca nada, y lo que no se
     * mostro sigue sin leer. Los ids que no son de ese usuario se ignoran. Devuelve cuantos marco.
     */
    int markRead(long userId, List<Long> ids);
}
