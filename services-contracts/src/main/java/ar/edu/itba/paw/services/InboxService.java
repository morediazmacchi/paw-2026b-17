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

    /** Una pagina de la bandeja, mas nuevos primero. Una pagina invalida se toma como la 1. */
    PageResult<InboxMessage> findPage(String email, int page);

    long countUnread(String email);

    /** Al abrir la bandeja se marcan todos como leidos. */
    void markAllRead(String email);
}
