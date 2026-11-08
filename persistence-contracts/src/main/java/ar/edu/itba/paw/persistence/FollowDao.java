// Contrato de persistencia para seguir organizadores. Implementacion: FollowJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.User;

import java.util.List;

public interface FollowDao {
    // true si se agrego; false si ya lo seguia.
    boolean follow(long followerId, long followedId);
    // true si estaba y se saco.
    boolean unfollow(long followerId, long followedId);
    boolean isFollowing(long followerId, long followedId);

    // Quienes siguen a este usuario (ver EventServiceImpl.approve(): a cada uno se le avisa cuando
    // publica un evento nuevo). Sin paginar a proposito: son pocos en la escala de esta app, y el
    // aviso tiene que salir para todos en la misma pasada, no de a paginas.
    List<User> findFollowers(long followedId);

    // Cuantos lo siguen (el contador de "Mi perfil"): un COUNT, sin traer las filas.
    long countFollowers(long followedId);
}
