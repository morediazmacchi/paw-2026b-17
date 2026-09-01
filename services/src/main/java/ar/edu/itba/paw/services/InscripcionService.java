package ar.edu.itba.paw.services;

public interface InscripcionService {

    /**
     * Registra a alguien en un evento. Si ya estaba anotado con ese mail, no crea uno nuevo
     * y devuelve el estado existente. Si hay cupo maximo y ya esta lleno, anota en lista de espera.
     *
     * @return el estado final: "confirmada" o "espera"
     */
    String register(long eventoId, String email, String nombre);
}
