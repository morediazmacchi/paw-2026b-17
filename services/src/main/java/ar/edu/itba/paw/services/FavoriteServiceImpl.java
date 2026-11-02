// Implementa el contrato FavoriteService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.FavoriteDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FavoriteServiceImpl implements FavoriteService {

    static final int PAGE_SIZE = 10;

    private final FavoriteDao favoriteDao;
    private final EventAccessService accessChecker;
    private final EventService eventService;

    @Autowired
    public FavoriteServiceImpl(final FavoriteDao favoriteDao, final EventAccessService accessChecker,
                                final EventService eventService) {
        this.favoriteDao = favoriteDao;
        this.accessChecker = accessChecker;
        this.eventService = eventService;
    }

    @Override
    @Transactional
    public boolean add(final User user, final long eventId) {
        // Que se pueda ver el evento lo decide @PreAuthorize en el controller (ver EventSecurity).
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

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> findViewablePage(final User user, final int page) {
        // La visibilidad (canView) depende de la inscripcion/invitacion de cada
        // usuario, no es una columna: no se puede filtrar en el LIMIT/OFFSET. Se
        // filtra primero la lista completa y recien despues se corta la pagina
        // (PageResult.of), asi el total y las paginas cuentan solo lo que se ve.
        return PageResult.of(viewableFavorites(user), page, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public long countViewable(final long userId, final boolean isAdmin) {
        // COUNT en la base, con la misma regla de visibilidad que viewableFavorites() (EventAccessService.canView):
        // un admin ve todos los eventos, el resto solo los suyos, los publicos aprobados y los privados con acceso.
        return isAdmin ? favoriteDao.countByUserId(userId) : favoriteDao.countViewableByUserId(userId);
    }

    // Los favoritos de la cuenta, mas recientes primero, SIN los de un evento
    // privado al que ya no tiene acceso. Una sola consulta para todos los
    // eventos (findByIds) en vez de una por favorito.
    private List<Event> viewableFavorites(final User user) {
        final List<Long> favoriteEventIds = favoriteDao.findEventIdsByUser(user.getId());
        if (favoriteEventIds.isEmpty()) {
            return Collections.emptyList();
        }
        final Map<Long, Event> eventsById = eventService.findByIds(favoriteEventIds).stream()
                .collect(Collectors.toMap(Event::getId, event -> event));
        final List<Event> viewable = new ArrayList<>();
        for (final Long eventId : favoriteEventIds) {
            final Event event = eventsById.get(eventId);
            if (event != null && accessChecker.canView(event, user)) {
                viewable.add(event);
            }
        }
        return viewable;
    }
}
