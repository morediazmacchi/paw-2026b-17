// Que paso, para cada mensaje de la bandeja (ver InboxMessage). Se guarda con
// name() en inbox_message.type, igual que los otros enums. El texto que ve la
// persona sale de messages.properties (inbox.type.<NOMBRE>), no de aca.
package ar.edu.itba.paw.model;

public enum InboxMessageType {
    // ---- Para quien se inscribe ----
    REGISTERED_CONFIRMED,
    REGISTERED_PENDING,
    REGISTERED_WAITLISTED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    WAITLIST_SPOT_OFFERED,
    PRIVATE_INVITE,
    EVENT_UPDATED,
    EVENT_CANCELLED,
    // Recordatorio automatico: el evento empieza en menos de 24 hs.
    EVENT_REMINDER,

    // ---- Para quien organiza ----
    NEW_REGISTRATION,
    REGISTRATION_CANCELLED,
    NEW_PENDING,
    WAITLIST_ACCEPTED,
    WAITLIST_DECLINED,
    // Recordatorio automatico para el organizador: su evento empieza en menos de 24 hs.
    EVENT_TOMORROW
}
