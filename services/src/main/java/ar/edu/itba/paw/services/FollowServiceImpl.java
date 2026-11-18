// Implementa el contrato FollowService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.persistence.FollowDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    private final FollowDao followDao;
    // Para no seguir una cuenta que no se puede mostrar (misma regla que su perfil publico).
    private final UserService userService;

    @Autowired
    public FollowServiceImpl(final FollowDao followDao, final UserService userService) {
        this.followDao = followDao;
        this.userService = userService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean followOrganizerOf(final long eventId, final long followerId) {
        try {
            return followDao.followOrganizerOf(followerId, eventId);
        } catch (final DataIntegrityViolationException e) {
            // Dos "seguir" simultaneos de la misma persona: los dos pasaron el chequeo del DAO ("ya lo sigue?") y el
            // segundo choco contra la clave (follower_id, followed_id) al insertar. Para quien lo pidio es lo mismo que
            // "ya lo seguia": false. Cualquier otra violacion es un error de verdad y no se disfraza. Mismo criterio
            // que ReviewServiceImpl.create() (ver RollbackOnly).
            if (!UniqueViolation.isUniqueViolation(e)) {
                throw e;
            }
            RollbackOnly.ifInTransaction();
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean follow(final long followerId, final long followedId) {
        // Uno no se sigue a si mismo, y a una cuenta que no se puede mostrar (no existe, suspendida, sin confirmar)
        // tampoco: su perfil publico da 404.
        if (followerId == followedId || !userService.findActivePublicById(followedId).isPresent()) {
            return false;
        }
        try {
            return followDao.follow(followerId, followedId);
        } catch (final DataIntegrityViolationException e) {
            // Dos "seguir" simultaneos: mismo criterio que followOrganizerOf().
            if (!UniqueViolation.isUniqueViolation(e)) {
                throw e;
            }
            RollbackOnly.ifInTransaction();
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollow(final long followerId, final long followedId) {
        return followDao.unfollow(followerId, followedId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollowOrganizerOf(final long eventId, final long followerId) {
        return followDao.unfollowOrganizerOf(followerId, eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(final long followerId, final long followedId) {
        return followDao.isFollowing(followerId, followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowerContact> findFollowersAfter(final long followedId, final long afterId, final int limit) {
        return followDao.findFollowersAfter(followedId, afterId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowers(final long followedId) {
        return followDao.countFollowers(followedId);
    }
}
