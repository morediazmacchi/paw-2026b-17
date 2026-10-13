package ar.edu.itba.paw.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import java.util.Date;

// La PK es el token mismo (aleatorio, lo genera el service): no hay id numerico ni
// @GeneratedValue. created_at de la tabla se llena con su DEFAULT y no se mapea.
@Entity
@Table(name = "verification_token")
public class VerificationToken {

    @Id
    @Column(name = "token", length = 64, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private TokenPurpose purpose;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "expires_at", nullable = false)
    private Date expiresAt;

    public VerificationToken() {
        // Solo para Hibernate
    }

    public VerificationToken(final String token, final User user, final TokenPurpose purpose, final Date expiresAt) {
        this.token = token;
        this.user = user;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public void setToken(final String token) { this.token = token; }
    public User getUser() { return user; }
    public void setUser(final User user) { this.user = user; }
    public long getUserId() { return user.getId(); }
    public TokenPurpose getPurpose() { return purpose; }
    public void setPurpose(final TokenPurpose purpose) { this.purpose = purpose; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(final Date expiresAt) { this.expiresAt = expiresAt; }
}
