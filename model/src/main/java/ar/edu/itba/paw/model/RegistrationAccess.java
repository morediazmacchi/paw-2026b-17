package ar.edu.itba.paw.model;

// Lo minimo de una inscripcion para decidir un permiso (ver EventSecurity: isRegistrant,
// isOwnerOfRegistrationEvent, isConfirmedAttendee y la rama privada de canView): de quien es, de
// que evento y en que estado esta. Es una proyeccion (no una entidad): se arma con una consulta de
// tres columnas, sin cargar la inscripcion ni sus relaciones. La devuelve RegistrationDao.
public class RegistrationAccess {

    private final Long userId;
    private final long eventId;
    private final RegistrationStatus status;

    public RegistrationAccess(final Long userId, final long eventId, final RegistrationStatus status) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = status;
    }

    // null en una inscripcion que no tiene cuenta asociada.
    public Long getUserId() {
        return userId;
    }

    public long getEventId() {
        return eventId;
    }

    public RegistrationStatus getStatus() {
        return status;
    }
}
