package ar.edu.itba.paw.model;

import java.util.Date;

public class Review {

    private long id;
    private long eventId;
    private long userId;
    private int rating;
    private String comment;
    private Date createdAt;

    public Review() {
    }

    public Review(final long eventId, final long userId, final int rating, final String comment) {
        this.eventId = eventId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public long getUserId() { return userId; }
    public void setUserId(final long userId) { this.userId = userId; }
    public int getRating() { return rating; }
    public void setRating(final int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(final String comment) { this.comment = comment; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
}
