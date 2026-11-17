package ar.edu.itba.paw.services;

// Hecho de dominio: un evento se dio de baja (lo cancelo su organizador o lo saco un admin). Lo publica EventServiceImpl
// DENTRO de su transaccion y lo escuchan dos: RegistrantsEventListener (el aviso de la bandeja, en esa misma
// transaccion) y MassNotifier (el mail a los confirmados, en segundo plano, despues del commit y de a tandas).
// Mismo criterio que EventUpdatedEvent: lleva solo el id.
public class EventCancelledEvent {

    private final long eventId;

    public EventCancelledEvent(final long eventId) {
        this.eventId = eventId;
    }

    public long getEventId() {
        return eventId;
    }
}
