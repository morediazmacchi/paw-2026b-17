package ar.edu.itba.paw.services;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Todos los numeros que regulan la similitud entre eventos y las recomendaciones (ver
 * EventSimilarity y EventSimilarityServiceImpl). Valores inmutables: se arman una vez al
 * levantar la app a partir de similarity.properties (ver config.SimilarityConfig), asi que
 * cambiar uno es editar ese archivo y reiniciar -- no hay que tocar codigo.
 *
 * Los pesos no hace falta que sumen 1: S se divide por la suma de los pesos de las features
 * que ambos eventos tienen.
 */
public final class SimilarityParameters {

    // ---- S(a, b): peso de cada feature ----
    private final double weightCategory;
    private final double weightPrice;
    private final double weightNeighborhood;
    private final double weightHour;
    private final double weightWeekday;
    private final double weightOrganizer;
    // Gratis contra pago: nunca llega a parecerse como dos gratis. Va de 0 (no se parecen en
    // nada) a 1 (un gratis y un pago barato valen como dos gratis); 0,5 = hasta la mitad.
    private final double freeVsPaidFactor;
    // Categorias que no cuentan para comparar (son un "otros": que dos eventos la compartan no
    // dice nada). Por nombre, tal cual esta en la tabla category.
    private final Set<String> ignoredCategoryNames;
    // Cada cuanto se recalcula la distribucion de precios (ver PriceDistributionCache).
    private final long priceRefreshSeconds;

    // ---- "Te puede interesar" ----
    private final int relatedLimit;
    private final double relatedMinScore;

    // ---- Orden RECOMENDADOS ----
    // Se miran los ultimos N eventos asistidos.
    private final int historySize;

    private SimilarityParameters(final Builder b) {
        this.weightCategory = b.weightCategory;
        this.weightPrice = b.weightPrice;
        this.weightNeighborhood = b.weightNeighborhood;
        this.weightHour = b.weightHour;
        this.weightWeekday = b.weightWeekday;
        this.weightOrganizer = b.weightOrganizer;
        this.freeVsPaidFactor = b.freeVsPaidFactor;
        this.ignoredCategoryNames = Collections.unmodifiableSet(new HashSet<>(b.ignoredCategoryNames));
        this.priceRefreshSeconds = b.priceRefreshSeconds;
        this.relatedLimit = b.relatedLimit;
        this.relatedMinScore = b.relatedMinScore;
        this.historySize = b.historySize;
    }

    /** Los valores con los que se calibro el algoritmo; son tambien los de similarity.properties. */
    public static SimilarityParameters defaults() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public double getWeightCategory() { return weightCategory; }
    public double getWeightPrice() { return weightPrice; }
    public double getWeightNeighborhood() { return weightNeighborhood; }
    public double getWeightHour() { return weightHour; }
    public double getWeightWeekday() { return weightWeekday; }
    public double getWeightOrganizer() { return weightOrganizer; }
    public double getFreeVsPaidFactor() { return freeVsPaidFactor; }
    public Set<String> getIgnoredCategoryNames() { return ignoredCategoryNames; }
    public long getPriceRefreshSeconds() { return priceRefreshSeconds; }
    public int getRelatedLimit() { return relatedLimit; }
    public double getRelatedMinScore() { return relatedMinScore; }
    public int getHistorySize() { return historySize; }

    /** Arranca con los valores por defecto; cada setter pisa uno. */
    public static final class Builder {
        private double weightCategory = 0.35;
        private double weightPrice = 0.15;
        private double weightNeighborhood = 0.15;
        private double weightHour = 0.15;
        private double weightWeekday = 0.05;
        private double weightOrganizer = 0.15;
        private double freeVsPaidFactor = 0.5;
        private Set<String> ignoredCategoryNames = Collections.singleton("Otros");
        private long priceRefreshSeconds = 24 * 60 * 60;
        private int relatedLimit = 5;
        private double relatedMinScore = 0.5;
        private int historySize = 30;

        private Builder() {
        }

        public Builder weightCategory(final double v) { this.weightCategory = v; return this; }
        public Builder weightPrice(final double v) { this.weightPrice = v; return this; }
        public Builder weightNeighborhood(final double v) { this.weightNeighborhood = v; return this; }
        public Builder weightHour(final double v) { this.weightHour = v; return this; }
        public Builder weightWeekday(final double v) { this.weightWeekday = v; return this; }
        public Builder weightOrganizer(final double v) { this.weightOrganizer = v; return this; }
        public Builder freeVsPaidFactor(final double v) { this.freeVsPaidFactor = v; return this; }
        public Builder ignoredCategoryNames(final Set<String> v) { this.ignoredCategoryNames = v; return this; }
        public Builder priceRefreshSeconds(final long v) { this.priceRefreshSeconds = v; return this; }
        public Builder relatedLimit(final int v) { this.relatedLimit = v; return this; }
        public Builder relatedMinScore(final double v) { this.relatedMinScore = v; return this; }
        public Builder historySize(final int v) { this.historySize = v; return this; }

        public SimilarityParameters build() {
            return new SimilarityParameters(this);
        }
    }
}
