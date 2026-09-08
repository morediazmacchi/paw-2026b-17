package ar.edu.itba.paw.model;

import java.util.Date;

public class EventSearchCriteria {

    private Long categoryId;
    private Double minPrice;
    private Double maxPrice;
    private Date fromDate;
    private Date toDate;
    private Barrio barrio;
    private EventSort sort = EventSort.FECHA;
    private int page = 1;
    private int pageSize = 12;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(final Long categoryId) { this.categoryId = categoryId; }
    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(final Double minPrice) { this.minPrice = minPrice; }
    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(final Double maxPrice) { this.maxPrice = maxPrice; }
    public Date getFromDate() { return fromDate; }
    public void setFromDate(final Date fromDate) { this.fromDate = fromDate; }
    public Date getToDate() { return toDate; }
    public void setToDate(final Date toDate) { this.toDate = toDate; }
    public Barrio getBarrio() { return barrio; }
    public void setBarrio(final Barrio barrio) { this.barrio = barrio; }
    public EventSort getSort() { return sort; }
    public void setSort(final EventSort sort) { this.sort = sort; }
    public int getPage() { return page; }
    public void setPage(final int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(final int pageSize) { this.pageSize = pageSize; }
}
