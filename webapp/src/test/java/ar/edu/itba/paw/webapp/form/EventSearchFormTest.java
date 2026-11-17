package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.SearchTerms;
import org.junit.Test;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.ServletRequestDataBinder;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventSearchFormTest {

    // El form tal como lo arma Spring al recibir los parametros de la URL (con la misma conversion de tipos).
    private static BindingResult bind(final EventSearchForm form, final String... nameValuePairs) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            request.addParameter(nameValuePairs[i], nameValuePairs[i + 1]);
        }
        final ServletRequestDataBinder binder = new ServletRequestDataBinder(form, "eventSearchForm");
        binder.setConversionService(new DefaultFormattingConversionService());
        binder.bind(request);
        return binder.getBindingResult();
    }

    // ---- toFilterQuery() ----

    @Test
    public void testToFilterQueryWithEmptyFormIsEmpty() {
        assertEquals("", new EventSearchForm().toFilterQuery());
    }

    @Test
    public void testToFilterQuerySkipsTheFiltersThatWereNotSet() {
        final EventSearchForm form = new EventSearchForm();
        form.setPrecioMax(new BigDecimal("500"));
        assertEquals("precioMax=500&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryRepeatsEachListValue() {
        final EventSearchForm form = new EventSearchForm();
        form.setCategoria(Arrays.asList(1L, 3L));
        form.setBarrio(Arrays.asList(Barrio.PALERMO, Barrio.RECOLETA));
        assertEquals("categoria=1&categoria=3&barrio=PALERMO&barrio=RECOLETA&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryWritesThePricesWithoutScientificNotation() {
        final EventSearchForm form = new EventSearchForm();
        form.setPrecioMin(new BigDecimal("1E+3"));
        assertEquals("precioMin=1000&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryWritesTheDatesAsTheFormSendsThem() throws Exception {
        final EventSearchForm form = new EventSearchForm();
        form.setFechaDesde(new SimpleDateFormat("yyyy-MM-dd").parse("2030-05-17"));
        assertEquals("fechaDesde=2030-05-17&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryOmitsTheSortWhenNoneWasChosen() {
        assertEquals("", new EventSearchForm().toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsAnExplicitDefaultSort() {
        // Quien elige "Mas proximos" a mano tiene que seguir en ese orden al cambiar de pagina:
        // si se omitiera, volveria el orden por defecto, que para quien tiene historial es RECOMENDADOS.
        final EventSearchForm form = new EventSearchForm();
        form.setSort(EventSort.FECHA_ASC);
        assertEquals("sort=FECHA_ASC&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsNonDefaultSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort(EventSort.PRECIO_ASC);
        assertEquals("sort=PRECIO_ASC&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsTheRecommendedSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort(EventSort.RECOMENDADOS);
        assertEquals("sort=RECOMENDADOS&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryWritesTheCheckboxesOnlyWhenTheyAreOn() {
        final EventSearchForm form = new EventSearchForm();
        form.setPocosCupos(true);
        form.setAgotados(false);
        assertEquals("pocosCupos=true&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryNeverIncludesPage() {
        final EventSearchForm form = new EventSearchForm();
        form.setPage(4);
        form.setAgotados(true);
        assertEquals("agotados=true&", form.toFilterQuery());
    }

    // ---- orden ----

    @Test
    public void testSortIsNotExplicitWhenMissing() {
        assertFalse(new EventSearchForm().isSortExplicit());
    }

    @Test
    public void testSortIsExplicitWhenItIsAKnownOption() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort(EventSort.CUPOS_ASC);
        assertTrue(form.isSortExplicit());
    }

    @Test
    public void testToCriteriaLeavesTheSortEmptyWhenNoneWasChosen() {
        assertNull(new EventSearchForm().toCriteria().getSort());
    }

    @Test
    public void testToCriteriaKeepsTheChosenSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort(EventSort.PRECIO_DESC);
        assertEquals(EventSort.PRECIO_DESC, form.toCriteria().getSort());
    }

    // ---- toCriteria() ----

    @Test
    public void testToCriteriaAsksForTheFirstPageWhenNoneWasChosen() {
        assertEquals(1, new EventSearchForm().toCriteria().getPage());
    }

    @Test
    public void testToCriteriaAsksForTheChosenPage() {
        final EventSearchForm form = new EventSearchForm();
        form.setPage(3);
        assertEquals(3, form.toCriteria().getPage());
    }

    @Test
    public void testToCriteriaIgnoresAPriceRangeThatIsBackwards() {
        final EventSearchForm form = new EventSearchForm();
        form.setPrecioMin(new BigDecimal("900"));
        form.setPrecioMax(new BigDecimal("100"));
        final EventSearchCriteria criteria = form.toCriteria();
        assertNull(criteria.getMinPrice());
        assertNull(criteria.getMaxPrice());
    }

    @Test
    public void testToCriteriaKeepsAPriceRangeInOrder() {
        final EventSearchForm form = new EventSearchForm();
        form.setPrecioMin(new BigDecimal("100"));
        form.setPrecioMax(new BigDecimal("900"));
        final EventSearchCriteria criteria = form.toCriteria();
        assertEquals(new BigDecimal("100"), criteria.getMinPrice());
        assertEquals(new BigDecimal("900"), criteria.getMaxPrice());
    }

    // ---- enlazado desde la URL (lo que convierte Spring) ----

    @Test
    public void testBindingTurnsEachParameterIntoItsType() throws Exception {
        final EventSearchForm form = new EventSearchForm();
        final BindingResult errors = bind(form, "categoria", "2", "categoria", "5", "barrio", "PALERMO",
                "precioMin", "100.50", "fechaDesde", "2030-05-17", "puntajeMin", "4", "pocosCupos", "true",
                "page", "3", "sort", "PRECIO_ASC");

        assertFalse(errors.hasErrors());
        assertEquals(Arrays.asList(2L, 5L), form.getCategoria());
        assertEquals(Collections.singletonList(Barrio.PALERMO), form.getBarrio());
        assertEquals(new BigDecimal("100.50"), form.getPrecioMin());
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").parse("2030-05-17"), form.getFechaDesde());
        assertEquals(Integer.valueOf(4), form.getPuntajeMin());
        assertTrue(form.isOnlyFewSpotsLeft());
        assertEquals(Integer.valueOf(3), form.getPage());
        assertEquals(EventSort.PRECIO_ASC, form.getSort());
    }

    @Test
    public void testBindingAValueThatIsNotUnderstoodLeavesThatFilterEmptyAndReportsTheField() {
        final EventSearchForm form = new EventSearchForm();
        final BindingResult errors = bind(form, "precioMin", "abc", "fechaDesde", "no-es-fecha", "page", "xyz",
                "sort", "FECHA");

        assertEquals(4, errors.getFieldErrorCount());
        assertNull(form.getPrecioMin());
        assertNull(form.getFechaDesde());
        assertNull(form.getPage());
        assertNull(form.getSort());
    }

    @Test
    public void testBindingAValueThatIsNotUnderstoodDoesNotStopTheOtherFilters() {
        final EventSearchForm form = new EventSearchForm();
        bind(form, "precioMin", "abc", "precioMax", "800", "puntajeMin", "3");

        final EventSearchCriteria criteria = form.toCriteria();
        assertNull(criteria.getMinPrice());
        assertEquals(new BigDecimal("800"), criteria.getMaxPrice());
        assertEquals(Integer.valueOf(3), criteria.getMinOrganizerRating());
    }

    @Test
    public void testBindingAnEmptyValueIsNotAnError() {
        final EventSearchForm form = new EventSearchForm();
        final BindingResult errors = bind(form, "precioMin", "", "fechaDesde", "", "puntajeMin", "", "pocosCupos", "");

        assertFalse(errors.hasErrors());
        assertFalse(form.hasActiveFilters());
    }

    // ---- barra de busqueda (q) ----

    @Test
    public void testToCriteriaCarriesTheSearchText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("recital de rock");
        assertEquals("recital de rock", form.toCriteria().getText());
    }

    @Test
    public void testToCriteriaTrimsTheSearchText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("  rock  ");
        assertEquals("rock", form.toCriteria().getText());
    }

    @Test
    public void testToCriteriaCutsTheSearchTextAtTheMaximumLength() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ(String.join("", Collections.nCopies(SearchTerms.MAX_TEXT_LENGTH + 50, "a")));
        assertEquals(SearchTerms.MAX_TEXT_LENGTH, form.toCriteria().getText().length());
    }

    @Test
    public void testToCriteriaOfABlankSearchTextHasNoText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("   ");
        assertNull(form.toCriteria().getText());
    }

    @Test
    public void testToCriteriaOfASearchTextOfOnlySignsHasNoText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("¿?!...");
        assertNull(form.toCriteria().getText());
    }

    @Test
    public void testHasSearchTextIsTrueWhenThereIsAWordToSearch() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("rock");
        assertTrue(form.hasSearchText());
    }

    @Test
    public void testHasSearchTextIsFalseWithoutText() {
        assertFalse(new EventSearchForm().hasSearchText());
    }

    @Test
    public void testSearchTextIsNotAnActiveFilter() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("rock");
        assertFalse(form.hasActiveFilters());
    }

    @Test
    public void testToFilterQueryEncodesTheSearchText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("Rock & Roll");
        assertEquals("q=Rock+%26+Roll&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsTheSearchTextNextToTheFilters() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("rock");
        form.setPrecioMax(new BigDecimal("500"));
        assertEquals("q=rock&precioMax=500&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQuerySkipsABlankSearchText() {
        final EventSearchForm form = new EventSearchForm();
        form.setQ("   ");
        assertEquals("", form.toFilterQuery());
    }
}
