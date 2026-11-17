// Caso de uso de lectura: arma el perfil publico de una persona (ver UserProfile). Implementacion:
// UserProfileServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.ProfileTab;

import java.util.Optional;

public interface UserProfileService {

    // Cuantos eventos realizados (publicos, aprobados, no cancelados, ya pasados) hacen falta para que el perfil
    // diga que es un "supereventista".
    int SUPER_ORGANIZER_MIN_EVENTS = 10;

    /**
     * El perfil publico de {@code userId}: cuantos eventos tiene en cada pestaña (proximos y realizados) y la pagina
     * {@code page} de los de UNA sola, la que se ve. {@code requestedTab} es la que pidio la URL; null (o ninguna) y
     * el service elige: proximos, salvo que no tenga ninguno y si tenga realizados. Una pagina fuera de rango cae en
     * la ultima. {@code viewerId} es quien mira (null si es anonimo) y solo sirve para saber si es el perfil propio.
     *
     * Vacio si la cuenta no existe o no esta activa (suspendida, sin confirmar el mail): un perfil que no se puede
     * mostrar no se distingue de uno que no existe.
     */
    Optional<UserProfile> findProfile(long userId, Long viewerId, ProfileTab requestedTab, int page);

    /**
     * La foto de portada del perfil de {@code userId}. Vacio si la cuenta no existe o no esta activa (mismo criterio
     * que {@link #findProfile}) o si no eligio ninguna: en ese caso la pantalla usa la portada por defecto.
     */
    Optional<Image> findCoverImage(long userId);
}
