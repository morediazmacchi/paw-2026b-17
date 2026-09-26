// Contrato de persistencia para la bandeja. Implementacion: InboxMessageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;

import java.util.Date;
import java.util.List;

public interface InboxMessageDao {

    InboxMessage create(InboxMessage message);

    /** Una pagina de la bandeja de ese email, mas nuevos primero. page empieza en 1. */
    List<InboxMessage> findByRecipient(String email, int page, int pageSize);

    long countByRecipient(String email);

    /** Para el numerito de la navbar. */
    long countUnread(String email);

    /** Marca como leidos todos los que estaban sin leer (los ya leidos conservan su fecha). */
    void markAllRead(String email, Date when);

    /**
     * Misma bandeja, pero para quien ya esta logueado: por user_id en vez de
     * por email (no depende de que recipient_email siga siendo el email actual
     * de la cuenta). page empieza en 1.
     */
    List<InboxMessage> findByRecipientUserId(long userId, int page, int pageSize);

    long countByUserId(long userId);

    long countUnreadByUserId(long userId);

    void markAllReadByUserId(long userId, Date when);

    /**
     * Reconciliacion (ver UserServiceImpl.register()): alguien que recibio
     * avisos sin tener cuenta todavia acaba de crearse una. Completa el
     * user_id de los suyos que quedaron sin resolver (user_id NULL).
     */
    void linkUserId(String email, long userId);
}
