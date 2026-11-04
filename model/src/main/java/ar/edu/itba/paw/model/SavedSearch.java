package ar.edu.itba.paw.model;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

// Una busqueda guardada ("watchlist"): los filtros de /eventos que una persona guardo para que le avisen cuando se
// publique un evento nuevo que los cumpla (ver SavedSearchService). Guarda solo los filtros que describen al evento
// en si (categorias, barrios, precio y fechas). Los que cambian solos con el tiempo (cupos libres, puntaje del
// organizador) no tienen sentido para "avisame de eventos nuevos" y no se guardan.
@Entity
@Table(name = "saved_search")
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ElementCollection
    @CollectionTable(name = "saved_search_category", joinColumns = @JoinColumn(name = "saved_search_id"))
    @Column(name = "category_id", nullable = false)
    private Set<Long> categoryIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "saved_search_barrio", joinColumns = @JoinColumn(name = "saved_search_id"))
    @Column(name = "barrio", length = 80, nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Barrio> barrios = new HashSet<>();

    @Column(name = "min_price", precision = 10, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 10, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "from_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date fromDate;

    // Exclusiva: el dia siguiente al "hasta" a las 00:00, igual que EventSearchCriteria.getToDate().
    @Column(name = "to_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date toDate;

    // Los filtros escritos siempre de la misma forma (ver keyOf()). Con UNIQUE (user_id, filters_key) en la base,
    // la misma persona no puede guardar dos veces la misma busqueda, y saber si ya la guardo es una sola consulta.
    @Column(name = "filters_key", length = 2000, nullable = false)
    private String filtersKey;

    // La completa la base (DEFAULT now()).
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    // Hasta cuando ya se le aviso: el proximo aviso cuenta solo los eventos aprobados despues de esto. Arranca en el
    // momento de guardar: lo que ya estaba publicado cuando la guardo no es "nuevo".
    @Column(name = "last_checked_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastCheckedAt;

    public SavedSearch() {
        // Solo para Hibernate
    }

    public SavedSearch(final User user, final Collection<Long> categoryIds, final Collection<Barrio> barrios,
                       final BigDecimal minPrice, final BigDecimal maxPrice, final Date fromDate, final Date toDate) {
        this.user = user;
        if (categoryIds != null) {
            this.categoryIds.addAll(categoryIds);
        }
        if (barrios != null) {
            this.barrios.addAll(barrios);
        }
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.filtersKey = keyOf(categoryIds, barrios, minPrice, maxPrice, fromDate, toDate);
        this.lastCheckedAt = new Date();
    }

    // Los filtros que se guardan, como texto canonico: el orden en que se eligieron las categorias o los barrios no
    // importa, y 100 y 100.00 son el mismo precio. Es la UNICA definicion de "misma busqueda": la usan el
    // constructor y SavedSearchService.isSaved()/save().
    public static String keyOf(final Collection<Long> categoryIds, final Collection<Barrio> barrios,
                               final BigDecimal minPrice, final BigDecimal maxPrice,
                               final Date fromDate, final Date toDate) {
        final String categories = categoryIds == null ? ""
                : categoryIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
        final String barrioNames = barrios == null ? ""
                : barrios.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        return "c=" + categories + ";b=" + barrioNames + ";min=" + plain(minPrice) + ";max=" + plain(maxPrice)
                + ";from=" + millis(fromDate) + ";to=" + millis(toDate);
    }

    private static String plain(final BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String millis(final Date value) {
        return value == null ? "" : String.valueOf(value.getTime());
    }

    // Los filtros guardados como criterio de busqueda (el mismo que arma /eventos), para buscar con el mismo SQL
    // del listado los eventos que la cumplen.
    public EventSearchCriteria toCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setCategoryIds(categoryIds.isEmpty() ? null : new ArrayList<>(categoryIds));
        criteria.setBarrios(barrios.isEmpty() ? null : new ArrayList<>(barrios));
        criteria.setMinPrice(minPrice);
        criteria.setMaxPrice(maxPrice);
        criteria.setFromDate(fromDate);
        criteria.setToDate(toDate);
        return criteria;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Long getUserId() { return user == null ? null : user.getId(); }
    public Set<Long> getCategoryIds() { return categoryIds; }
    public Set<Barrio> getBarrios() { return barrios; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public Date getFromDate() { return fromDate; }
    public Date getToDate() { return toDate; }

    public String getFiltersKey() { return filtersKey; }
    public Date getCreatedAt() { return createdAt; }
    public Date getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(final Date lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
}
