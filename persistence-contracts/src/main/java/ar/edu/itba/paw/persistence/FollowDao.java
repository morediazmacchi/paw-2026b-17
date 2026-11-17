// Contrato de persistencia para seguir organizadores. Implementacion: FollowJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.FollowerContact;

import java.util.List;

public interface FollowDao {
    // true si se agrego; false si ya lo seguia.
    boolean follow(long followerId, long followedId);
    // true si estaba y se saco.
    boolean unfollow(long followerId, long followedId);
    boolean isFollowing(long followerId, long followedId);

    // Quienes siguen a este usuario, para mandarles el mail cuando publica un evento nuevo (ver
    // EventServiceImpl.approve(); el aviso de la bandeja sale aparte, con InboxMessageDao.createForFollowersOfCreator).
    // Solo mail, nombre e idioma (proyeccion): no carga las cuentas enteras. Sin paginar a proposito: el mail
    // tiene que salir para todos en la misma pasada, no de a paginas.
    List<FollowerContact> findFollowers(long followedId);

    // Cuantos lo siguen (el contador de "Mi perfil"): un COUNT, sin traer las filas.
    long countFollowers(long followedId);
}
