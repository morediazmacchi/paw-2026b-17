package ar.edu.itba.paw.model;

import java.util.Date;

public class VerificationToken {

    private String token;
    private long userId;
    private TokenPurpose purpose;
    private Date expiresAt;

    public VerificationToken() {
    }

    public VerificationToken(final String token, final long userId, final TokenPurpose purpose, final Date expiresAt) {
        this.token = token;
        this.userId = userId;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public String getToken() { return token; }
    public void setToken(final String token) { this.token = token; }
    public long getUserId() { return userId; }
    public void setUserId(final long userId) { this.userId = userId; }
    public TokenPurpose getPurpose() { return purpose; }
    public void setPurpose(final TokenPurpose purpose) { this.purpose = purpose; }
    public Date getExpiresAt() { return expiresAt; }
    public void setExpiresAt(final Date expiresAt) { this.expiresAt = expiresAt; }
}
