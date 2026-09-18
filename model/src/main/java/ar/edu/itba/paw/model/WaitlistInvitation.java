package ar.edu.itba.paw.model;

import java.util.Date;

/**
 * El lugar que se le ofrecio a alguien de la lista de espera cuando se libero
 * un cupo. Mientras esta PENDING y no vencio, ese cupo queda reservado: no lo
 * ocupa nadie, pero tampoco esta libre.
 */
public class WaitlistInvitation {

    private long id;
    private long registrationId;
    private String token;
    private InvitationStatus status;
    private Date sentAt;
    private Date expiresAt;

    public WaitlistInvitation() {
    }

    public WaitlistInvitation(final long registrationId, final String token, final Date expiresAt) {
        this.registrationId = registrationId;
        this.token = token;
        this.status = InvitationStatus.PENDING;
        this.expiresAt = expiresAt;
    }

    /**
     * Recibe el "ahora" en vez de leer el reloj: asi el mismo instante vale para
     * toda una pasada del reconcile, y los tests pueden pararse en una fecha fija.
     */
    public boolean isExpiredAt(final Date now) {
        return expiresAt != null && expiresAt.before(now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public long getRegistrationId() { return registrationId; }
    public void setRegistrationId(final long registrationId) { this.registrationId = registrationId; }
    public String getToken() { return token; }
    public void setToken(final String token) { this.token = token; }
    public InvitationStatus getStatus() { return status; }
    public void setStatus(final InvitationStatus status) { this.status = status; }
    public Date getSentAt() { return sentAt; }
    public void setSentAt(final Date sentAt) { this.sentAt = sentAt; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(final Date expiresAt) { this.expiresAt = expiresAt; }
}
