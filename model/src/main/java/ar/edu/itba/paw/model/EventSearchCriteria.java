package ar.edu.itba.paw.model;

import java.util.Date;
import java.util.List;

public class EventSearchCriteria {

    private List<Long> categoryIds;
    private Double minPrice;
    private Double maxPrice;
    private Date fromDate;
    private Date toDate;
    private List<Barrio> barrios;
    private EventSort sort = EventSort.FECHA;
    private int page = 1;
    private int pageSize = 12;

    public List<Long> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(final List<Long> categoryIds) { this.categoryIds = categoryIds; }
    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(final Double minPrice) { this.minPrice = minPrice; }
    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(final Double maxPrice) { this.maxPrice = maxPrice; }
    public Date getFromDate() { return fromDate; }
    public void setFromDate(final Date fromDate) { this.fromDate = fromDate; }
    public Date getToDate() { return toDate; }
    public void setToDate(final Date toDate) { this.toDate = toDate; }
    public List<Barrio> getBarrios() { return barrios; }
    public void setBarrios(final List<Barrio> barrios) { this.barrios = barrios; }
    public EventSort getSort() { return sort; }
    public void setSort(final EventSort sort) { this.sort = sort; }
    public int getPage() { return page; }
    public void setPage(final int page) { this.page = page; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(final int pageSize) { this.pageSize = pageSize; }
}
