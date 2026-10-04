// Implementa el contrato FavoriteService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteDao favoriteDao;
    private final EventAccessService accessChecker;

    @Autowired
    public FavoriteServiceImpl(final FavoriteDao favoriteDao, final EventAccessService accessChecker) {
        this.favoriteDao = favoriteDao;
        this.accessChecker = accessChecker;
    }

    @Override
    @Transactional
    public boolean add(final User user, final long eventId) {
        // Mismo chequeo que el detalle: si no podes ver el evento, tampoco podes
        // guardarlo
        accessChecker.getViewableEvent(eventId, user);
        return favoriteDao.add(user.getId(), eventId);
    }

    @Override
    @Transactional
    public boolean remove(final long userId, final long eventId) {
        return favoriteDao.remove(userId, eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isFavorite(final long userId, final long eventId) {
        return favoriteDao.isFavorite(userId, eventId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findEventIdsByUser(final long userId) {
        return favoriteDao.findEventIdsByUser(userId);
    }
}
