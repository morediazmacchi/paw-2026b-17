package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

// Una pregunta de un usuario sobre un evento, con la respuesta del organizador
// (null mientras no respondio).
@Entity
@Table(name = "event_question")
public class EventQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Relacion con el evento (muchos N:1). LAZY: Hibernate trae el evento de la base recien
    // cuando alguien lo usa; mientras tanto deja un "representante" que ya conoce el id.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    // Quien pregunto.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "question_text", length = 300, nullable = false)
    private String text;
    @Column(name = "answer_text", length = 300)
    private String answer;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;
    @Column(name = "answered_at")
    @Temporal(TemporalType.TIMESTAMP)
    private Date answeredAt;
    public EventQuestion() {
    }

    public EventQuestion(final Event event, final User user, final String text) {
        this.event = event;
        this.user = user;
        this.text = text;
    }

    public boolean isAnswered() { return answer != null; }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    public long getEventId() { return event.getId(); }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    public long getUserId() { return user.getId(); }
    public String getText() { return text; }
    public void setText(final String text) { this.text = text; }
    public String getAnswer() { return answer; }
    public void setAnswer(final String answer) { this.answer = answer; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
    public Date getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(final Date answeredAt) { this.answeredAt = answeredAt; }
    // Nombre y mail de quien pregunto: salen del usuario relacionado (antes eran campos
    // aparte que el DAO completaba con otra consulta). EventQuestionJpaDao trae al usuario junto
    // con la pregunta (JOIN FETCH), asi que se pueden leer aunque la transaccion ya cerro.
    public String getAskerName() { return user.getName(); }
    public String getAskerEmail() { return user.getEmail(); }
}
