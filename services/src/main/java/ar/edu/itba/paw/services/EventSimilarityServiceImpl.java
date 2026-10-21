// Implementa el contrato EventSimilarityService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.RegistrationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class EventSimilarityServiceImpl implements EventSimilarityService {

    // Los puntos son sumas de doubles: se comparan redondeados a esta precision para que dos
    // eventos con los mismos matches nunca se desempaten por ruido de punto flotante.
    private static final double POINTS_PRECISION = 1e9;
    // Lo mismo para "S >= piso" de "Te puede interesar": un S que mide 0,5 en papel y 0,49999999999
    // en doubles entra.
    private static final double EPSILON = 1e-9;

    private final EventDao eventDao;
    private final RegistrationDao registrationDao;
    private final PriceDistributionCache priceDistributionCache;
    private final SimilarityParameters parameters;
    private final EventSimilarity similarity;
    private final Clock clock;

    @Autowired
    public EventSimilarityServiceImpl(final EventDao eventDao, final RegistrationDao registrationDao,
                                      final PriceDistributionCache priceDistributionCache,
                                      final SimilarityParameters parameters) {
        this(eventDao, registrationDao, priceDistributionCache, parameters, Clock.systemUTC());
    }

    EventSimilarityServiceImpl(final EventDao eventDao, final RegistrationDao registrationDao,
                               final PriceDistributionCache priceDistributionCache,
                               final SimilarityParameters parameters, final Clock clock) {
        this.eventDao = eventDao;
        this.registrationDao = registrationDao;
        this.priceDistributionCache = priceDistributionCache;
        this.parameters = parameters;
        this.similarity = new EventSimilarity(parameters);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Event> searchRecommended(final EventSearchCriteria criteria) {
        final Long viewerId = Objects.requireNonNull(criteria.getViewerId(), "viewerId");
        final Date now = Date.from(clock.instant());

        // Ya vienen por fecha ascendente y id ascendente: el desempate final del orden.
        final List<Event> candidates = eventDao.findAllMatching(criteria);
        final List<Event> attended = loadAttended(viewerId, now);
        if (attended.isEmpty() || candidates.isEmpty()) {
            // Sin historial nadie suma puntos y el orden por fecha es justamente este.
            return PageResult.of(candidates, criteria.getPage(), criteria.getPageSize());
        }

        final PriceDistribution prices = priceDistributionCache.get();

        // Todos los eventos asistidos suman, sin umbral ni peso por antiguedad: cada uno aporta
        // exactamente su similitud con el candidato (de 0 a 1).
        final Map<Long, Long> pointsByEvent = new HashMap<>();
        for (final Event candidate : candidates) {
            double points = 0;
            for (final Event attendedEvent : attended) {
                points += similarity.similarity(candidate, attendedEvent, prices);
            }
            pointsByEvent.put(candidate.getId(), Math.round(points * POINTS_PRECISION));
        }

        final List<Event> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparing((Event e) -> pointsByEvent.get(e.getId())).reversed()
                .thenComparing(Event::getEventDate)
                .thenComparing(Event::getId));
        return PageResult.of(ranked, criteria.getPage(), criteria.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> findSimilar(final long eventId, final Long viewerId) {
        final Optional<Event> found = eventDao.findById(eventId);
        if (!found.isPresent() || found.get().isPrivate() || !found.get().isApproved()) {
            return Collections.emptyList();
        }
        final Event event = found.get();

        final EventSearchCriteria all = new EventSearchCriteria();
        all.setViewerId(viewerId);
        final PriceDistribution prices = priceDistributionCache.get();

        final Map<Long, Double> scores = new HashMap<>();
        final List<Event> similar = new ArrayList<>();
        for (final Event candidate : eventDao.findAllMatching(all)) {
            if (candidate.getId().equals(event.getId())) {
                continue;
            }
            final double score = similarity.similarity(event, candidate, prices);
            if (score >= parameters.getRelatedMinScore() - EPSILON) {
                scores.put(candidate.getId(), score);
                similar.add(candidate);
            }
        }
        // Mas parecido primero; a igual S, el que ocurre antes, y despues el id.
        similar.sort(Comparator
                .comparing((Event e) -> scores.get(e.getId())).reversed()
                .thenComparing(Event::getEventDate)
                .thenComparing(Event::getId));
        return similar.stream().limit(parameters.getRelatedLimit()).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAttendanceHistory(final long userId) {
        return !registrationDao.findAttendedEventIds(userId, Date.from(clock.instant()), 1).isEmpty();
    }

    // Los ultimos eventos asistidos, del mas reciente al mas viejo.
    private List<Event> loadAttended(final long userId, final Date now) {
        final List<Long> ids = registrationDao.findAttendedEventIds(userId, now, parameters.getHistorySize());
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        final Map<Long, Event> byId = new HashMap<>();
        for (final Event event : eventDao.findByIds(ids)) {
            byId.put(event.getId(), event);
        }
        final List<Event> ordered = new ArrayList<>(ids.size());
        for (final Long id : ids) {
            final Event event = byId.get(id);
            if (event != null) {
                ordered.add(event);
            }
        }
        return ordered;
    }
}
