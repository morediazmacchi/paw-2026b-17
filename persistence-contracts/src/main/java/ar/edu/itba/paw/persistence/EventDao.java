// Contrato de persistencia para Event. Implementacion: EventJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;

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

    // No excluye cancelados a proposito: el detalle de un evento cancelado
    // sigue siendo accesible para quien ya estaba anotado (ver EventController).
    Optional<Event> findById(long id);

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

    // Combina todos los filtros con AND (un campo en null en criteria significa "sin filtrar por
    // esto"); solo eventos futuros y no cancelados; ordenado y paginado segun criteria.
    List<Event> search(EventSearchCriteria criteria);

    // Mismos filtros que search() (mismo WHERE), sin orden ni paginado: el total para el paginador.
    long countSearch(EventSearchCriteria criteria);

    // Recordatorio de 24 hs: los eventos (publicos o privados) no cancelados que
    // empiezan entre "from" y "until" y a los que todavia no se les mando.
    List<Long> findIdsNeedingReminder(java.util.Date from, java.util.Date until);

    // Marca el recordatorio como mandado. true solo si ESTA llamada lo marco (si
    // ya estaba marcado devuelve false): asi nunca se manda dos veces.
    boolean markReminderSent(long id, java.util.Date when);

    // Banner de /eventos (ver EventController.eventos()): los que un admin
    // destaco, mas reciente primero (por featured_at). Mismos filtros de base
    // que search() (publicos, no cancelados, futuros): un evento destacado que
    // se cancela o vence deja de aparecer solo, sin que un admin tenga que
    // acordarse de desmarcarlo.
    List<Event> findFeatured(int limit);

    // Marca o desmarca un evento para el banner (ver AdminController.destacar()/
    // quitarDestacado()). Al desmarcar limpia featured_at.
    boolean setFeatured(long eventId, boolean featured);
}
