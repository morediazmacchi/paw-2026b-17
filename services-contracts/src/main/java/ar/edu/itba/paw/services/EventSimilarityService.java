// Contrato de servicio de similitud entre eventos. Implementacion: EventSimilarityServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Una sola nocion de "que tan parecidos son dos eventos" (S(a, b), ver EventSimilarity) que sirve
// para dos cosas: ordenar el listado segun lo que la persona ya asistio (searchRecommended) y
// sugerir eventos parecidos a uno que se esta mirando (findSimilar).
public interface EventSimilarityService {

    /**
     * Una pagina del listado ordenada por RECOMENDADOS: cada evento que matchea los filtros suma
     * puntos por cada evento asistido recientemente al que se parece lo suficiente; primero los
     * de mas puntos, desempate por fecha del evento (el mas proximo primero) y por id.
     *
     * criteria.getViewerId() es obligatorio. Si esa persona todavia no asistio a nada, nadie suma
     * puntos y el orden queda por fecha (el mismo que FECHA_ASC), paginado en la base con LIMIT/OFFSET. Con
     * historial el puntaje se calcula en memoria sobre los eventos que matchean los filtros. Ignora criteria.getSort().
     */
    @Transactional(readOnly = true)
    PageResult<Event> searchRecommended(EventSearchCriteria criteria);

    /**
     * "Eventos que te pueden interesar": los eventos publicos futuros mas parecidos a este, del
     * mas parecido al menos, solo los que llegan al piso de similitud y como mucho el tope
     * configurado. Vacio si el evento no existe o no es publico y aprobado. Si viewerId no es
     * null, sus propios eventos no entran.
     */
    @Transactional(readOnly = true)
    List<Event> findSimilar(long eventId, Long viewerId);

    /** Si esta persona asistio a algun evento (entonces el orden RECOMENDADOS le dice algo). */
    @Transactional(readOnly = true)
    boolean hasAttendanceHistory(long userId);
}
