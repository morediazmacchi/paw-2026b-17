// Implementa el contrato FavoriteService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    static final int PAGE_SIZE = 10;

    private final FavoriteDao favoriteDao;

    @Autowired
    public FavoriteServiceImpl(final FavoriteDao favoriteDao) {
        this.favoriteDao = favoriteDao;
    }

    @Override
    @Transactional
    public boolean add(final long userId, final long eventId) {
        // Que se pueda ver el evento lo decide @PreAuthorize en el controller (ver EventSecurity).
        try {
            return favoriteDao.add(userId, eventId);
        } catch (final DataIntegrityViolationException e) {
            // Dos "guardar" simultaneos de la misma persona: los dos pasaron el chequeo del DAO ("ya era favorito?") y
            // el segundo choco contra la clave (user_id, event_id) al insertar. Para quien lo pidio es lo mismo que
            // "ya era favorito": false. Cualquier otra violacion (por ejemplo un evento que no existe) es un error de
            // verdad y no se disfraza. Mismo criterio que ReviewServiceImpl.create().
            if (!UniqueViolation.isUniqueViolation(e)) {
                throw e;
            }
            // El insert fallido ya dejo la transaccion marcada para rollback: se lo decimos explicitamente, asi Spring
            // hace rollback sin tirar UnexpectedRollbackException (un 500).
            RollbackOnly.ifInTransaction();
            return false;
        }
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
    public Set<Long> findFavoriteEventIds(final long userId, final Collection<Long> eventIds) {
        return favoriteDao.findFavoriteEventIds(userId, eventIds);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findViewablePage(final long userId, final boolean isAdmin, final int page) {
        // Todo en la base: el COUNT y la pagina comparten el WHERE de visibilidad, asi el total y las
        // paginas cuentan solo lo que se ve. El DAO trae solo la pagina (LIMIT/OFFSET), ya ordenada: nunca se trae
        // la lista completa de favoritos ni se reordena en Java.
        final long total = countViewable(userId, isAdmin);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        final List<Event> events = isAdmin
                ? favoriteDao.findEventsByUser(userId, effectivePage, PAGE_SIZE)
                : favoriteDao.findViewableEventsByUser(userId, effectivePage, PAGE_SIZE);
        return new PageResult<>(events, total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countViewable(final long userId, final boolean isAdmin) {
        // COUNT en la base, con la misma regla de visibilidad que findViewablePage() (EventAccessService.canView):
        // un admin ve todos los eventos, el resto solo los suyos, los publicos aprobados y los privados con acceso.
        return isAdmin ? favoriteDao.countByUserId(userId) : favoriteDao.countViewableByUserId(userId);
    }
}
