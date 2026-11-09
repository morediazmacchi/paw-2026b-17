// Caso de uso de lectura: arma el perfil publico de una persona (ver UserProfile). Implementacion:
// UserProfileServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;

import java.util.Optional;

public interface UserProfileService {

    // Cuantos eventos realizados (publicos, aprobados, no cancelados, ya pasados) hacen falta para que el perfil
    // diga que es un "supereventista".
    int SUPER_ORGANIZER_MIN_EVENTS = 10;

    /**
     * El perfil publico de {@code userId}, con la pagina pedida de sus eventos proximos y de sus eventos realizados
     * (cada grupo tiene su paginado; una pagina fuera de rango cae en la ultima de ESE grupo). {@code viewerId} es
     * quien mira (null si es anonimo) y solo sirve para saber si es el perfil propio.
     *
     * Vacio si la cuenta no existe o no esta activa (suspendida, sin confirmar el mail): un perfil que no se puede
     * mostrar no se distingue de uno que no existe.
     */
    Optional<UserProfile> findProfile(long userId, Long viewerId, int upcomingPage, int pastPage);

    /**
     * La foto de portada del perfil de {@code userId}. Vacio si la cuenta no existe o no esta activa (mismo criterio
     * que {@link #findProfile}) o si no eligio ninguna: en ese caso la pantalla usa la portada por defecto.
     */
    Optional<Image> findCoverImage(long userId);
}
