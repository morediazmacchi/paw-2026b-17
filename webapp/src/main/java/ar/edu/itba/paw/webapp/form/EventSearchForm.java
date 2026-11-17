// Filtros de /eventos (ver EventController.list()). Los campos tienen su tipo (numero, fecha, orden...) y los
// convierte Spring al enlazar el formulario: un valor que no se entiende (?precioMin=abc) queda como un error del
// BindingResult en ese campo y el campo queda sin valor. A diferencia de EventForm, un filtro invalido no rechaza
// nada: la busqueda sigue mostrando resultados sin ese filtro (el controller no corta por los errores). Los rangos
// al reves los valida @ValidSearchRanges (ver isPriceRangeInvalid()/isDateRangeInvalid()).
package ar.edu.itba.paw.webapp.form;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.SearchTerms;
import ar.edu.itba.paw.webapp.validation.FilterRanges;
import ar.edu.itba.paw.webapp.validation.ValidSearchRanges;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@ValidSearchRanges
public class EventSearchForm implements FilterRanges {

    // El formato de las fechas en la URL y en los campos del formulario (el que manda el almanaque).
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    // Lo que se escribio en la barra de busqueda (?q=). No es un filtro mas: no cuenta para hasActiveFilters() (que
    // activa "Guardar busqueda": una busqueda guardada son los filtros, no el texto, asi que escribir solo texto no
    // deja nada para guardar).
    private String q;
    private List<Long> categoria;
    private BigDecimal precioMin;
    private BigDecimal precioMax;
    @DateTimeFormat(pattern = DATE_PATTERN)
    private Date fechaDesde;
    @DateTimeFormat(pattern = DATE_PATTERN)
    private Date fechaHasta;
    private List<Barrio> barrio;
    private Integer puntajeMin;
    // Casillas: Boolean y no boolean para que un valor vacio no sea un error.
    private Boolean pocosCupos;
    private Boolean agotados;
    // Pagina pedida (desde 1). Sin pedir ninguna, o con una que no es un numero, es la primera: el service ajusta
    // el resto (una pagina mas alla del total cae a la ultima).
    private Integer page;
    private EventSort sort;

    public String getQ() { return q; }
    public void setQ(final String q) { this.q = q; }

    // El texto como se lo vuelve a mostrar y se lo busca: sin espacios de mas y acotado (no es un error escribir de mas:
    // se ignora lo que sobra, igual que con los otros filtros). "" si no hay.
    public String getSearchText() {
        if (q == null) {
            return "";
        }
        final String trimmed = q.trim();
        return trimmed.length() > SearchTerms.MAX_TEXT_LENGTH ? trimmed.substring(0, SearchTerms.MAX_TEXT_LENGTH) : trimmed;
    }

    // Si el texto tiene alguna palabra que buscar (un "?!" o un "   " no es una busqueda).
    public boolean hasSearchText() {
        return !SearchTerms.parse(getSearchText()).isEmpty();
    }

    public List<Long> getCategoria() { return categoria; }
    public void setCategoria(final List<Long> categoria) { this.categoria = categoria; }

    public BigDecimal getPrecioMin() { return precioMin; }
    public void setPrecioMin(final BigDecimal precioMin) { this.precioMin = precioMin; }

    public BigDecimal getPrecioMax() { return precioMax; }
    public void setPrecioMax(final BigDecimal precioMax) { this.precioMax = precioMax; }

    public Date getFechaDesde() { return fechaDesde; }
    public void setFechaDesde(final Date fechaDesde) { this.fechaDesde = fechaDesde; }

    public Date getFechaHasta() { return fechaHasta; }
    public void setFechaHasta(final Date fechaHasta) { this.fechaHasta = fechaHasta; }

    public List<Barrio> getBarrio() { return barrio; }
    public void setBarrio(final List<Barrio> barrio) { this.barrio = barrio; }

    public Integer getPuntajeMin() { return puntajeMin; }
    public void setPuntajeMin(final Integer puntajeMin) { this.puntajeMin = puntajeMin; }

    public Boolean getPocosCupos() { return pocosCupos; }
    public void setPocosCupos(final Boolean pocosCupos) { this.pocosCupos = pocosCupos; }

    public Boolean getAgotados() { return agotados; }
    public void setAgotados(final Boolean agotados) { this.agotados = agotados; }

    public Integer getPage() { return page; }
    public void setPage(final Integer page) { this.page = page; }

    public EventSort getSort() { return sort; }
    public void setSort(final EventSort sort) { this.sort = sort; }

    // Para el JSP: tildar las casillas y volver a mandar los filtros (campos ocultos) tal como estan.
    public boolean isOnlyFewSpotsLeft() { return Boolean.TRUE.equals(pocosCupos); }
    public boolean isOnlySoldOut() { return Boolean.TRUE.equals(agotados); }

    // Las fechas como las manda el formulario ("yyyy-MM-dd"), "" si no hay: es lo que escriben los campos ocultos
    // del almanaque. Para los precios alcanza con el BigDecimal.
    public String getFechaDesdeIso() { return formatDate(fechaDesde); }
    public String getFechaHastaIso() { return formatDate(fechaHasta); }

