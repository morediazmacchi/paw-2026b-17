package ar.edu.itba.paw.model;

import java.util.HashSet;
import java.util.Set;

public class User {

    private long id;
    private String email;
    private String name;
    private String password;
    private Set<String> roles = new HashSet<>();
    // Cuentas creadas antes de este feature (o "fantasma", sin password) quedan
    // en true: solo un register() nuevo empieza en false y necesita confirmar el mail.
    private boolean verified = true;
    // "es"/"en", o null si todavia no eligio ninguno a mano (ver LocaleController):
    // en ese caso la pagina usa el idioma de la cookie/navegador y los mails el
    // default de NotificationServiceImpl, no este campo.
    private String locale;

    public User() {
    }

    public User(final String email, final String name) {
        this.email = email;
        this.name = name;
    }

    public long getId() { return id; }
    public void setId(final long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(final String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(final String name) { this.name = name; }
    public String getPassword() { return password; }
    public void setPassword(final String password) { this.password = password; }
    public Set<String> getRoles() { return roles; }
    public void setRoles(final Set<String> roles) { this.roles = roles; }
    public boolean isVerified() { return verified; }
    public void setVerified(final boolean verified) { this.verified = verified; }
    public String getLocale() { return locale; }
    public void setLocale(final String locale) { this.locale = locale; }
}
