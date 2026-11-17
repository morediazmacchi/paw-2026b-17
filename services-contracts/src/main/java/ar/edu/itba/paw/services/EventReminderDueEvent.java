package ar.edu.itba.paw.services;

// Hecho de dominio: a un evento le toca el recordatorio de 24 hs (ya quedo marcado como enviado). Lo publica
// EventReminderServiceImpl.sendReminder() DENTRO de su transaccion (despues de escribir, en esa misma transaccion y en
// una sola sentencia, el aviso de la bandeja de los confirmados). Lo escucha MassNotifier, que en segundo plano y
// DESPUES del commit les manda el mail a los confirmados, buscandolos de a tandas. Lleva solo el id.
public class EventReminderDueEvent {

    private final long eventId;

    public EventReminderDueEvent(final long eventId) {
        this.eventId = eventId;
    }

    public long getEventId() {
        return eventId;
    }
}
