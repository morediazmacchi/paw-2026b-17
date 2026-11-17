package ar.edu.itba.paw.model;

// Lo minimo de quien sigue a un organizador para mandarle el mail de "publico un evento nuevo": a donde (mail),
// como saludarlo (nombre) y en que idioma (locale). Es una proyeccion, no una entidad: no trae la contrasena,
// los roles ni el resto de la cuenta, y el mail (que sale en otro hilo, despues del commit) ya tiene el idioma
// sin volver a la base por cada seguidor. La devuelve FollowDao.findFollowers().
public class FollowerContact {

    private final long id;
    private final String email;
    private final String name;
    private final String locale;

    public FollowerContact(final long id, final String email, final String name, final String locale) {
        this.id = id;
        this.email = email;
        this.name = name;
        this.locale = locale;
    }

    public long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    // Puede ser null (cuentas viejas sin nombre): el mail saluda con la parte de antes de la @.
    public String getName() {
        return name;
    }

    // Puede ser null (nunca eligio idioma): el mail sale en el idioma por defecto.
    public String getLocale() {
        return locale;
    }
}
