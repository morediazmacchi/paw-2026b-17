// Contrato de persistencia para la bandeja. Implementacion: InboxMessageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;

import java.util.Date;
import java.util.List;

public interface InboxMessageDao {

    InboxMessage create(InboxMessage message);

    /**
     * Misma bandeja, pero para quien ya esta logueado: por user_id en vez de
     * por email (no depende de que recipient_email siga siendo el email actual
     * de la cuenta). page empieza en 1.
     */
    List<InboxMessage> findByRecipientUserId(long userId, int page, int pageSize);

    long countByUserId(long userId);

    long countUnreadByUserId(long userId);

    /**
     * Marca como leidos los mensajes de esos ids que sean de ese usuario y sigan sin leer.
     * Un id de un mensaje ajeno (o que no existe) se ignora. Devuelve cuantos marco.
     */
    int markReadByIds(long userId, List<Long> ids, Date when);

    /**
     * Reconciliacion (ver UserServiceImpl.register()): alguien que recibio
     * avisos sin tener cuenta todavia acaba de crearse una. Completa el
     * user_id de los suyos que quedaron sin resolver (user_id NULL).
     */
    int linkUserId(String email, long userId);
}
