package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;

// Hecho de dominio: un evento se edito (ya guardado). Lo publica EventServiceImpl.update() DENTRO de su transaccion; a
// los inscriptos confirmados les avisa (mail + bandeja) y a la lista de espera la hace avanzar si subio el cupo quien
// lo escucha (ver RegistrantsEventListener). EventService anuncia lo que paso y no conoce a RegistrationService, que
// si depende de el. Es un POJO, igual que UserAccessRevokedEvent.
public class EventUpdatedEvent {

    private final Event event;

    public EventUpdatedEvent(final Event event) {
        this.event = event;
    }

    // El evento con los datos nuevos.
    public Event getEvent() {
        return event;
    }
}
