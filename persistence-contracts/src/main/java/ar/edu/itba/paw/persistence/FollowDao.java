// Contrato de persistencia para seguir organizadores. Implementacion: FollowJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.FollowerContact;

import java.util.List;

public interface FollowDao {
    // Sigue al organizador de ese evento (el organizador sale del evento, no de un id que mande el cliente).
    // true si se agrego; false si ya lo seguia o si el evento no existe / no tiene organizador cargado (eventos viejos).
    boolean followOrganizerOf(long followerId, long eventId);
    // Deja de seguir al organizador de ese evento. true si lo seguia y se saco.
    boolean unfollowOrganizerOf(long followerId, long eventId);
    boolean isFollowing(long followerId, long followedId);

    // Una tanda de quienes siguen a este usuario, para mandarles el mail cuando publica un evento nuevo (ver
    // MassNotifier; el aviso de la bandeja sale aparte, con InboxMessageDao.createForFollowersOfCreator): los de id
    // mayor a "afterId", de menor a mayor id, hasta "limit". Se recorre de a tandas, en segundo plano y despues del
    // commit, nunca todos de golpe. Solo mail, nombre e idioma (proyeccion): no carga las cuentas enteras.
    List<FollowerContact> findFollowersAfter(long followedId, long afterId, int limit);

    // Cuantos lo siguen (el contador de "Mi perfil"): un COUNT, sin traer las filas.
    long countFollowers(long followedId);
}
