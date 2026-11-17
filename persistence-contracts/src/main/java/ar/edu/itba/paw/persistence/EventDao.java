// Contrato de persistencia para Event. Implementacion: EventJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.SimilarityParameters;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface EventDao {
    Event create(Event event);

    // event.getId() tiene que venir seteado (identifica la fila a actualizar).
    // No toca creator_email ni created_at -- quien creo el evento y cuando no
    // se editan. Devuelve true si la fila existia y se actualizo.
    boolean update(Event event);

    // Soft-delete: marca cancelled_at = now(). No borra la fila (ver comentario
    // en schema.sql). Idempotente a nivel SQL, pero quien llama (EventService)
    // es responsable de no invocarlo dos veces sobre el mismo evento.
    boolean cancel(long id);

    // Baja por moderacion (ver ModerationService.removeEvent()): mismo mecanismo
    // que cancel() (marca cancelled_at), pero ademas deja marcado que fue un
    // admin y por que, para que el detalle y el mail al creador lo digan.
    boolean removeByAdmin(long id, String reason);

    // Revision previa a publicarse (ver EventService.approve()/reject()): NO
    // es el mismo mecanismo que cancel()/removeByAdmin (esos son para un
    // evento que YA estaba publico y se da de baja; esto es si nunca llego a
    // estarlo). false si no existe.
    boolean approve(long id);
    boolean reject(long id, String reason);

    // Pedir cambios (ver EventService.requestChanges()): NO es terminal como reject() -- deja el
    // evento en CHANGES_REQUESTED, editable, hasta que resubmitForReview() lo vuelva a PENDING.
    boolean requestChanges(long id, String message);

    // El creador edito un evento que estaba en CHANGES_REQUESTED: lo vuelve a PENDING para que un
    // admin lo revise de nuevo (ver EventService.update()). No toca changes_requested_message
    // (el admin que lo revise ve que ya se le pidio algo antes).
    boolean resubmitForReview(long id);

    // No excluye cancelados a proposito: el detalle de un evento cancelado
    // sigue siendo accesible para quien ya estaba anotado (ver EventController).
    Optional<Event> findById(long id);

    // Lectura acotada para autorizar (ver EventSecurity): solo id, organizador, visibilidad y estado de
    // aprobacion (4 columnas), sin cargar el Event. Vacio si el evento no existe.
    Optional<EventAccessSummary> findAccessById(long id);

    // SELECT ... FOR UPDATE: usar solo dentro de un @Transactional que chequee cupo.
    Optional<Event> findByIdForUpdate(long id);

    // Una sola consulta para varios ids (WHERE id IN (...)), para armar un mapa
    // id->Event sin un findById() por cada uno (ver AdminController.reportQueueView(),
    // EventController.addCreatorReputation()). Mismos ids repetidos o inexistentes
    // se ignoran en silencio; el orden del resultado no esta garantizado.
    List<Event> findByIds(List<Long> ids);

    // "Mis eventos": los del creador (pasados y cancelados incluidos, a
    // diferencia de search()), de a una pagina (page empieza en 1). Primero los
    // que todavia no pasaron (el mas proximo primero) y despues los pasados (el
    // mas reciente primero); el orden y el corte se hacen en SQL.
    List<Event> findByCreatorId(long creatorId, int page, int pageSize);

    /** Para el badge de "Mis eventos" (ver ProfileController): sin traer las filas. */
    long countByCreatorId(long creatorId);

    // Perfil publico de una persona (ver UserProfileService): solo lo que cualquiera puede ver de sus eventos
    // organizados, o sea publicos, aprobados y no cancelados (las mismas reglas que search()). "Proximos" = con
    // fecha >= now, el mas cercano primero; "realizados" = con fecha < now, el mas reciente primero. Cada par
    // find/count comparte el WHERE, asi el total y la pagina coinciden. page empieza en 1.
    List<Event> findUpcomingPublicByCreatorId(long creatorId, Date now, int page, int pageSize);

    long countUpcomingPublicByCreatorId(long creatorId, Date now);

    List<Event> findPastPublicByCreatorId(long creatorId, Date now, int page, int pageSize);

    long countPastPublicByCreatorId(long creatorId, Date now);

    // Panel de admin "Eventos" (ver EventService.findAllEventsPage()): TODOS los eventos, cualquier
    // estado de aprobacion, visibilidad o cancelacion, de cualquier creador. A diferencia de
    // search()/findByCreatorId(), no filtra por estado: es la unica forma de moderar algo que todavia no
    // esta publico (pendiente/rechazado) o que ya se dio de baja. titleQuery (null = sin filtrar) es un texto
    // que el titulo tiene que contener, sin distinguir mayusculas; los comodines LIKE (% y _) se toman
    // literalmente. El orden sale de la lista blanca AdminEventSort (null = CREATED_DESC).
    List<Event> findAll(String titleQuery, AdminEventSort sort, int page, int pageSize);

    /** Para el paginado del panel de admin "Eventos" (mismo filtro de titulo que findAll): sin traer las filas. */
    long countAll(String titleQuery);

    // Combina todos los filtros con AND (un campo en null en criteria significa "sin filtrar por
    // esto"); solo eventos futuros y no cancelados; ordenado y paginado segun criteria.
    // Texto (criteria.getText(), ver SearchTerms): quedan los eventos que tienen TODAS las palabras en el titulo o en la
    // descripcion, sin importar mayusculas, tildes ni puntuacion; las palabras de 4 letras o mas toleran un error de
    // tipeo, pero solo contra el titulo. EventSort.RELEVANCIA ordena por que tan bien coincide (palabra al inicio del
    // titulo, titulo, titulo con un error, descripcion; se suma por palabra); sin texto cae a fecha. Todo en la misma
    // consulta que filtra y pagina (nunca se trae el listado entero a memoria).
    List<Event> search(EventSearchCriteria criteria);

    // Mismos filtros que search() (mismo WHERE), sin orden ni paginado: el total para el paginador.
    long countSearch(EventSearchCriteria criteria);

    // Orden RECOMENDADOS (ver EventSort): UNA pagina del listado, ya ordenada y ya cortada en la base. Mismo WHERE
    // que search()/countSearch() (el total para el paginador sale de countSearch()); ignora criteria.getSort(). Cada
    // evento que matchea suma, por cada uno de los ultimos parameters.getHistorySize() eventos que criteria.getViewerId()
    // asistio (inscripcion CONFIRMED, ya ocurrido, no cancelado), exactamente S(candidato, ese evento); primero los de
    // mas puntos, despues el que ocurre antes, despues el menor id. Sin eventos asistidos nadie suma y queda por fecha.
    // Toda la cuenta (S, percentil de precio, suma, orden y LIMIT/OFFSET) es una sola consulta: nunca se traen los
    // candidatos a memoria. criteria.getViewerId() es obligatorio.
    List<Event> findRecommendedPage(EventSearchCriteria criteria, SimilarityParameters parameters);

    // "Te puede interesar": los eventos del listado (mismas reglas que search(), y sin los propios de viewerId si no es
    // null) mas parecidos al evento eventId -- S >= parameters.getRelatedMinScore() --, de mas a menos parecido (empate:
    // el que ocurre antes, despues el menor id), como mucho parameters.getRelatedLimit(). El propio evento no entra.
    // Vacio si eventId no existe o no es publico y aprobado. Tambien es una sola consulta.
    List<Event> findSimilarTo(long eventId, Long viewerId, SimilarityParameters parameters);

    // Recordatorio de 24 hs: los eventos (publicos o privados) no cancelados que
    // empiezan entre "from" y "until" y a los que todavia no se les mando.
    List<Long> findIdsNeedingReminder(java.util.Date from, java.util.Date until);

    // Marca el recordatorio como mandado. true solo si ESTA llamada lo marco (si
    // ya estaba marcado devuelve false): asi nunca se manda dos veces.
    boolean markReminderSent(long id, java.util.Date when);

    // Banner de /eventos (ver EventController.eventos()): los eventos con mas inscripciones CONFIRMADAS
    // en este momento, de mas a menos. Mismos filtros de base que search() (publicos, aprobados, no
    // cancelados, futuros): uno que se cancela, vence o se vuelve privado sale solo del banner. Ni los
    // pedidos pendientes, ni la lista de espera, ni los rechazados cuentan. Uno sin ninguna inscripcion
    // confirmada no entra: puede devolver menos de "limit" eventos, o ninguno. Si dos empatan va primero
    // el que ocurre antes, y despues el de menor id. Se ven siempre, tambien los de quien navega.
    List<Event> findMostRegistered(int limit);

    /**
     * Cuantos lugares tiene ocupados un evento: las inscripciones CONFIRMADAS mas las invitaciones por mail cuya
     * persona todavia no se anoto (cada una tiene un lugar reservado; al anotarse pasa a ser su inscripcion y deja de
     * contarse aparte). Ni los pedidos PENDING ni la lista de espera ocupan cupo. Es la unica definicion de "ocupado":
     * el cupo (EventService.occupiedSpots), la lista de espera y la edicion del cupo se apoyan en ella.
     */
    long countOccupiedSpots(long eventId);

    /**
     * Si esa persona ya asistio a algo: tiene al menos una inscripcion CONFIRMADA a un evento que ya paso (antes de
     * "now") y que no se cancelo (uno cancelado, o dado de baja por un admin, no ocurrio). Es lo que habilita el
     * orden RECOMENDADOS (ver EventSimilarityService).
     */
    boolean hasAttendanceHistory(long userId, Date now);
}
