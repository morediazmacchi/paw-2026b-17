// Implementa el contrato FavoriteService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    static final int PAGE_SIZE = 10;

    private final FavoriteDao favoriteDao;
    private final EventService eventService;

    @Autowired
    public FavoriteServiceImpl(final FavoriteDao favoriteDao, final EventService eventService) {
        this.favoriteDao = favoriteDao;
        this.eventService = eventService;
    }

    @Override
    @Transactional
    public boolean add(final long userId, final long eventId) {
        // Que se pueda ver el evento lo decide @PreAuthorize en el controller (ver EventSecurity).
        return favoriteDao.add(userId, eventId);
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
        // paginas cuentan solo lo que se ve. Primero los ids de la pagina (LIMIT/OFFSET) y despues sus
        // eventos con un unico IN (...): nunca se trae la lista completa de favoritos.
        final long total = countViewable(userId, isAdmin);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        final List<Long> pageIds = isAdmin
                ? favoriteDao.findEventIdsByUser(userId, effectivePage, PAGE_SIZE)
                : favoriteDao.findViewableEventIdsByUser(userId, effectivePage, PAGE_SIZE);
        return new PageResult<>(eventsInOrder(pageIds), total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countViewable(final long userId, final boolean isAdmin) {
        // COUNT en la base, con la misma regla de visibilidad que findViewablePage() (EventAccessService.canView):
        // un admin ve todos los eventos, el resto solo los suyos, los publicos aprobados y los privados con acceso.
        return isAdmin ? favoriteDao.countByUserId(userId) : favoriteDao.countViewableByUserId(userId);
    }

    // Los eventos de los ids de UNA pagina, en el orden de esos ids (findByIds no garantiza orden).
    // Una sola consulta para todos en vez de una por favorito.
    private List<Event> eventsInOrder(final List<Long> pageIds) {
        if (pageIds.isEmpty()) {
            return Collections.emptyList();
        }
        final Map<Long, Event> eventsById = eventService.findByIds(pageIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        final List<Event> ordered = new ArrayList<>(pageIds.size());
        for (final Long eventId : pageIds) {
            final Event event = eventsById.get(eventId);
            if (event != null) {
                ordered.add(event);
            }
        }
        return ordered;
    }
}
