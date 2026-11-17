// Contrato de persistencia para la bandeja. Implementacion: InboxMessageJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface InboxMessageDao {

    /**
     * Deja un aviso para ese mail (ya normalizado). El evento se enlaza por su id (null en los avisos que no
     * hablan de uno) y, si ya hay una cuenta con ese mail, el aviso queda enlazado a ella; si no, queda sin
     * cuenta hasta que esa persona se registre (ver linkUserId()).
     */
    InboxMessage create(String recipientEmail, InboxMessageType type, Long eventId, String actorName, String body);

    /**
     * El mismo aviso (sin actor ni texto) a cada inscripto de ese evento cuyo estado este en statuses, en UNA
     * sola sentencia (INSERT ... SELECT): no trae las inscripciones ni busca la cuenta de cada uno. Cada aviso
     * queda enlazado a la cuenta con ese mail, si la hay (mismo criterio que create() desde el service).
     * statuses no puede estar vacio. Devuelve cuantos avisos dejo.
     */
    int createForRegistrants(long eventId, InboxMessageType type, Collection<RegistrationStatus> statuses);

    /**
     * El mismo aviso a cada inscripcion de la lista (por id), en UNA sola sentencia: la lista exacta, no un estado
     * (ver createForRegistrants()), para cuando lo que cambia de estado son unas inscripciones puntuales y no todas
     * las que estan en cierto estado. El evento de cada aviso es el de su inscripcion. Cada aviso queda enlazado a
     * la cuenta con ese mail, si la hay. registrationIds no puede estar vacio. Devuelve cuantos avisos dejo.
     */
    int createForRegistrations(InboxMessageType type, Collection<Long> registrationIds);

    /**
     * El mismo aviso, para el ORGANIZADOR del evento, por cada inscripcion de la lista (por id): una fila por
     * inscripcion, con el nombre de esa persona como actor ("Ana se anoto"; su mail si no tiene nombre, igual que
     * Registration.getDisplayName()). En UNA sola sentencia. El destinatario es el mail del organizador del evento
     * de cada inscripcion, enlazado a su cuenta si la hay. registrationIds no puede estar vacio. Devuelve cuantos
     * avisos dejo.
     */
    int createForOrganizerOfRegistrations(InboxMessageType type, Collection<Long> registrationIds);

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
