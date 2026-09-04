// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo
     * y devuelve el estado existente. Si el evento tiene cupo maximo y ya esta lleno,
     * lo anota en lista de espera.
     *
     * @return el estado final: "confirmed" o "waitlisted"
     */
    String register(long eventId, String email, String name);
}
