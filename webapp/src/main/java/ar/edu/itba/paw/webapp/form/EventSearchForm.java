// Filtros de /eventos (ver EventController.list()). A diferencia de EventForm,
// un filtro invalido no rechaza nada: la busqueda sigue mostrando resultados,
// solo se ignora ese filtro puntual y se avisa (ver isPriceRangeInvalid()/
// isDateRangeInvalid(), que el controller usa para armar filterErrors). Por
// eso esto no es @Valid + BindingResult, es un objeto que se parsea a si mismo.
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.webapp.util.Paging;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EventSearchForm {

    private List<String> categoria;
    private String precioMin;
    private String precioMax;
    private String fechaDesde;
    private String fechaHasta;
    private List<String> barrio;
    private String puntajeMin;
    private String pocosCupos;
    private String agotados;
    private String page;
    private String sort;

    public List<String> getCategoria() { return categoria; }
    public void setCategoria(final List<String> categoria) { this.categoria = categoria; }

    public String getPrecioMin() { return precioMin; }
    public void setPrecioMin(final String precioMin) { this.precioMin = precioMin; }

    public String getPrecioMax() { return precioMax; }
    public void setPrecioMax(final String precioMax) { this.precioMax = precioMax; }

    public String getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(final String fechaDesde) { this.fechaDesde = fechaDesde; }

    public String getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(final String fechaHasta) { this.fechaHasta = fechaHasta; }

    public List<String> getBarrio() { return barrio; }
    public void setBarrio(final List<String> barrio) { this.barrio = barrio; }

    public String getPuntajeMin() { return puntajeMin; }
    public void setPuntajeMin(final String puntajeMin) { this.puntajeMin = puntajeMin; }

    public String getPocosCupos() { return pocosCupos; }
    public void setPocosCupos(final String pocosCupos) { this.pocosCupos = pocosCupos; }

    public String getAgotados() { return agotados; }
    public void setAgotados(final String agotados) { this.agotados = agotados; }

    public String getPage() { return page; }
    public void setPage(final String page) { this.page = page; }

    public String getSort() { return sort; }
    public void setSort(final String sort) { this.sort = sort; }

    // Ya parseados: el JSP los usa para marcar tildado (selectedCategorias.contains(cat.id),
    // selectedBarrios.contains(b)). Cada entrada mal formada se descarta sola, no invalida las demas.
    public List<Long> getSelectedCategoryIds() {
        return parseLongList(categoria);
    }

    public List<Barrio> getSelectedBarrios() {
        return parseBarrioList(barrio);
    }

    // Siempre un valor valido (default FECHA_ASC): el JSP lo usa para marcar la
    // opcion elegida y los links de paginacion lo repiten tal cual.
    public EventSort getParsedSort() {
        return parseSort(sort);
    }

    public boolean isPriceRangeInvalid() {
        final BigDecimal min = parseDecimal(precioMin);
        final BigDecimal max = parseDecimal(precioMax);
        return min != null && max != null && min.compareTo(max) > 0;
    }

    public boolean isDateRangeInvalid() {
        final Date from = parseDate(fechaDesde);
        final Date to = parseDate(fechaHasta);
        return from != null && to != null && to.before(from);
    }

    // "N eventos encontrados" / "Limpiar filtros" solo tienen sentido si el
    // usuario realmente filtro algo. Mira lo crudo: un rango invalido (que
    // toCriteria() resetea a null) igual cuenta como "hay algo para limpiar".
    public boolean hasActiveFilters() {
        return notEmpty(categoria) || notEmpty(barrio)
                || notBlank(precioMin) || notBlank(precioMax)
                || notBlank(fechaDesde) || notBlank(fechaHasta)
                || notBlank(puntajeMin) || notBlank(pocosCupos) || notBlank(agotados);
    }

    public EventSearchCriteria toCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(getSelectedCategoryIds());
        criteria.setBarrios(getSelectedBarrios());

        final boolean priceRangeInvalid = isPriceRangeInvalid();
        criteria.setMinPrice(priceRangeInvalid ? null : parseDecimal(precioMin));
        criteria.setMaxPrice(priceRangeInvalid ? null : parseDecimal(precioMax));

        final boolean dateRangeInvalid = isDateRangeInvalid();
        criteria.setFromDate(dateRangeInvalid ? null : parseDate(fechaDesde));
        criteria.setToDate(dateRangeInvalid ? null : parseDateEndOfDay(fechaHasta));

        criteria.setMinOrganizerRating(parseInteger(puntajeMin));
        // Un si/no, no un rango: cuantos lugares son "pocos" lo define el modelo
        // (EventSearchCriteria.FEW_SPOTS_MIN/MAX), no lo que venga en la URL.
        criteria.setOnlyFewSpotsLeft(notBlank(pocosCupos));
        criteria.setOnlySoldOut(notBlank(agotados));
        criteria.setSort(getParsedSort());
        criteria.setPage(Paging.parsePage(page));
        return criteria;
    }

    private static boolean notBlank(final String value) {
        return value != null && !value.isEmpty();
    }

    private static boolean notEmpty(final List<String> values) {
        return values != null && !values.isEmpty();
    }

    private static Long parseLong(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    // Multi-seleccion: cada elemento se parsea por separado, el que este mal
    // formado se descarta solo (no invalida los demas). Lista vacia = sin filtro.
    private static List<Long> parseLongList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final List<Long> result = values.stream()
                .map(EventSearchForm::parseLong)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
    }

    private static Barrio parseBarrioFilter(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Barrio.valueOf(value);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Barrio> parseBarrioList(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        final List<Barrio> result = values.stream()
                .map(EventSearchForm::parseBarrioFilter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
    }

    private static BigDecimal parseDecimal(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static Date parseDate(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (final ParseException e) {
            return null;
        }
    }

    // "Hasta" tiene que incluir todo ese dia: se corre al dia siguiente a las
    // 00:00 y en la query se compara con "<", no "<=".
    private static Date parseDateEndOfDay(final String value) {
        final Date date = parseDate(value);
        if (date == null) {
            return null;
        }
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }

    private static Integer parseInteger(final String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private static EventSort parseSort(final String value) {
        if (value == null || value.isEmpty()) {
            return EventSort.FECHA_ASC;
        }
        try {
            return EventSort.valueOf(value);
        } catch (final IllegalArgumentException e) {
            // Un valor que no existe (link viejo con "FECHA", o la URL tocada a
            // mano) no es un error: se ordena como siempre, por fecha.
            return EventSort.FECHA_ASC;
        }
    }
}
