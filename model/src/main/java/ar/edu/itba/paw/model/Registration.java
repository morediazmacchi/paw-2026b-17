package ar.edu.itba.paw.model;

import java.util.Date;

public class Registration {

    // Tienen que coincidir con el CHECK de la columna status en la tabla registration.
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_WAITLISTED = "waitlisted";

    private long id;
    private long eventId;
    private String email;
    private String name;
    private Date registeredAt;
    private String status;

    public Registration() {
    }

    public Registration(final long eventId, final String email, final String name, final String status) {
        this.eventId = eventId;
        this.email = email;
        this.name = name;
        this.status = status;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public Date getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(final Date registeredAt) { this.registeredAt = registeredAt; }
    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }
}
