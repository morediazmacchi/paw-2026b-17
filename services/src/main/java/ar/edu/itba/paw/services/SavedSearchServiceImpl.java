// Implementa el contrato SavedSearchService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.SavedSearch;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.SavedSearchDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class SavedSearchServiceImpl implements SavedSearchService {

    // Un minuto de margen al final de la ventana de "eventos nuevos" (ver notifyNewEvents()).
    static final long COMMIT_MARGIN_MILLIS = 60 * 1000L;

    // Busquedas guardadas por pagina en "Mis listas".
    static final int PAGE_SIZE = 10;

    private final SavedSearchDao savedSearchDao;
    private final EventDao eventDao;
    private final InboxService inboxService;
    private final NotificationService notificationService;

    @Autowired
    public SavedSearchServiceImpl(final SavedSearchDao savedSearchDao, final EventDao eventDao,
                                  final InboxService inboxService, final NotificationService notificationService) {
        this.savedSearchDao = savedSearchDao;
        this.eventDao = eventDao;
        this.inboxService = inboxService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public SaveSearchResult save(final long userId, final EventSearchCriteria criteria) {
        if (savedSearchDao.existsByKey(userId, keyOf(criteria))) {
            return SaveSearchResult.ALREADY_SAVED;
        }
        savedSearchDao.create(userId, criteria.getCategoryIds(), criteria.getBarrios(),
                criteria.getMinPrice(), criteria.getMaxPrice(), criteria.getFromDate(), criteria.getToDate());
        return SaveSearchResult.SAVED;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSaved(final long userId, final EventSearchCriteria criteria) {
        return savedSearchDao.existsByKey(userId, keyOf(criteria));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SavedSearch> findPageByUser(final long userId, final int page) {
        final long total = savedSearchDao.countByUserId(userId);
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(savedSearchDao.findByUserId(userId, effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedSearch> findByIdForUser(final long userId, final long savedSearchId) {
        return savedSearchDao.findByIdWithFilters(savedSearchId)
                .filter(search -> Long.valueOf(userId).equals(search.getUserId()));
    }

    @Override
    @Transactional
    public boolean delete(final long userId, final long savedSearchId) {
        return savedSearchDao.deleteByIdAndUserId(savedSearchId, userId);
    }

    private static String keyOf(final EventSearchCriteria criteria) {
        return SavedSearch.keyOf(criteria.getCategoryIds(), criteria.getBarrios(), criteria.getMinPrice(),
                criteria.getMaxPrice(), criteria.getFromDate(), criteria.getToDate());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findIdsAfter(final long afterId, final int limit) {
        return savedSearchDao.findIdsAfter(afterId, limit);
    }

    @Override
    @Transactional
    public boolean notifyNewEvents(final long savedSearchId) {
        // Sin lock: el unico que modifica una busqueda guardada es este aviso, que corre en un solo hilo
        // (SavedSearchScheduler, fixedDelay): nunca hay dos corridas a la vez sobre la misma fila.
        final Optional<SavedSearch> found = savedSearchDao.findByIdWithFilters(savedSearchId);
        if (!found.isPresent()) {
            // La borraron entre la tanda y este punto: no hay a quien avisarle.
            return false;
        }
        final SavedSearch search = found.get();
        // Un minuto de margen: un evento recien aprobado puede tener approved_at puesto pero su transaccion todavia
        // sin commitear (no se ve). Si la ventana llegara hasta "ahora" y se corriera la marca, ese evento quedaria
        // afuera para siempre. Con el margen, lo mas nuevo entra en la corrida siguiente.
        final Date until = new Date(System.currentTimeMillis() - COMMIT_MARGIN_MILLIS);

        // Los mismos eventos que veria en /eventos con esos filtros (mismo SQL), pero solo los aprobados desde el
        // ultimo aviso hasta "until". Sus propios eventos no cuentan, igual que en el listado.
        final EventSearchCriteria criteria = search.toCriteria();
        criteria.setViewerId(search.getUserId());
        criteria.setApprovedAfter(search.getLastCheckedAt());
        criteria.setApprovedUntil(until);
        final long newEvents = eventDao.countSearch(criteria);

        // La marca se corre siempre, haya o no eventos nuevos. Va en la misma transaccion que el aviso: si algo
        // falla se deshacen las dos cosas y la proxima corrida lo vuelve a intentar con la misma ventana.
        savedSearchDao.markChecked(savedSearchId, until);
        if (newEvents == 0) {
            return false;
        }

        // Uno de ejemplo para el aviso: el mas proximo (una pagina de un solo evento, no la lista entera).
        criteria.setSort(EventSort.FECHA_ASC);
        criteria.setPage(1);
        criteria.setPageSize(1);
        final List<Event> example = eventDao.search(criteria);
        if (example.isEmpty()) {
            return false;
        }
        final User owner = search.getUser();
        // Bandeja: adentro de esta transaccion. Mail: @Async, sale recien despues del commit (AfterCommitTaskExecutor).
        inboxService.add(owner.getEmail(), InboxMessageType.SAVED_SEARCH_MATCHES, example.get(0).getId(), null, null);
        notificationService.notifySavedSearchMatches(owner, savedSearchId, example.get(0), newEvents);
        return true;
    }
}
