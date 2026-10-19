package ar.edu.itba.paw.services;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PriceDistributionTest {

    private static final double DELTA = 1e-9;

    private static PriceDistribution of(final String... prices) {
        final List<BigDecimal> list = new ArrayList<>();
        for (final String price : prices) {
            list.add(new BigDecimal(price));
        }
        return PriceDistribution.of(list);
    }

    @Test
    public void testPercentileOfAnEmptyDistributionIsTheMiddle() {
        assertEquals(0.5, PriceDistribution.of(Collections.<BigDecimal>emptyList()).percentile(new BigDecimal("1000")), DELTA);
    }

    @Test
    public void testPercentileOfTheCheapestIsNearZero() {
        assertEquals(0.125, of("1000", "2000", "3000", "4000").percentile(new BigDecimal("1000")), DELTA);
    }

    @Test
    public void testPercentileOfTheMostExpensiveIsNearOne() {
        assertEquals(0.875, of("1000", "2000", "3000", "4000").percentile(new BigDecimal("4000")), DELTA);
    }

    @Test
    public void testPercentileBelowEveryPriceIsZero() {
        assertEquals(0.0, of("1000", "2000").percentile(new BigDecimal("500")), DELTA);
    }

    @Test
    public void testPercentileAboveEveryPriceIsOne() {
        assertEquals(1.0, of("1000", "2000").percentile(new BigDecimal("9000")), DELTA);
    }

    @Test
    public void testPercentileOfAPriceBetweenTwoCountsOnlyTheOnesBelow() {
        assertEquals(0.5, of("1000", "2000", "3000", "4000").percentile(new BigDecimal("2500")), DELTA);
    }

    @Test
    public void testEqualPricesLandInTheMiddleOfTheirGroup() {
        final String[] prices = new String[100];
        Arrays.fill(prices, "5000");
        assertEquals(0.5, of(prices).percentile(new BigDecimal("5000")), DELTA);
    }

    @Test
    public void testASingleEventIsInTheMiddle() {
        assertEquals(0.5, of("1000").percentile(new BigDecimal("1000")), DELTA);
    }

    @Test
    public void testSizeCountsThePrices() {
        assertEquals(3, of("1", "2", "3").size());
    }
}
