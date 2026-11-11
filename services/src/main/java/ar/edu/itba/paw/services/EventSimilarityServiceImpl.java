// Implementa el contrato EventSimilarityService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.SimilarityParameters;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Objects;

// No calcula nada: S(a, b), el puntaje y el orden los resuelve la base de datos en una sola consulta
// (EventDao.findRecommendedPage()/findSimilarTo()), que ya devuelve la pagina o el top cortado. Este service
// solo le pasa los coeficientes configurados y arma el PageResult (total + pagina efectiva).
@Service
public class EventSimilarityServiceImpl implements EventSimilarityService {

    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final SimilarityParameters parameters;
    private final Clock clock;

    @Autowired
    public EventSimilarityServiceImpl(final EventDao eventDao, final RegistrationDao registrationDao,
                                      final SimilarityParameters parameters) {
        this(eventDao, registrationDao, parameters, Clock.systemUTC());
    }

    EventSimilarityServiceImpl(final EventDao eventDao, final RegistrationDao registrationDao,
                               final SimilarityParameters parameters, final Clock clock) {
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.parameters = parameters;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> searchRecommended(final EventSearchCriteria criteria) {
        Objects.requireNonNull(criteria.getViewerId(), "viewerId");
        // El total es el del listado (mismo WHERE); la pagina, la que corresponde del ranking. Si piden una pagina que
        // no existe cae a la ultima con resultados (igual que EventService.search()).
        final long total = eventDao.countSearch(criteria);
        final int page = PageResult.clampPage(criteria.getPage(), total, criteria.getPageSize());
        criteria.setPage(page);
        return new PageResult<>(eventDao.findRecommendedPage(criteria, parameters), total, page, criteria.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findSimilar(final long eventId, final Long viewerId) {
        return eventDao.findSimilarTo(eventId, viewerId, parameters);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAttendanceHistory(final long userId) {
        return !registrationDao.findAttendedEventIds(userId, Date.from(clock.instant()), 1).isEmpty();
    }
}
