package ar.edu.itba.paw.model;

import javax.persistence.Column;
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
import javax.persistence.UniqueConstraint;
import java.util.Date;

@Entity
@Table(name = "registration", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "email"}))
public class Registration {

    // null = todavia no existe en la base (lo asigna la base al insertar).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Relacion de verdad con el evento (en vez de guardar solo el numero): muchas inscripciones
    // pertenecen a UN evento (N:1). LAZY: Hibernate no trae el evento de la base hasta que alguien
    // lo usa; mientras tanto deja un "representante" que ya conoce el id (por eso getEventId()
    // no genera ninguna consulta).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    @Column(name = "email", length = 150, nullable = false)
    private String email;
    @Column(name = "name", length = 150)
    private String name;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "registered_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date registeredAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private RegistrationStatus status;
    // Quien se anota (FK contra users.id, ver schema.sql). Siempre esta logueado
    // (RegistrationServiceImpl.register() lo exige), asi que esta resuelto para una fila nueva.
    // Null solo en una fila vieja que todavia no paso por el backfill de postgres-migrations.sql.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public Registration() {
    }

    public Registration(final Event event, final User user, final String email, final String name,
                        final RegistrationStatus status) {
        this.event = event;
        this.user = user;
        this.email = email;
        this.name = name;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    // Atajo para no tener que escribir getEvent().getId() en todos lados. No dispara ninguna
    // consulta: el id del evento ya esta en la fila de la inscripcion.
    public long getEventId() { return event.getId(); }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    // Mismo atajo que getEventId(); null si la inscripcion no tiene usuario (fila vieja).
    public Long getUserId() { return user == null ? null : user.getId(); }
    public Date getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(final Date registeredAt) { this.registeredAt = registeredAt; }
    // Como mostrar a esta persona en un aviso ("Juan se anoto"): el nombre si lo
    // dejo, y si no (los usuarios fantasma pueden no tenerlo) el email.
    public String getDisplayName() {
        return name == null || name.trim().isEmpty() ? email : name;
    }

    public RegistrationStatus getStatus() { return status; }

    // Al organizador se le puede sacar el lugar solo si lo tiene confirmado (ver RegistrationService.revoke()).
    public boolean isRevocable() { return status == RegistrationStatus.CONFIRMED; }
    public void setStatus(final RegistrationStatus status) { this.status = status; }
}
