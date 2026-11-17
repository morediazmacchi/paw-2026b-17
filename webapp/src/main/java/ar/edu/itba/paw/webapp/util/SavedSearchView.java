package ar.edu.itba.paw.webapp.util;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.SavedSearch;
import ar.edu.itba.paw.services.SavedSearchWithCategoryNames;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Una busqueda guardada lista para mostrarse ("Mis listas" y la confirmacion de la baja): los nombres de sus
 * categorias y barrios, el rango de precio y fechas tal como los eligio la persona, y el link a /eventos con esos
 * mismos filtros. Es solo para la vista: no tiene logica de negocio.
 */
public final class SavedSearchView {

    private final long id;
    private final List<String> categoryNames;
    private final List<String> barrioNames;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final Date fromDate;
    private final Date lastDate;
    private final String eventsPath;

    private SavedSearchView(final long id, final List<String> categoryNames, final List<String> barrioNames,
                            final BigDecimal minPrice, final BigDecimal maxPrice, final Date fromDate,
                            final Date lastDate, final String eventsPath) {
        this.id = id;
        this.categoryNames = categoryNames;
        this.barrioNames = barrioNames;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.fromDate = fromDate;
        this.lastDate = lastDate;
        this.eventsPath = eventsPath;
    }

    /** Los nombres de las categorias ya vienen resueltos por el service (ver MyAccountService). */
    public static SavedSearchView of(final SavedSearchWithCategoryNames named) {
        final SavedSearch search = named.getSearch();
        final List<String> categories = named.getCategoryNames();
        final List<String> barrios = search.getBarrios().stream()
                .sorted(Comparator.comparing(Barrio::getDisplayName))
                .map(Barrio::getDisplayName)
                .collect(Collectors.toList());
        // En la base "hasta" es el dia siguiente a las 00:00 (exclusivo): la persona eligio el dia anterior.
        final Date lastDate = search.getToDate() == null ? null : addDays(search.getToDate(), -1);
        return new SavedSearchView(search.getId(), Collections.unmodifiableList(categories),
                Collections.unmodifiableList(barrios), search.getMinPrice(), search.getMaxPrice(),
                search.getFromDate(), lastDate, eventsPathOf(search, lastDate));
    }

    // /eventos con los mismos filtros guardados (mismos nombres de parametros que EventSearchForm).
    private static String eventsPathOf(final SavedSearch search, final Date lastDate) {
        final StringBuilder query = new StringBuilder();
        search.getCategoryIds().stream().sorted().forEach(c -> append(query, "categoria", String.valueOf(c)));
        search.getBarrios().stream().map(Enum::name).sorted().forEach(b -> append(query, "barrio", b));
        if (search.getMinPrice() != null) {
            append(query, "precioMin", search.getMinPrice().stripTrailingZeros().toPlainString());
        }
        if (search.getMaxPrice() != null) {
            append(query, "precioMax", search.getMaxPrice().stripTrailingZeros().toPlainString());
        }
        if (search.getFromDate() != null) {
            append(query, "fechaDesde", new SimpleDateFormat("yyyy-MM-dd").format(search.getFromDate()));
        }
        if (lastDate != null) {
            append(query, "fechaHasta", new SimpleDateFormat("yyyy-MM-dd").format(lastDate));
        }
        return query.length() == 0 ? "/eventos" : "/eventos?" + query.substring(0, query.length() - 1);
    }

    private static void append(final StringBuilder query, final String name, final String value) {
        query.append(name).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
    }

    private static Date addDays(final Date date, final int days) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    public long getId() { return id; }
    public List<String> getCategoryNames() { return categoryNames; }
    public List<String> getBarrioNames() { return barrioNames; }
    // Los mismos nombres en un solo texto, separados por coma, para mostrarlos en un renglon.
    public String getCategoriesText() { return String.join(", ", categoryNames); }
    public String getBarriosText() { return String.join(", ", barrioNames); }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public Date getFromDate() { return fromDate; }
    // El ultimo dia que cubre la busqueda (inclusive), tal como lo eligio la persona.
    public Date getLastDate() { return lastDate; }
    public String getEventsPath() { return eventsPath; }

    // Sin ningun filtro guardado (los que no se guardan, como el puntaje, no cuentan).
    public boolean isWithoutFilters() {
        return categoryNames.isEmpty() && barrioNames.isEmpty() && minPrice == null && maxPrice == null
                && fromDate == null && lastDate == null;
    }
}
