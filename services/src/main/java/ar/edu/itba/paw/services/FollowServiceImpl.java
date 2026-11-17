// Implementa el contrato FollowService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.FollowerContact;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.FollowDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FollowServiceImpl implements FollowService {

    private final FollowDao followDao;
    // EventDao y no EventService: EventServiceImpl ya depende de FollowService (avisa a los seguidores al
    // aprobar), y al reves seria un ciclo de constructores.
    private final EventDao eventDao;

    @Autowired
    public FollowServiceImpl(final FollowDao followDao, final EventDao eventDao) {
        this.followDao = followDao;
        this.eventDao = eventDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean followOrganizerOf(final long eventId, final long followerId) {
        return organizerOf(eventId)
                .map(organizerId -> followDao.follow(followerId, organizerId))
                .orElse(false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unfollowOrganizerOf(final long eventId, final long followerId) {
        return organizerOf(eventId)
                .map(organizerId -> followDao.unfollow(followerId, organizerId))
                .orElse(false);
    }

    // El organizador del evento, con la lectura acotada (4 columnas, sin cargar el Event). Vacio si el evento
    // no existe o no tiene organizador cargado.
    private Optional<Long> organizerOf(final long eventId) {
        return eventDao.findAccessById(eventId).map(EventAccessSummary::getCreatorId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(final long followerId, final long followedId) {
        return followDao.isFollowing(followerId, followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowerContact> findFollowers(final long followedId) {
        return followDao.findFollowers(followedId);
    }

    @Override
    @Transactional(readOnly = true)
    public long countFollowers(final long followedId) {
        return followDao.countFollowers(followedId);
    }
}
