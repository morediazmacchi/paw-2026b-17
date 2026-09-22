package ar.edu.itba.paw.model;

// Una de las categorias elegidas para un evento (relacion N:M, ver tabla
// event_category). categoryName solo se llena al leer (JOIN con category, no
// es una columna de event_category); extraInfo es el texto libre y opcional
// que el creador carga para ESA categoria en ESE evento (ej. que artistas van
// a tocar si la categoria es Arte).
public class EventCategoryInfo {

    private long categoryId;
    private String categoryName;
    private String extraInfo;

    public EventCategoryInfo() {
    }

    public EventCategoryInfo(final long categoryId) {
        this.categoryId = categoryId;
    }

    public EventCategoryInfo(final long categoryId, final String extraInfo) {
        this.categoryId = categoryId;
        this.extraInfo = extraInfo;
    }

    public long getCategoryId() { return categoryId; }
    public void setCategoryId(final long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(final String categoryName) { this.categoryName = categoryName; }
    public String getExtraInfo() { return extraInfo; }
    public void setExtraInfo(final String extraInfo) { this.extraInfo = extraInfo; }
}
