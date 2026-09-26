// Contrato de servicio para la bandeja de mensajes. Implementacion: InboxServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;

public interface InboxService {

    /**
     * Deja un aviso en la bandeja de ese email. Se llama al lado de cada
     * notificationService.notifyX(...), DENTRO de la misma transaccion: si la
     * accion hace rollback, el aviso tampoco queda (igual que el mail, que sale
     * recien despues del COMMIT). eventId, actorName y body pueden ser null.
     */
    void add(String recipientEmail, InboxMessageType type, Long eventId, String actorName, String body);

    /**
     * Una pagina de la bandeja de ese usuario logueado, mas nuevos primero. Una
     * pagina invalida se toma como la 1. Por id y no por email: es la propia
     * bandeja de quien pregunta, no la de un tercero que puede no tener cuenta
     * (para eso esta add(), que si recibe un email).
     */
    PageResult<InboxMessage> findPage(long userId, int page);

    long countUnread(long userId);

    /** Al abrir la bandeja se marcan todos como leidos. */
    void markAllRead(long userId);
}
