// Implementa el contrato FollowService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.FollowerContact;
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
    public boolean followOrganizerOf(final long eventId, final long followerId) {
        return followDao.followOrganizerOf(followerId, eventId);
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
