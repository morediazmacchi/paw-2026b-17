package ar.edu.itba.paw.model;

import java.util.Date;

// Una pregunta de un usuario sobre un evento, con la respuesta del organizador
// (null mientras no respondio). askerName/askerEmail no son columnas propias:
// vienen del JOIN con users al leer.
public class EventQuestion {

    private long id;
    private long eventId;
    private long userId;
    private String text;
    private String answer;
    private Date createdAt;
    private Date answeredAt;
    private String askerName;
    private String askerEmail;

    public EventQuestion() {
    }

    public EventQuestion(final long eventId, final long userId, final String text) {
        this.eventId = eventId;
        this.userId = userId;
        this.text = text;
    }

    public boolean isAnswered() { return answer != null; }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getEventId() { return eventId; }
    public void setEventId(final long eventId) { this.eventId = eventId; }
    public long getUserId() { return userId; }
    public void setUserId(final long userId) { this.userId = userId; }
    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
    public String getAnswer() { return answer; }
    public void setAnswer(final String answer) { this.answer = answer; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public Date getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(final Date answeredAt) { this.answeredAt = answeredAt; }
    public String getAskerName() { return askerName; }
    public void setAskerName(final String askerName) { this.askerName = askerName; }
    public String getAskerEmail() { return askerEmail; }
    public void setAskerEmail(final String askerEmail) { this.askerEmail = askerEmail; }
}
