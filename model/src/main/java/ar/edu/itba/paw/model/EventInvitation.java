package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

/**
 * Invitacion personal del creador a alguien para que pueda ver (y, si quiere,
 * anotarse) a un evento privado. Quien entra tiene que SER la persona
 * invitada: logueado con la cuenta enlazada a esta invitacion (ver userId
 * mas abajo y EventSecurity.canView() / EventAccessService.canView()). Ya no hay un token/link que valga
 * por si solo sin sesion (ver el comentario de la tabla event_invitation en
 * schema.sql).
 */
@Entity
@Table(name = "event_invitation", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "email"}))
public class EventInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Relacion con el evento (muchos N:1). LAZY: Hibernate trae el evento de la base recien
    // cuando alguien lo usa; mientras tanto deja un "representante" que ya conoce el id.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    @Column(name = "email", length = 150, nullable = false)
    private String email;
    @Column(name = "invited_by", length = 150, nullable = false)
    private String invitedBy;
    // FK de verdad contra users.id (ver schema.sql): quien invita es siempre
    // el creador del evento, logueado. Null solo en una fila vieja sin
    // backfillear todavia (ver postgres-migrations.sql).
    // Usuario que invito (FK; "invitedBy" de arriba es su mail, dato historico).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedByUser;
    // El INVITADO (no el que invita): puede no tener cuenta todavia, asi que
    // esto es optimista -- se completa si ya existia una cuenta al invitar
    // (ver EventInvitationServiceImpl.invite()), o mas adelante solo, cuando
    // esa persona se registra (ver UserServiceImpl.register()). Un valor en
    // null para siempre es un caso normal, no una fila vieja sin migrar.
    // Cuenta enlazada a la invitacion (null si la persona invitada todavia no tiene cuenta).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    // DEFAULT now() de la base: Hibernate no lo inserta ni lo pisa; se lee al cargar la fila.
    @Column(name = "created_at", insertable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdAt;

    public EventInvitation() {
    }

    public EventInvitation(final Event event, final String email, final String invitedBy) {
        this.event = event;
        this.email = email;
        this.invitedBy = invitedBy;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(final Event event) { this.event = event; }
    public long getEventId() { return event.getId(); }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getInvitedBy() { return invitedBy; }
    public void setInvitedBy(final String invitedBy) { this.invitedBy = invitedBy; }
    public User getInvitedByUser() { return invitedByUser; }
    public void setInvitedByUser(final User invitedByUser) { this.invitedByUser = invitedByUser; }
    public Long getInvitedByUserId() { return invitedByUser == null ? null : invitedByUser.getId(); }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    public Long getUserId() { return user == null ? null : user.getId(); }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(final Date createdAt) { this.createdAt = createdAt; }
}
