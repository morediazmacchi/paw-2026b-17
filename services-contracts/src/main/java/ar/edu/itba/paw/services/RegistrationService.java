// Contrato de servicio para inscripciones. Implementacion: RegistrationServiceImpl (modulo services).
package ar.edu.itba.paw.services;

public interface RegistrationService {

    /**
     * Anota a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo
     * y devuelve el estado existente. Si el evento tiene cupo maximo y ya esta lleno,
     * no lo anota y devuelve "full".
     *
     * @return "confirmed" si quedo anotado, o "full" si el evento ya llego al cupo
     */
    String register(long eventId, String email, String name);

    /**
     * @return true si el evento tiene cupo maximo y ya se alcanzo.
     *         Se usa para no mostrar el formulario de inscripcion.
     */
    boolean isFull(long eventId);
}
