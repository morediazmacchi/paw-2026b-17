package ar.edu.itba.paw.model;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class EventSearchCriteria {

    /**
     * Que es "quedan pocos cupos": entre 1 y 5 lugares libres. Los dos extremos
     * entran. Un evento lleno (0 libres) no cuenta, y uno sin cupo maximo tampoco:
     * nunca se le pueden acabar los lugares.
     *
     * Vive aca, en el modelo, y no en el controller ni en el DAO, porque es una
     * regla del dominio: asi nadie puede pedir otro rango desde la URL y no hay
     * dos definiciones distintas dando vueltas.
     */
    public static final int FEW_SPOTS_MIN = 1;
    public static final int FEW_SPOTS_MAX = 5;

    // Lo que se escribio en la barra de busqueda (null = no se busco texto). Se busca en el titulo y la descripcion;
    // las palabras que cuentan salen de SearchTerms.parse(). Se combina con AND con el resto de los filtros.
    private String text;
    private List<Long> categoryIds;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Date fromDate;
    private Date toDate;
    private List<Barrio> barrios;
    // Solo eventos cuyo creador tenga un promedio de estrellas (entre todos sus
    // eventos, ver ReviewDao.findByCreatorId) mayor o igual a este valor.
    private Integer minOrganizerRating;
    // Solo eventos a los que les quedan entre FEW_SPOTS_MIN y FEW_SPOTS_MAX
    // lugares libres. Es un si/no, no un rango que elija quien navega.
    private boolean onlyFewSpotsLeft;
    // Solo eventos sin lugares libres ("Sold out"). Se puede combinar con el de
    // arriba: si estan los dos, trae los de pocos cupos Y los agotados.
    private boolean onlySoldOut;
    // Solo eventos aprobados dentro de la ventana (approvedAfter, approvedUntil]. Las usan las busquedas guardadas
    // (ver SavedSearchService) para contar los eventos NUEVOS desde el ultimo aviso; el listado no las usa.
    private Date approvedAfter;
    private Date approvedUntil;
    // Quien esta navegando (null = anonimo). Sus propios eventos no se listan en el
    // home, y con EventSort.RECOMENDADOS el orden se calcula para esta persona.
    private Long viewerId;
    private EventSort sort = EventSort.FECHA_ASC;
    private int page = 1;
    private int pageSize = 12;

    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
    /** Las palabras que se buscan (vacia si el texto es nulo, en blanco o solo signos: entonces no filtra nada). */
    public List<String> getSearchTerms() { return SearchTerms.parse(text); }
    public boolean hasSearchTerms() { return !getSearchTerms().isEmpty(); }
    public List<Long> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(final List<Long> categoryIds) { this.categoryIds = categoryIds; }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(final BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(final BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public Date getFromDate() { return fromDate; }
    public void setFromDate(final Date fromDate) { this.fromDate = fromDate; }
    public Date getToDate() { return toDate; }
    public void setToDate(final Date toDate) { this.toDate = toDate; }
    public List<Barrio> getBarrios() { return barrios; }
    public void setBarrios(final List<Barrio> barrios) { this.barrios = barrios; }
    public Integer getMinOrganizerRating() { return minOrganizerRating; }
    public void setMinOrganizerRating(final Integer minOrganizerRating) { this.minOrganizerRating = minOrganizerRating; }
    public boolean isOnlyFewSpotsLeft() { return onlyFewSpotsLeft; }
    public void setOnlyFewSpotsLeft(final boolean onlyFewSpotsLeft) { this.onlyFewSpotsLeft = onlyFewSpotsLeft; }
    public boolean isOnlySoldOut() { return onlySoldOut; }
    public void setOnlySoldOut(final boolean onlySoldOut) { this.onlySoldOut = onlySoldOut; }
    public Date getApprovedAfter() { return approvedAfter; }
    public void setApprovedAfter(final Date approvedAfter) { this.approvedAfter = approvedAfter; }
    public Date getApprovedUntil() { return approvedUntil; }
    public void setApprovedUntil(final Date approvedUntil) { this.approvedUntil = approvedUntil; }
    public Long getViewerId() { return viewerId; }
    public void setViewerId(final Long viewerId) { this.viewerId = viewerId; }
    public EventSort getSort() { return sort; }
    public void setSort(final EventSort sort) { this.sort = sort; }
    public int getPage() { return page; }
    public void setPage(final int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(final int pageSize) { this.pageSize = pageSize; }
}
