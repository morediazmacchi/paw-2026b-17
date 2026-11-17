package ar.edu.itba.paw.services;

// Hecho de dominio: un admin aprobo un evento, que recien ahora es "nuevo" para el publico. Lo publica
// EventServiceImpl.approve() DENTRO de su transaccion (despues de escribir, en esa misma transaccion y en una sola
// sentencia, el aviso de la bandeja para los seguidores del organizador). Lo escucha MassNotifier, que en segundo
// plano y DESPUES del commit les manda el mail a esos seguidores, buscandolos de a tandas.
// Lleva solo datos simples (no entidades): el id del evento, el del organizador y su nombre, que se lee dentro de la
// transaccion para no tener que navegar relaciones desde otro hilo.
public class EventApprovedEvent {

    private final long eventId;
    private final long organizerId;
    private final String organizerName;

    public EventApprovedEvent(final long eventId, final long organizerId, final String organizerName) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.organizerName = organizerName;
    }

    public long getEventId() {
        return eventId;
    }

    public long getOrganizerId() {
        return organizerId;
    }

    public String getOrganizerName() {
        return organizerName;
    }
}
