package ar.edu.itba.paw.model;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "password", length = 100)
    private String password;

    // Los roles viven en la tabla user_role (PK compuesta user_id + role). EAGER: quien
    // pide un usuario lo necesita ya cargado (autenticacion), no en un segundo paso.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", length = 30, nullable = false)
    private Set<String> roles = new HashSet<>();

    // Cuentas creadas antes de este feature (o "fantasma", sin password) quedan
    // en true: solo un register() nuevo empieza en false y necesita confirmar el mail.
    @Column(name = "verified", nullable = false)
    private boolean verified = true;

    // "es"/"en", o null si todavia no eligio ninguno a mano (ver LocaleController):
    // en ese caso la pagina usa el idioma de la cookie/navegador y los mails el
    // default de NotificationServiceImpl, no este campo.
    @Column(name = "preferred_locale", length = 5)
    private String locale;

    // Un admin suspendio la cuenta (ver UserService.suspend()/ModerationService):
    // UserDetailsServiceImpl la marca disabled=true igual que una cuenta sin
    // verificar, asi que no puede loguearse hasta que un admin la reactive.
    // false por default, igual que la columna.
    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    public User() {
        // Solo para Hibernate (y para los que arman un User a mano, ver tests de services)
    }

    public User(final String email, final String name) {
        this.email = email;
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
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
    public boolean isSuspended() { return suspended; }
    public void setSuspended(final boolean suspended) { this.suspended = suspended; }
}
