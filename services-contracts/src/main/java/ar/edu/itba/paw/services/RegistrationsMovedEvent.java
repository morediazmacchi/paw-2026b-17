package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.RegistrationStatus;

import java.util.Collections;
import java.util.List;

// Hecho de dominio: muchas inscripciones de un evento cambiaron de estado de golpe. Lo publica RegistrationServiceImpl
// DENTRO de su transaccion, despues de cambiarles el estado (una sola sentencia) y de escribir los avisos de la bandeja
// (tambien una sola sentencia):
// - WAITLISTED: el evento se lleno y los pendientes que quedaban pasaron a la lista de espera;
// - PENDING: se libero lugar en un evento con aprobacion y los primeros de la lista de espera pasaron a esperar
//   que el organizador los acepte;
// - CONFIRMED: se libero lugar y los primeros de la lista de espera entraron.
// Lo escucha MassNotifier, que en segundo plano y DESPUES del commit carga esas inscripciones de a tandas y manda los
// mails. Lleva solo ids (no entidades): las inscripciones se vuelven a leer, ya guardadas, desde el otro hilo.
public class RegistrationsMovedEvent {

    private final long eventId;
    private final List<Long> registrationIds;
    private final RegistrationStatus newStatus;

    public RegistrationsMovedEvent(final long eventId, final List<Long> registrationIds,
                                   final RegistrationStatus newStatus) {
        this.eventId = eventId;
        this.registrationIds = Collections.unmodifiableList(registrationIds);
        this.newStatus = newStatus;
    }

    public long getEventId() {
        return eventId;
    }

    public List<Long> getRegistrationIds() {
        return registrationIds;
    }

    public RegistrationStatus getNewStatus() {
        return newStatus;
    }
}
