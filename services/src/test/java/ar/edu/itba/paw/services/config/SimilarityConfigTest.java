package ar.edu.itba.paw.services.config;

import ar.edu.itba.paw.services.SimilarityParameters;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

// Levanta SOLO esta @Configuration (sin base ni el resto de la app) para comprobar que los
// valores salen de similarity.properties y que un archivo externo los puede pisar.
public class SimilarityConfigTest {

    private static final String EXTERNAL_CONFIG_PROPERTY = "paw.similarity.config";
    private static final double DELTA = 1e-9;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @After
    public void clearSystemProperty() {
        System.clearProperty(EXTERNAL_CONFIG_PROPERTY);
    }

    private static SimilarityParameters load() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SimilarityConfig.class)) {
            return context.getBean(SimilarityParameters.class);
        }
    }

    @Test
    public void testThePackagedFileHoldsTheCalibratedValues() {
        final SimilarityParameters loaded = load();
        final SimilarityParameters defaults = SimilarityParameters.defaults();

        assertEquals(defaults.getWeightCategory(), loaded.getWeightCategory(), DELTA);
        assertEquals(defaults.getWeightPrice(), loaded.getWeightPrice(), DELTA);
        assertEquals(defaults.getWeightNeighborhood(), loaded.getWeightNeighborhood(), DELTA);
        assertEquals(defaults.getWeightHour(), loaded.getWeightHour(), DELTA);
        assertEquals(defaults.getWeightWeekday(), loaded.getWeightWeekday(), DELTA);
        assertEquals(defaults.getWeightOrganizer(), loaded.getWeightOrganizer(), DELTA);
        assertEquals(defaults.getFreeVsPaidFactor(), loaded.getFreeVsPaidFactor(), DELTA);
        assertEquals(defaults.getPriceRefreshSeconds(), loaded.getPriceRefreshSeconds());
        assertEquals(defaults.getIgnoredCategoryNames(), loaded.getIgnoredCategoryNames());
        assertEquals(defaults.getRelatedLimit(), loaded.getRelatedLimit());
        assertEquals(defaults.getRelatedMinScore(), loaded.getRelatedMinScore(), DELTA);
        assertEquals(defaults.getHistorySize(), loaded.getHistorySize());
    }

    @Test
    public void testTheCalibratedValuesAreTheOnesAgreedOn() {
        final SimilarityParameters loaded = load();

        assertEquals(0.5, loaded.getRelatedMinScore(), DELTA);
        assertEquals(5, loaded.getRelatedLimit());
        assertEquals(30, loaded.getHistorySize());
        assertEquals(Collections.singleton("Otros"), loaded.getIgnoredCategoryNames());
    }

    @Test
    public void testAnExternalFileOverridesOnlyTheKeysItHas() throws IOException {
        final File external = folder.newFile("similarity.properties");
        Files.write(external.toPath(), ("similarity.user.history-size=10\n"
                + "similarity.weight.category=0.5\n"
                + "similarity.category.ignored=Otros, Arte\n").getBytes(StandardCharsets.UTF_8));
        System.setProperty(EXTERNAL_CONFIG_PROPERTY, external.getAbsolutePath());

        final SimilarityParameters loaded = load();

        assertEquals(10, loaded.getHistorySize());
        assertEquals(0.5, loaded.getWeightCategory(), DELTA);
        assertEquals(2, loaded.getIgnoredCategoryNames().size());
        // Lo que el externo no menciona queda como en el archivo del WAR.
        assertEquals(0.15, loaded.getWeightPrice(), DELTA);
        assertEquals(5, loaded.getRelatedLimit());
    }

    @Test
    public void testAMissingExternalFileIsIgnored() {
        System.setProperty(EXTERNAL_CONFIG_PROPERTY, new File(folder.getRoot(), "no-existe.properties").getAbsolutePath());

        assertEquals(30, load().getHistorySize());
    }
}
