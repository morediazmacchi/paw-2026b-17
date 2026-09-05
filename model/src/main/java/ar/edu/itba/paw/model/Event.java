package ar.edu.itba.paw.model;

import java.util.Date;

public class Event {

    private long id;
    private String title;
    private String description;
    private Date eventDate;
    private String address;
    private Barrio neighborhood;
    private Integer imageId;
    private double price;
    private Integer maxCapacity;
    private String creatorEmail;
    private long categoryId;
    private String categoryName; // solo se llena al hacer el JOIN con category, no es una columna de event
    private Date createdAt;

    public Event() {
    }

    public Event(final String title, final String description, final Date eventDate, final String address,
                 final Barrio neighborhood, final Integer imageId, final double price, final Integer maxCapacity,
                 final String creatorEmail, final long categoryId) {
        this.title = title;
        this.description = description;
        this.eventDate = eventDate;
        this.address = address;
        this.neighborhood = neighborhood;
        this.imageId = imageId;
        this.price = price;
        this.maxCapacity = maxCapacity;
        this.creatorEmail = creatorEmail;
        this.categoryId = categoryId;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(final String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
    public Date getEventDate() { return eventDate; }
    public void setEventDate(final Date eventDate) { this.eventDate = eventDate; }
    public String getAddress() { return address; }
    public void setAddress(final String address) { this.address = address; }
    public Barrio getNeighborhood() { return neighborhood; }
    public void setNeighborhood(final Barrio neighborhood) { this.neighborhood = neighborhood; }
    public Integer getImageId() { return imageId; }
    public void setImageId(final Integer imageId) { this.imageId = imageId; }
    public double getPrice() { return price; }
    public void setPrice(final double price) { this.price = price; }
    public Integer getMaxCapacity() { return maxCapacity; }
    public void setMaxCapacity(final Integer maxCapacity) { this.maxCapacity = maxCapacity; }
    public String getCreatorEmail() { return creatorEmail; }
    public void setCreatorEmail(final String creatorEmail) { this.creatorEmail = creatorEmail; }
    public long getCategoryId() { return categoryId; }
    public void setCategoryId(final long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(final String categoryName) { this.categoryName = categoryName; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }

    public boolean isFree() { return price == 0; }
}
