package ar.edu.itba.paw.webapp.form;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

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
    public void testToFilterQueryOmitsDefaultSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA_ASC");
        assertEquals("", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryKeepsNonDefaultSort() {
        final EventSearchForm form = new EventSearchForm();
        form.setSort("PRECIO_ASC");
        assertEquals("sort=PRECIO_ASC&", form.toFilterQuery());
    }

    @Test
    public void testToFilterQueryNormalizesInvalidSortToDefault() {
        // Un valor que no existe cae en FECHA_ASC (ver parseSort): no debe quedar en la URL.
        final EventSearchForm form = new EventSearchForm();
        form.setSort("FECHA");
        assertEquals("", form.toFilterQuery());
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
