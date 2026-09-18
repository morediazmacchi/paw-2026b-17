// Implementa el contrato FavoriteService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.persistence.FavoriteDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteDao favoriteDao;

    @Autowired
    public FavoriteServiceImpl(final FavoriteDao favoriteDao) {
        this.favoriteDao = favoriteDao;
    }

    @Override
    @Transactional
    public void add(final long userId, final long eventId) {
        favoriteDao.add(userId, eventId);
    }

    @Override
    @Transactional
    public void remove(final long userId, final long eventId) {
        favoriteDao.remove(userId, eventId);
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
