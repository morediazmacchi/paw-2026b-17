package ar.edu.itba.paw.services;

import ar.edu.itba.paw.persistence.EventDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Guarda en memoria la distribucion de precios para no leer todos los precios de la base en
 * cada calculo de similitud: se vuelve a leer solo cuando pasaron
 * {@link SimilarityParameters#getPriceRefreshSeconds()} desde la ultima vez (por defecto, un dia).
 *
 * No hace falta nada mas fino que eso: la distribucion no se guarda en ningun lado ni se
 * compara contra una version anterior, asi que un umbral que se corre un poco entre lecturas
 * solo cambia un ranking, no deja nada inconsistente.
 *
 * Es lazy (se carga la primera vez que alguien la pide, dentro de la transaccion de quien
 * llama) y no necesita un @Scheduled propio.
 */
@Component
class PriceDistributionCache {

    private final EventDao eventDao;
    private final Duration refreshEvery;
    private final Clock clock;

    // Se reemplaza entera (nunca se modifica): quien ya tiene una referencia sigue viendo una
    // distribucion consistente aunque otro hilo la renueve.
    private volatile PriceDistribution distribution;
    private volatile Instant loadedAt;

    @Autowired
    PriceDistributionCache(final EventDao eventDao, final SimilarityParameters parameters) {
        this(eventDao, parameters, Clock.systemUTC());
    }

    PriceDistributionCache(final EventDao eventDao, final SimilarityParameters parameters, final Clock clock) {
        this.eventDao = eventDao;
        this.refreshEvery = Duration.ofSeconds(parameters.getPriceRefreshSeconds());
        this.clock = clock;
    }

    PriceDistribution get() {
        final Instant now = clock.instant();
        if (distribution == null || Duration.between(loadedAt, now).compareTo(refreshEvery) >= 0) {
            synchronized (this) {
                if (distribution == null || Duration.between(loadedAt, now).compareTo(refreshEvery) >= 0) {
                    distribution = PriceDistribution.of(eventDao.findPublicPaidPrices());
                    loadedAt = now;
                }
            }
        }
        return distribution;
    }
}
