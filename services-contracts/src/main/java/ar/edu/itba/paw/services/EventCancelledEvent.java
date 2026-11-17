package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

// Hecho de dominio: un evento se dio de baja (lo cancelo su organizador o lo saco un admin). Lo publica EventServiceImpl
// DENTRO de su transaccion; a quienes estaban anotados les avisa quien lo escucha (ver RegistrantsEventListener).
// Mismo criterio que EventUpdatedEvent.
public class EventCancelledEvent {

    private final Event event;

    public EventCancelledEvent(final Event event) {
        this.event = event;
    }

    public Event getEvent() {
        return event;
    }
}
