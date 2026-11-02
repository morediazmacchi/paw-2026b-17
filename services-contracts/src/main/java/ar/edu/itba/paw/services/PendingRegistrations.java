// La pantalla de "aceptar o rechazar inscriptos" de un evento: los pedidos pendientes, cuantos lugares
// quedan y si ya no queda ninguno. Lo devuelve RegistrationService.findPendingSummary().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;

import java.util.List;

public class PendingRegistrations {

    private final List<Registration> registrations;
    private final Integer availableSpots;
    private final boolean full;

    public PendingRegistrations(final List<Registration> registrations, final Integer availableSpots,
                                final boolean full) {
        this.registrations = registrations;
        this.availableSpots = availableSpots;
        this.full = full;
    }

    public List<Registration> getRegistrations() { return registrations; }

    // null si el evento no tiene limite de cupo.
    public Integer getAvailableSpots() { return availableSpots; }

    // Sin lugares libres el organizador ya no puede aceptar a nadie mas.
    public boolean isFull() { return full; }
}
