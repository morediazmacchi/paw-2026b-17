package ar.edu.itba.paw.model;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
public class User implements RoleHolder {

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
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30, nullable = false)
    private Set<Role> roles = new HashSet<>();

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

    // Presentacion libre que la persona escribe en su perfil publico (ver UserService.updateBio()). Null si todavia
    // no escribio nada.
    @Column(name = "bio", length = 500)
    private String bio;

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
    // Id de la foto de portada del perfil publico (tabla image). Solo el id, no la relacion: asi cargar un User nunca
    // trae los bytes de la imagen. Null = portada por defecto.
    @Column(name = "cover_image_id")
    private Long coverImageId;

    public Long getCoverImageId() { return coverImageId; }
    public void setCoverImageId(final Long coverImageId) { this.coverImageId = coverImageId; }

    public String getBio() { return bio; }
    public void setBio(final String bio) { this.bio = bio; }
    public String getPassword() { return password; }
    public void setPassword(final String password) { this.password = password; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(final Set<Role> roles) { this.roles = roles; }
    // Para no preguntar por el rol con un literal en cada lugar (ver Role).
    // Es la misma cuenta que ese id. Es la regla de "nadie actua sobre su propia cuenta" (suspenderse, sacarse el
    // rol ADMIN): la usan los services (OWN_ACCOUNT) y la pantalla de usuarios.
    public boolean isSameAccountAs(final Long userId) {
        return getId() != null && getId().equals(userId);
    }

    @Override
    public boolean isAdmin() { return roles.contains(Role.ADMIN); }
    @Override
    public boolean isSuperadmin() { return roles.contains(Role.SUPERADMIN); }
    public boolean isVerified() { return verified; }
    public void setVerified(final boolean verified) { this.verified = verified; }
    public String getLocale() { return locale; }
    public void setLocale(final String locale) { this.locale = locale; }
    public boolean isSuspended() { return suspended; }

    // Tiene clave (no es una fila "fantasma" de antes del login).
    public boolean hasPassword() { return password != null; }

    // La cuenta puede entrar: tiene clave, confirmo el mail y no esta suspendida. Es la definicion que lleva el login
    // (ver PlancitoUserDetails.isActive()): "habilitada" = clave + mail confirmado, "bloqueada" = suspendida.
    public boolean isActive() { return hasPassword() && isVerified() && !isSuspended(); }
    public void setSuspended(final boolean suspended) { this.suspended = suspended; }
}
