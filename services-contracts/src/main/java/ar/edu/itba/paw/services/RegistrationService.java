// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo.
     *
     * @throws IllegalStateException si el evento ya esta lleno
     */
    RegistrationResult register(long eventId, String email, String name);

    /**
     * @return true si el evento ya alcanzo su cupo maximo (siempre false si no tiene limite)
     */
    boolean isFull(long eventId);
}