    // Para marcar tildado en el JSP (selectedCategorias.contains(cat.id), selectedBarrios.contains(b)): null si no
    // eligio ninguno.
    public List<Long> getSelectedCategoryIds() {
        return categoria == null || categoria.isEmpty() ? null : categoria;
    }

    public List<Barrio> getSelectedBarrios() {
        return barrio == null || barrio.isEmpty() ? null : barrio;
    }

    // Si la persona eligio un orden (en la URL). Sin esto no se sabe si FECHA_ASC es lo que pidio o solo el default:
    // el controller necesita la diferencia, porque para quien tiene historial el orden por defecto es otro
    // (RECOMENDADOS).
    public boolean isSortExplicit() {
        return sort != null;
    }

    @Override
    public boolean isPriceRangeInvalid() {
        return precioMin != null && precioMax != null && precioMin.compareTo(precioMax) > 0;
    }

    @Override
    public boolean isDateRangeInvalid() {
        return fechaDesde != null && fechaHasta != null && fechaHasta.before(fechaDesde);
    }

    // "N eventos encontrados" / "Limpiar filtros" solo tienen sentido si el usuario realmente filtro algo. Un rango
    // invalido (que toCriteria() ignora) igual cuenta como "hay algo para limpiar".
    public boolean hasActiveFilters() {
        return notEmpty(categoria) || notEmpty(barrio)
                || precioMin != null || precioMax != null
                || fechaDesde != null || fechaHasta != null
                || puntajeMin != null || isOnlyFewSpotsLeft() || isOnlySoldOut();
    }

    public EventSearchCriteria toCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setText(hasSearchText() ? getSearchText() : null);
        criteria.setCategoryIds(getSelectedCategoryIds());
        criteria.setBarrios(getSelectedBarrios());

        final boolean priceRangeInvalid = isPriceRangeInvalid();
        criteria.setMinPrice(priceRangeInvalid ? null : precioMin);
        criteria.setMaxPrice(priceRangeInvalid ? null : precioMax);

        final boolean dateRangeInvalid = isDateRangeInvalid();
        criteria.setFromDate(dateRangeInvalid ? null : fechaDesde);
        criteria.setToDate(dateRangeInvalid ? null : endOfDay(fechaHasta));

        criteria.setMinOrganizerRating(puntajeMin);
        // Un si/no, no un rango: cuantos lugares son "pocos" lo define el modelo
        // (EventSearchCriteria.FEW_SPOTS_MIN/MAX), no lo que venga en la URL.
        criteria.setOnlyFewSpotsLeft(isOnlyFewSpotsLeft());
        criteria.setOnlySoldOut(isOnlySoldOut());
        // null = no pidio ninguno: el service decide el orden por defecto (ver EventService.search()).
        criteria.setSort(sort);
        criteria.setPage(page == null ? 1 : page);
        return criteria;
    }

    // Query string de los filtros para los links de paginacion (sin "page"). Solo
    // entran los valores con contenido y el sort si se eligio uno, asi la URL
    // queda limpia en vez de llena de "precioMin=&precioMax=&...". Si hay algun
    // filtro, termina en "&" para que el JSP pueda agregar "page=N" al final.
    public String toFilterQuery() {
        final StringBuilder query = new StringBuilder();
        appendParam(query, "q", getSearchText());
        if (categoria != null) {
            categoria.forEach(c -> appendParam(query, "categoria", String.valueOf(c)));
        }
        if (barrio != null) {
            barrio.forEach(b -> appendParam(query, "barrio", b.name()));
        }
        appendParam(query, "precioMin", precioMin == null ? null : precioMin.toPlainString());
        appendParam(query, "precioMax", precioMax == null ? null : precioMax.toPlainString());
        appendParam(query, "fechaDesde", getFechaDesdeIso());
        appendParam(query, "fechaHasta", getFechaHastaIso());
        appendParam(query, "puntajeMin", puntajeMin == null ? null : String.valueOf(puntajeMin));
        appendParam(query, "pocosCupos", isOnlyFewSpotsLeft() ? "true" : null);
        appendParam(query, "agotados", isOnlySoldOut() ? "true" : null);
        // Solo el orden que la persona eligio de verdad (aunque sea FECHA_ASC): si se omitiera,
        // al cambiar de pagina volveria el default, que puede ser RECOMENDADOS.
        if (sort != null) {
            appendParam(query, "sort", sort.name());
        }
        return query.toString();
    }

    private static void appendParam(final StringBuilder query, final String name, final String value) {
        if (value != null && !value.isEmpty()) {
            query.append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
        }
    }

    private static boolean notEmpty(final List<?> values) {
        return values != null && !values.isEmpty();
    }

    private static String formatDate(final Date date) {
        return date == null ? "" : new SimpleDateFormat(DATE_PATTERN).format(date);
    }

    // "Hasta" tiene que incluir todo ese dia: se corre al dia siguiente a las
    // 00:00 y en la query se compara con "<", no "<=".
    private static Date endOfDay(final Date date) {
        if (date == null) {
            return null;
        }
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }
}
