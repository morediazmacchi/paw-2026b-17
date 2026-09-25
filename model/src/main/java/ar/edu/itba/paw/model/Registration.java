package ar.edu.itba.paw.model;

import java.util.Date;

public class Registration {

    private long id;
    private long eventId;
    private String email;
    private String name;
    private Date registeredAt;
    private RegistrationStatus status;

    public Registration() {
    }

    public Registration(final long eventId, final String email, final String name,
                        final RegistrationStatus status) {
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
    public RegistrationStatus getStatus() { return status; }
    public void setStatus(final RegistrationStatus status) { this.status = status; }
}
