// La pantalla de "aceptar o rechazar inscriptos" de un evento: una pagina de los pedidos pendientes, cuantos
// pedidos hay en total, cuantos lugares quedan y si ya no queda ninguno. Lo devuelve
// RegistrationService.findPendingSummary().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Registration;

import java.util.List;

public class PendingRegistrations {

    private final PageResult<Registration> registrations;
    private final Integer availableSpots;
    private final boolean full;

    public PendingRegistrations(final PageResult<Registration> registrations, final Integer availableSpots,
                                final boolean full) {
        this.registrations = registrations;
        this.availableSpots = availableSpots;
        this.full = full;
    }

    // Los pedidos de ESTA pagina.
    public List<Registration> getRegistrations() { return registrations.getContent(); }

    // Pendientes en total (todas las paginas) y los datos para dibujar el paginador.
    public long getTotalPending() { return registrations.getTotalElements(); }

    public int getPage() { return registrations.getPage(); }

    public int getTotalPages() { return registrations.getTotalPages(); }

    // null si el evento no tiene limite de cupo.
    public Integer getAvailableSpots() { return availableSpots; }

    // Sin lugares libres el organizador ya no puede aceptar a nadie mas.
    public boolean isFull() { return full; }
}
