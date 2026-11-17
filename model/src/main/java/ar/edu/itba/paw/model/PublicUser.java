package ar.edu.itba.paw.model;

// Lo unico de una cuenta que muestra su perfil publico: id, nombre, biografia y el id de su portada. Es una proyeccion
// (no una entidad): se arma sin cargar el User, asi que NO trae el mail ni la clave, ni siquiera por error: lo que no
// esta en el tipo no se puede mostrar. La devuelve UserDao.findActivePublicById().
public class PublicUser {

    private final long id;
    private final String name;
    private final String bio;
    private final Long coverImageId;

    public PublicUser(final long id, final String name, final String bio, final Long coverImageId) {
        this.id = id;
        this.name = name;
        this.bio = bio;
        this.coverImageId = coverImageId;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    // null si no escribio ninguna.
    public String getBio() {
        return bio;
    }

    // El id de la imagen de portada (no la imagen: sus bytes se piden aparte, en /usuarios/{id}/portada); null si no
    // eligio ninguna.
    public Long getCoverImageId() {
        return coverImageId;
    }

    // Misma regla que User.isSameAccountAs(): quien mira es esta misma cuenta (un anonimo, null, nunca lo es).
    public boolean isSameAccountAs(final Long userId) {
        return userId != null && userId == id;
    }
}
