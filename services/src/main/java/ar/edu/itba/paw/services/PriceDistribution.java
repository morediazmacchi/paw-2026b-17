package ar.edu.itba.paw.services;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * La distribucion de precios pagos de los eventos publicos, para ubicar un precio como
 * percentil (0 = de los mas baratos, 1 = de los mas caros) en vez de compararlo en pesos: asi
 * la similitud de precio no depende de cuanto valgan los pesos ni de un evento carisimo.
 *
 * Inmutable. Se arma desde una lista ya ordenada de menor a mayor (ver PriceDistributionCache).
 */
final class PriceDistribution {

    // Sin ningun evento pago de referencia: no hay forma de decir quien es mas caro.
    static final PriceDistribution EMPTY = new PriceDistribution(new double[0]);

    private final double[] sortedPrices;

    private PriceDistribution(final double[] sortedPrices) {
        this.sortedPrices = sortedPrices;
    }

    /** @param ascendingPrices precios > 0 de menor a mayor (el orden lo garantiza quien llama). */
    static PriceDistribution of(final Collection<BigDecimal> ascendingPrices) {
        final double[] prices = new double[ascendingPrices.size()];
        int i = 0;
        for (final BigDecimal price : ascendingPrices) {
            prices[i++] = price.doubleValue();
        }
        return new PriceDistribution(prices);
    }

    /**
     * Percentil de ese precio: la fraccion de precios de la distribucion por debajo, contando
     * la mitad de los que valen exactamente lo mismo (asi cien eventos a $5.000 quedan en el
     * medio de su grupo, no todos pegados al piso). 0,5 si no hay distribucion.
     */
    double percentile(final BigDecimal price) {
        if (sortedPrices.length == 0) {
            return 0.5;
        }
        final double value = price.doubleValue();
        final int below = lowerBound(value);
        final int belowOrEqual = upperBound(value);
        return (below + belowOrEqual) / 2.0 / sortedPrices.length;
    }

    int size() {
        return sortedPrices.length;
    }

    // Primer indice con precio >= value (= cuantos hay estrictamente por debajo).
    private int lowerBound(final double value) {
        int lo = 0;
        int hi = sortedPrices.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (sortedPrices[mid] < value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    // Primer indice con precio > value (= cuantos hay <= value).
    private int upperBound(final double value) {
        int lo = 0;
        int hi = sortedPrices.length;
        while (lo < hi) {
            final int mid = (lo + hi) >>> 1;
            if (sortedPrices[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
