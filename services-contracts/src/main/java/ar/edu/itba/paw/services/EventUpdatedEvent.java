package ar.edu.itba.paw.services;

// Hecho de dominio: un evento se edito (ya guardado). Lo publica EventServiceImpl.update() DENTRO de su transaccion y
// lo escuchan dos:
// - RegistrantsEventListener, en esa misma transaccion: el aviso de la bandeja (una sola sentencia) y hacer avanzar la
//   lista de espera si subio el cupo (cambia estado, necesita el lock del evento);
// - MassNotifier, en segundo plano y DESPUES del commit: el mail a los confirmados, buscandolos de a tandas.
// EventService anuncia lo que paso y no conoce a quienes reaccionan. Lleva solo el id (no la entidad): quien lo
// necesita lo vuelve a leer, ya guardado. Es un POJO, igual que UserAccessRevokedEvent.
public class EventUpdatedEvent {

    private final long eventId;

    public EventUpdatedEvent(final long eventId) {
        this.eventId = eventId;
    }

    public long getEventId() {
        return eventId;
    }
}
