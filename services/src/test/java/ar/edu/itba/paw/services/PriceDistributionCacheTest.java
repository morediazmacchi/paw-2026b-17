package ar.edu.itba.paw.services;

import ar.edu.itba.paw.persistence.EventDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PriceDistributionCacheTest {

    private static final long REFRESH_SECONDS = 3600;

    @Mock
    private EventDao eventDao;

    private MutableClock clock;
    private PriceDistributionCache cache;

    @Before
    public void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-19T12:00:00Z"));
        cache = new PriceDistributionCache(eventDao,
                SimilarityParameters.builder().priceRefreshSeconds(REFRESH_SECONDS).build(), clock);
    }

    @Test
    public void testFirstGetLoadsThePricesFromTheDao() {
        when(eventDao.findPublicPaidPrices()).thenReturn(Arrays.asList(new BigDecimal("1000"), new BigDecimal("2000")));

        assertEquals(2, cache.get().size());
    }

    @Test
    public void testSecondGetWithinTheRefreshWindowDoesNotReadAgain() {
        // Si leyera de nuevo, la segunda lectura traeria dos precios y no uno.
        when(eventDao.findPublicPaidPrices())
                .thenReturn(Collections.singletonList(new BigDecimal("1000")))
                .thenReturn(Arrays.asList(new BigDecimal("1000"), new BigDecimal("3000")));

        final PriceDistribution first = cache.get();
        clock.advanceSeconds(REFRESH_SECONDS - 1);
        final PriceDistribution second = cache.get();

        assertSame(first, second);
        assertEquals(1, second.size());
    }

    @Test
    public void testGetAfterTheRefreshWindowReadsAgain() {
        when(eventDao.findPublicPaidPrices())
                .thenReturn(Collections.singletonList(new BigDecimal("1000")))
                .thenReturn(Arrays.asList(new BigDecimal("1000"), new BigDecimal("3000")));

        assertEquals(1, cache.get().size());
        clock.advanceSeconds(REFRESH_SECONDS);

        assertEquals(2, cache.get().size());
    }

    // Un reloj que se puede adelantar a mano.
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(final Instant now) {
            this.now = now;
        }

        private void advanceSeconds(final long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
