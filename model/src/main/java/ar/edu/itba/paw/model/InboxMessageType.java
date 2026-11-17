// Que paso, para cada mensaje de la bandeja (ver InboxMessage). Se guarda con
// name() en inbox_message.type, igual que los otros enums. El texto que ve la
// persona sale de messages.properties (inbox.type.<NOMBRE>), no de aca.
// Cada tipo dice tambien que pantalla abre su boton "Abrir" (getTarget()): sin
// argumento, el detalle del evento.
package ar.edu.itba.paw.model;

public enum InboxMessageType {
    // ---- Para quien se inscribe ----
    REGISTERED_CONFIRMED,
    REGISTERED_PENDING,
    REGISTERED_WAITLISTED,
    REQUEST_APPROVED,
    REQUEST_REJECTED,
    // Se libero un lugar y le toco: paso de la lista de espera a confirmado.
    WAITLIST_PROMOTED,
    // El organizador le saco el lugar (estaba confirmado).
    REGISTRATION_REVOKED,
    PRIVATE_INVITE,
    EVENT_UPDATED,
    EVENT_CANCELLED,
    // Recordatorio automatico: el evento empieza en menos de 24 hs.
    EVENT_REMINDER,
    // Se publicaron eventos nuevos que coinciden con una de sus busquedas guardadas (ver SavedSearch).
    SAVED_SEARCH_MATCHES,
    // Un organizador que sigue (ver Follow) publico un evento nuevo que quedo aprobado.
    FOLLOWED_USER_NEW_EVENT,

    // ---- Para quien organiza ----
    // Se anoto o se dio de baja alguien: abre la lista completa de inscriptos (no la de pedidos pendientes:
    // en un evento sin aprobacion esa esta vacia y la persona no aparece ahi).
    NEW_REGISTRATION(Target.ATTENDEES),
    REGISTRATION_CANCELLED(Target.ATTENDEES),
    // Alguien pidio anotarse en un evento con aprobacion: abre los pedidos pendientes, donde se acepta o rechaza.
    NEW_PENDING(Target.PENDING_REQUESTS),
    // Recordatorio automatico para el organizador: su evento empieza en menos de 24 hs.
    EVENT_TOMORROW(Target.ATTENDEES),
    // Al organizador: alguien pregunto en su evento.
    NEW_QUESTION,
    // A quien pregunto: el organizador le respondio.
    QUESTION_ANSWERED,

    // ---- Moderacion (ver ModerationService/UserService) ----
    // Un admin dio de baja el evento tras revisar un reporte: le llega al creador.
    EVENT_REMOVED_BY_ADMIN,
    // Un admin le pidio al creador que corrija algo en el evento (en vez de
    // darlo de baja directo): le llega al creador con el mensaje del admin.
    EVENT_CHANGES_REQUESTED,
    // Un admin suspendio la cuenta.
    ACCOUNT_SUSPENDED,
    // Un admin reactivo la cuenta.
    ACCOUNT_REACTIVATED,
    // Revision previa a publicarse (ver EventApprovalStatus), no un reporte:
    // un admin aprobo el evento recien creado, ya es visible publicamente.
    EVENT_APPROVED,
    // Un admin rechazo el evento recien creado (definitivo, no hay reenvio).
    EVENT_REJECTED;

    // Que pantalla del evento abre el aviso. Dice QUE pantalla, no su URL: la ruta la pone la vista.
    public enum Target {
        // El detalle del evento.
        EVENT,
        // Todos los inscriptos (solo la ve su organizador).
        ATTENDEES,
        // Los pedidos pendientes de aceptar o rechazar (solo la ve su organizador).
        PENDING_REQUESTS
    }

    private final Target target;

    InboxMessageType() {
        this(Target.EVENT);
    }

    InboxMessageType(final Target target) {
        this.target = target;
    }

    public Target getTarget() {
        return target;
    }
}
