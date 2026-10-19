package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.model.EventSort;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EventSearchFormTest {

    @Test
    public void testToFilterQueryWithEmptyFormIsEmpty() {
        assertEquals("", new EventSearchForm().toFilterQuery());
    }

    @Test
    public void testToFilterQuerySkipsBlankValues() {
        final EventSearchForm form = new EventSearchForm();
        form.setPrecioMin("");
        form.setPrecioMax("500");
        form.setFechaDesde("");
        form.setFechaHasta("");
        assertEquals("precioMax=500&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryRepeatsEachListValue() {
        final EventSearchForm form = new EventSearchForm();
        form.setCategoria(Arrays.asList("1", "3"));
        form.setBarrio(Arrays.asList("PALERMO", ""));
        assertEquals("categoria=1&categoria=3&barrio=PALERMO&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryOmitsTheSortWhenNoneWasChosen() {
        final EventSearchForm form = new EventSearchForm();
        assertEquals("", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsAnExplicitDefaultSort() {
        // Quien elige "Mas proximos" a mano tiene que seguir en ese orden al cambiar de pagina:
        // si se omitiera, volveria el orden por defecto, que para quien tiene historial es RECOMENDADOS.
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA_ASC");
        assertEquals("sort=FECHA_ASC&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsNonDefaultSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("PRECIO_ASC");
        assertEquals("sort=PRECIO_ASC&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsTheRecommendedSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("RECOMENDADOS");
        assertEquals("sort=RECOMENDADOS&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryDropsAnInvalidSort() {
        // Un valor que no existe no es un orden elegido: no debe quedar en la URL.
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA");
        assertEquals("", form.toFilterQuery());
    }

    @Test
    public void testSortIsNotExplicitWhenMissing() {
        assertFalse(new EventSearchForm().isSortExplicit());
    }

    @Test
    public void testSortIsNotExplicitWhenBlank() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("");
        assertFalse(form.isSortExplicit());
    }

    @Test
    public void testSortIsNotExplicitWhenInvalid() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA");
        assertFalse(form.isSortExplicit());
    }

    @Test
    public void testSortIsExplicitWhenItIsAKnownOption() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("CUPOS_ASC");
        assertTrue(form.isSortExplicit());
    }

    @Test
    public void testAnInvalidSortParsesToTheDateOrder() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA");
        assertEquals(EventSort.FECHA_ASC, form.getParsedSort());
    }

    @Test
    public void testToFilterQueryEncodesSpecialCharacters() {
        final EventSearchForm form = new EventSearchForm();
        form.setPocosCupos("a b&c");
        assertEquals("pocosCupos=a+b%26c&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryNeverIncludesPage() {
        final EventSearchForm form = new EventSearchForm();
        form.setPage("4");
        form.setAgotados("on");
        assertEquals("agotados=on&", form.toFilterQuery());
    }
}
