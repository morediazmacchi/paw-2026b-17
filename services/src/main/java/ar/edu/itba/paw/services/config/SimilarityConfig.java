package ar.edu.itba.paw.services.config;

import ar.edu.itba.paw.services.SimilarityParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carga los parametros de similitud desde similarity.properties (mismo criterio que
 * mail.properties/MailConfig y db.properties/RootConfig: cambiar un numero es editar un
 * archivo, no tocar codigo).
 *
 * Ademas del archivo que viene en el WAR, se lee uno externo opcional: si se arranca con
 * -Dpaw.similarity.config=/ruta/similarity.properties, sus claves pisan las del WAR. Asi se
 * ajustan los pesos en produccion sin recompilar (si hay que reiniciar la app).
 *
 * Una clave que falta (o no esta en ningun archivo) queda con el valor por defecto de
 * SimilarityParameters: nunca impide que la app arranque.
 */
@Configuration
@PropertySource("classpath:similarity.properties")
// El segundo @PropertySource se procesa despues y gana. Si la propiedad del sistema no esta,
// apunta a un archivo que no existe y ignoreResourceNotFound lo deja pasar.
@PropertySource(value = "file:${paw.similarity.config:/nonexistent/similarity.properties}",
        ignoreResourceNotFound = true)
public class SimilarityConfig {

    @Bean
    public SimilarityParameters similarityParameters(final Environment env) {
        final SimilarityParameters d = SimilarityParameters.defaults();
        return SimilarityParameters.builder()
                .weightCategory(env.getProperty("similarity.weight.category", Double.class, d.getWeightCategory()))
                .weightPrice(env.getProperty("similarity.weight.price", Double.class, d.getWeightPrice()))
                .weightNeighborhood(env.getProperty("similarity.weight.neighborhood", Double.class, d.getWeightNeighborhood()))
                .weightHour(env.getProperty("similarity.weight.hour", Double.class, d.getWeightHour()))
                .weightWeekday(env.getProperty("similarity.weight.weekday", Double.class, d.getWeightWeekday()))
                .weightOrganizer(env.getProperty("similarity.weight.organizer", Double.class, d.getWeightOrganizer()))
                .freeVsPaidFactor(env.getProperty("similarity.price.free-vs-paid-factor", Double.class, d.getFreeVsPaidFactor()))
                .priceRefreshSeconds(env.getProperty("similarity.price.refresh-seconds", Long.class, d.getPriceRefreshSeconds()))
                .ignoredCategoryNames(ignoredCategories(env.getProperty("similarity.category.ignored"), d))
                .relatedLimit(env.getProperty("similarity.related.limit", Integer.class, d.getRelatedLimit()))
                .relatedMinScore(env.getProperty("similarity.related.min-score", Double.class, d.getRelatedMinScore()))
                .userThreshold(env.getProperty("similarity.user.threshold", Double.class, d.getUserThreshold()))
                .historySize(env.getProperty("similarity.user.history-size", Integer.class, d.getHistorySize()))
                .recencyHalfLifeDays(env.getProperty("similarity.user.recency-half-life-days", Double.class, d.getRecencyHalfLifeDays()))
                .recencyBonus(env.getProperty("similarity.user.recency-bonus", Double.class, d.getRecencyBonus()))
                .build();
    }

    private static Set<String> ignoredCategories(final String raw, final SimilarityParameters defaults) {
        if (raw == null) {
            return defaults.getIgnoredCategoryNames();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }
}
