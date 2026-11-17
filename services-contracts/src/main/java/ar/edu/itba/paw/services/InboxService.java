// Contrato de servicio para la bandeja de mensajes. Implementacion: InboxServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.List;
import java.util.Set;

public interface InboxService {

    /**
     * Deja un aviso en la bandeja de ese email. Se llama al lado de cada
     * notificationService.notifyX(...), DENTRO de la misma transaccion: si la
     * accion hace rollback, el aviso tampoco queda (igual que el mail, que sale
     * recien despues del COMMIT). eventId, actorName y body pueden ser null.
     */
    InboxMessage add(String recipientEmail, InboxMessageType type, Long eventId, String actorName, String body);

    /**
     * El mismo aviso a cada inscripto de ese evento cuyo estado este en statuses (por ejemplo "se cancelo el
     * evento" a todos menos los rechazados), en una sola sentencia: no busca la cuenta de cada uno por separado
     * como add(). Va dentro de la transaccion de quien llama, igual que add(). Devuelve cuantos avisos dejo
     * (0 si statuses esta vacio).
     */
    int addForRegistrants(long eventId, InboxMessageType type, Set<RegistrationStatus> statuses);

    /**
     * El mismo aviso a cada persona que sigue al organizador de ese evento, con el nombre del organizador como
     * actor. Una sola sentencia, como addForRegistrants(). Devuelve cuantos avisos dejo.
     */
    int addForFollowersOfCreator(long eventId, InboxMessageType type);

    /**
     * Una pagina de la bandeja de ese usuario logueado, mas nuevos primero. Una
     * pagina invalida se toma como la 1. Por id y no por email: es la propia
     * bandeja de quien pregunta, no la de un tercero que puede no tener cuenta
     * (para eso esta add(), que si recibe un email).
     */
    PageResult<InboxMessage> findPage(long userId, int page);

    /**
     * Como findPage(), pero con los eventos a los que se refieren los avisos de esa pagina (una sola
     * consulta para todos, no una por aviso).
     */
    InboxPage findPageWithEvents(long userId, int page);

    long countUnread(long userId);

    /**
     * Marca como leidos los mensajes que el usuario tenia en pantalla (los ids de la pagina de
     * la bandeja que estaba viendo), y solo esos: abrir la bandeja no marca nada, y lo que no se
     * mostro sigue sin leer. Los ids que no son de ese usuario se ignoran. Devuelve cuantos marco.
     */
    int markRead(long userId, List<Long> ids);
}
