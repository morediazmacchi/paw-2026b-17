// Contrato de servicio para seguir organizadores. Implementacion: FollowServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface FollowService {

    /**
     * Sigue a ese organizador: a partir de ahora, cuando publique un evento nuevo y quede aprobado,
     * le llega un aviso (mail + bandeja, ver EventServiceImpl.approve()). Idempotente: si ya lo
     * seguia, no hace nada.
     */
    @Transactional(rollbackFor = Exception.class)
    FollowResult follow(long followerId, long followedId);

    @Transactional(rollbackFor = Exception.class)
    boolean unfollow(long followerId, long followedId);

    @Transactional(readOnly = true)
    boolean isFollowing(long followerId, long followedId);

    /** A quienes avisar cuando este usuario publique un evento nuevo (ver EventServiceImpl.approve()). */
    @Transactional(readOnly = true)
    List<User> findFollowers(long followedId);

    /** Cuantos lo siguen (el contador de "Mi perfil"). */
    @Transactional(readOnly = true)
    long countFollowers(long followedId);
}
