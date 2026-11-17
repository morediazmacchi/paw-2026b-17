// Contrato de persistencia para la bandeja. Implementacion: InboxMessageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface InboxMessageDao {

    InboxMessage create(InboxMessage message);

    /**
     * El mismo aviso (sin actor ni texto) a cada inscripto de ese evento cuyo estado este en statuses, en UNA
     * sola sentencia (INSERT ... SELECT): no trae las inscripciones ni busca la cuenta de cada uno. Cada aviso
     * queda enlazado a la cuenta con ese mail, si la hay (mismo criterio que create() desde el service).
     * statuses no puede estar vacio. Devuelve cuantos avisos dejo.
     */
    int createForRegistrants(long eventId, InboxMessageType type, Collection<RegistrationStatus> statuses);

    /**
     * El mismo aviso a cada persona que sigue al organizador de ese evento, con el nombre del organizador como
     * actor ("Ana publico un evento nuevo"). Una sola sentencia, como createForRegistrants(). Un evento sin
     * organizador cargado (eventos viejos) no deja ninguno. Devuelve cuantos avisos dejo.
     */
    int createForFollowersOfCreator(long eventId, InboxMessageType type);

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
