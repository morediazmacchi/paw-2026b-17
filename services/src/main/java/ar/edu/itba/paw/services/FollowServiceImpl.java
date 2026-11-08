// Implementa el contrato FollowService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.FollowDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    private final FollowDao followDao;

    @Autowired
    public FollowServiceImpl(final FollowDao followDao) {
        this.followDao = followDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResult follow(final long followerId, final long followedId) {
        if (followerId == followedId) {
            return FollowResult.OWN_PROFILE;
        }
        followDao.follow(followerId, followedId);
        return FollowResult.OK;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollow(final long followerId, final long followedId) {
        return followDao.unfollow(followerId, followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(final long followerId, final long followedId) {
        return followDao.isFollowing(followerId, followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findFollowers(final long followedId) {
        return followDao.findFollowers(followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowers(final long followedId) {
        return followDao.countFollowers(followedId);
    }
}
