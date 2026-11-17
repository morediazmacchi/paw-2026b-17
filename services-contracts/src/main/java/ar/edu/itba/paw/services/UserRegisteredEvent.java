package ar.edu.itba.paw.services;

// Hecho de dominio: una persona se registro (o completo una cuenta fantasma) y ahora tiene id. Lo publica
// UserServiceImpl DENTRO de su transaccion; quienes guardaron cosas a nombre de ese mail cuando todavia no tenia
// cuenta (invitaciones, avisos de la bandeja) las enlazan a la cuenta (ver UserRegistrationListener). Es un POJO:
// UserService anuncia lo que paso y no conoce a quienes lo escuchan (asi no depende de ellos, que si dependen de el).
public class UserRegisteredEvent {

    private final String email;
    private final long userId;

    public UserRegisteredEvent(final String email, final long userId) {
        this.email = email;
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public long getUserId() {
        return userId;
    }
}
