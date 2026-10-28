// Implementa el contrato EventDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventCategory;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class EventJpaDao implements EventDao {

    // ---- SQL nativo de search()/countSearch() ----
    // Es la UNICA consulta de este DAO que no esta en JPQL, y a proposito: el listado ordena
    // "por cupos libres", que es una CUENTA (cupo - inscriptos confirmados - invitaciones sin usar)
    // calculada con subconsultas, y JPQL/HQL no permite subconsultas dentro del ORDER BY. Para no
    // duplicar la logica en dos lenguajes, TODA la busqueda (filtros + orden + paginado) se
    // resuelve en una sola consulta SQL que trae unicamente los IDS de la pagina; despues
    // findOrderedByIds() carga los Event con JPQL y los pone en ese mismo orden. Las tablas y
    // columnas que usa son las mismas que mapean las entidades (no hay SQL "paralelo" distinto).

    private static final String SEARCH_IDS_BASE = "SELECT e.id FROM event e";

    private static final String SEARCH_COUNT_BASE = "SELECT COUNT(*) FROM event e";

    // Cupos libres de un evento, como SQL. Es la MISMA cuenta que
    // RegistrationService.occupiedSpots()/availableSpots() (lo que ve la pantalla):
    // cupo - confirmados - invitaciones por mail (eventos privados) que todavia no
    // se anotaron. Ni los pendientes de aprobacion ni la lista de espera cuentan.
    // Vive en un solo lugar porque la usan dos: el filtro "quedan pocos cupos" y
    // el orden por cupos; si contaran distinto, un evento podria aparecer primero
    // en "menos cupos" y al entrar decir otro numero.
    // Lleva 1 parametro con nombre: CONFIRMED_PARAM (el estado va como parametro y
    // no escrito en el SQL: si cambia como se guarda, se cambia en Java).
    // NULL si el evento no tiene cupo maximo.
    private static final String CONFIRMED_PARAM = "confirmedStatus";
    private static final String AVAILABLE_SPOTS_SQL = "(e.max_capacity"
            + " - (SELECT COUNT(*) FROM registration r2"
            + "     WHERE r2.event_id = e.id AND r2.status = :" + CONFIRMED_PARAM + ")"
            + " - (SELECT COUNT(*) FROM event_invitation i2"
            + "     WHERE i2.event_id = e.id AND NOT EXISTS ("
            + "         SELECT 1 FROM registration r3"
            + "         WHERE r3.event_id = i2.event_id AND r3.email = i2.email)))";

    // Sin limite de cupo = lugares "infinitos": al final de menor a mayor, al
    // principio de mayor a menor. Va primero en el ORDER BY para que los NULL de
    // AVAILABLE_SPOTS_SQL nunca se comparen con numeros (Postgres y HSQLDB los
    // ordenan distinto).
    private static final String UNLIMITED_LAST = "CASE WHEN e.max_capacity IS NULL THEN 1 ELSE 0 END";

    // Whitelist: el "columna real" de cada opcion de orden vive solo aca adentro,
    // nunca llega un string crudo del request hasta esta clase. El ultimo
    // criterio (e.id) es el desempate: sin el, dos eventos con la misma fecha o
    // el mismo precio podrian cambiar de pagina entre una consulta y la otra.
    private static final Map<EventSort, String> SORT_COLUMNS = new EnumMap<>(EventSort.class);
    static {
        SORT_COLUMNS.put(EventSort.FECHA_ASC, "e.event_date ASC, e.id ASC");
        SORT_COLUMNS.put(EventSort.FECHA_DESC, "e.event_date DESC, e.id ASC");
        SORT_COLUMNS.put(EventSort.PRECIO_ASC, "e.price ASC, e.event_date ASC, e.id ASC");
        SORT_COLUMNS.put(EventSort.PRECIO_DESC, "e.price DESC, e.event_date ASC, e.id ASC");
        SORT_COLUMNS.put(EventSort.CUPOS_ASC,
                UNLIMITED_LAST + " ASC, " + AVAILABLE_SPOTS_SQL + " ASC, e.event_date ASC, e.id ASC");
        SORT_COLUMNS.put(EventSort.CUPOS_DESC,
                UNLIMITED_LAST + " DESC, " + AVAILABLE_SPOTS_SQL + " DESC, e.event_date ASC, e.id ASC");
    }

    // Los ordenes que usan AVAILABLE_SPOTS_SQL.
    private static final Set<EventSort> SORTS_BY_SPOTS = EnumSet.of(EventSort.CUPOS_ASC, EventSort.CUPOS_DESC);

    // Cuantos ids se cargan por consulta en findAllMatching().
    private static final int FETCH_CHUNK = 500;

    @PersistenceContext
    private EntityManager em;

    @Override
    public Event create(final Event event) {
        // persist() guarda el evento y, por el cascade, sus categorias; las fotos (ElementCollection)
        // tambien. Con IDENTITY el INSERT del evento sale aca mismo y deja el id en el objeto.
        em.persist(event);
        // Fuerza los INSERT que faltan ya (categorias y fotos): si algo viola un constraint
        // (FK, unique, check) la excepcion sale de este metodo y no de un flush posterior.
        em.flush();
        return event;
    }

    @Override
    public boolean update(final Event event) {
        final Event managed = em.find(Event.class, event.getId());
        if (managed == null) {
            return false;
        }
        // Mismo conjunto de columnas que siempre se pudo editar: nunca creator_email,
        // created_at, cancelled_at, featured, etc.
        // Si cambia la fecha, el recordatorio de 24 hs vuelve a "no mandado" para que
        // salga para la fecha nueva (hay que compararla antes de pisarla).
        if (managed.getEventDate().getTime() != event.getEventDate().getTime()) {
            managed.setReminderSentAt(null);
        }
        managed.setTitle(event.getTitle());
        managed.setDescription(event.getDescription());
        managed.setEventDate(event.getEventDate());
        managed.setAddress(event.getAddress());
        managed.setNeighborhood(event.getNeighborhood());
        managed.setPrice(event.getPrice());
        managed.setMaxCapacity(event.getMaxCapacity());
        managed.setRequiresApproval(event.isRequiresApproval());
        managed.setVisibility(event.getVisibility());
        managed.setLatitude(event.getLatitude());
        managed.setLongitude(event.getLongitude());
        managed.setParking(event.isParking());
        managed.setAddressDetails(event.getAddressDetails());
        managed.setLocationManual(event.isLocationManual());

        // Fotos: la galeria queda exactamente como llego en "event". Se modifica la lista del
        // evento administrado "en el lugar" y Hibernate resuelve las filas de event_image (borra
        // las viejas y escribe las nuevas, en ese orden, por el UNIQUE(image_id)).
        final List<Long> newImageIds = new ArrayList<>(event.getImageIds());
        managed.getImageIds().clear();
        managed.getImageIds().addAll(newImageIds);

        // Categorias: se compara lo que habia con lo que llego. Las que siguen solo actualizan su
        // extraInfo, las nuevas se agregan y las que ya no estan se sacan (orphanRemoval borra
        // su fila). No se borra y recrea todo porque reinsertar una (evento, categoria) que ya
        // existe chocaria con la clave primaria.
        final Map<Long, EventCategory> current = new HashMap<>();
        for (final EventCategory existing : managed.getCategories()) {
            current.put(existing.getCategoryId(), existing);
        }
        final Set<Long> wanted = new HashSet<>();
        for (final EventCategory incoming : event.getCategories()) {
            wanted.add(incoming.getCategoryId());
            final EventCategory existing = current.get(incoming.getCategoryId());
            if (existing != null) {
                existing.setExtraInfo(incoming.getExtraInfo());
            } else {
                managed.addCategory(new EventCategory(managed, incoming.getCategory(), incoming.getExtraInfo()));
            }
        }
        for (final Map.Entry<Long, EventCategory> entry : current.entrySet()) {
            if (!wanted.contains(entry.getKey())) {
                managed.removeCategory(entry.getValue());
            }
        }
        em.flush();
        return true;
    }

    @Override
    public boolean cancel(final long id) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setCancelledAt(new Date());
        return true;
    }

    @Override
    public boolean removeByAdmin(final long id, final String reason) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setCancelledAt(new Date());
        event.setRemovedByAdmin(true);
        event.setRemovedReason(reason);
        return true;
    }

    @Override
    public boolean approve(final long id) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        return true;
    }

    @Override
    public boolean reject(final long id, final String reason) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setApprovalStatus(EventApprovalStatus.REJECTED);
        event.setRejectedReason(reason);
        return true;
    }

    @Override
    public List<Event> findFeatured(final int limit) {
        // Mismas cuatro reglas que buildSearchFilter() para search(): publico,
        // no cancelado, no pasado, y aprobado. Un destacado que se cancela,
        // vence, o (no deberia pasar, pero por las dudas) queda pendiente de
        // nuevo, deja de aparecer solo en el banner, sin desmarcarlo a mano.
        // Dos pasos (ver findOrderedByIds): primero solo los ids, ya ordenados y con el limite;
        // despues los eventos con sus categorias, que el banner muestra.
        final List<Long> ids = em.createQuery(
                        "SELECT e.id FROM Event e WHERE e.featured = true AND e.eventDate >= :now "
                                + "AND e.visibility = :visibility AND e.cancelledAt IS NULL "
                                + "AND e.approvalStatus = :approvalStatus "
                                + "ORDER BY e.featuredAt DESC", Long.class)
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("visibility", EventVisibility.PUBLIC)
                .setParameter("approvalStatus", EventApprovalStatus.APPROVED)
                .setMaxResults(limit)
                .getResultList();
        return findOrderedByIds(ids);
    }

    @Override
    public boolean setFeatured(final long eventId, final boolean featured) {
        final Event event = em.find(Event.class, eventId);
        if (event == null) {
            return false;
        }
        event.setFeatured(featured);
        event.setFeaturedAt(featured ? new Date() : null);
        return true;
    }

    @Override
    public List<Long> findIdsNeedingReminder(final Date from, final Date until) {
        // Ademas de no cancelado y no ya avisado: aprobado. Uno pendiente (un admin
        // todavia no lo revisa) o rechazado no le tiene que llegar un recordatorio
        // a nadie -- ni al creador, ni a nadie anotado (que ademas no deberia
        // existir: canView() ya le impide a un tercero inscribirse a uno pendiente).
        return em.createQuery(
                        "SELECT e.id FROM Event e WHERE e.eventDate > :from AND e.eventDate <= :until "
                                + "AND e.cancelledAt IS NULL AND e.reminderSentAt IS NULL "
                                + "AND e.approvalStatus = :approvalStatus", Long.class)
                .setParameter("from", from, TemporalType.TIMESTAMP)
                .setParameter("until", until, TemporalType.TIMESTAMP)
                .setParameter("approvalStatus", EventApprovalStatus.APPROVED)
                .getResultList();
    }

    @Override
    public boolean markReminderSent(final long id, final Date when) {
        // Update masivo (y no find + setter) a proposito: el "IS NULL" es lo que lo hace
        // atomico. Si dos procesos llegaran a la vez, la base deja que uno solo actualice
        // la fila; el otro ve 0 filas cambiadas.
        return em.createQuery(
                        "UPDATE Event e SET e.reminderSentAt = :when "
                                + "WHERE e.id = :id AND e.reminderSentAt IS NULL")
                .setParameter("when", when, TemporalType.TIMESTAMP)
                .setParameter("id", id)
                .executeUpdate() == 1;
    }

    @Override
    public Optional<Event> findById(final long id) {
        return Optional.ofNullable(em.find(Event.class, id));
    }

    @Override
    public Optional<Event> findByIdForUpdate(final long id) {
        // SELECT ... FOR UPDATE
        return Optional.ofNullable(em.find(Event.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    @Override
    public List<Event> findByIds(final List<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // Segundo paso del patron "ids primero": como aca NO hay limite ni pagina (la pagina ya
        // la decidio quien nos paso los ids), es seguro traer la coleccion de categorias con
        // JOIN FETCH, junto con la categoria de cada fila. DISTINCT: sin el, cada evento se
        // repetiria una vez por categoria. Las fotos no se traen: la portada sale de
        // Event.coverImageId y la galeria completa solo hace falta en el detalle.
        return em.createQuery(
                        "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.categories ec "
                                + "LEFT JOIN FETCH ec.category WHERE e.id IN :ids", Event.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public List<Event> findByCreatorId(final long creatorId, final int page, final int pageSize) {
        // Primero los que todavia no pasaron (el mas proximo primero), despues los
        // pasados (el mas reciente primero). Cada CASE ordena un solo grupo: en las
        // filas del otro grupo da NULL, y como el primer criterio ya las separo en
        // dos bloques, ese NULL no cambia el orden dentro de cada bloque.
        return em.createQuery(
                        "FROM Event e WHERE e.creator.id = :creatorId ORDER BY "
                                + "CASE WHEN e.eventDate >= :now THEN 0 ELSE 1 END ASC, "
                                + "CASE WHEN e.eventDate >= :now THEN e.eventDate END ASC, "
                                + "CASE WHEN e.eventDate < :now THEN e.eventDate END DESC, "
                                + "e.id ASC", Event.class)
                .setParameter("creatorId", creatorId)
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Override
    public long countByCreatorId(final long creatorId) {
        return em.createQuery("SELECT COUNT(e) FROM Event e WHERE e.creator.id = :creatorId", Long.class)
                .setParameter("creatorId", creatorId)
                .getSingleResult();
    }

    @Override
    public List<Event> findPendingApproval(final int page, final int pageSize) {
        return em.createQuery(
                        "FROM Event e WHERE e.approvalStatus = :status ORDER BY e.createdAt ASC, e.id ASC",
                        Event.class)
                .setParameter("status", EventApprovalStatus.PENDING)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
    }

    @Override
    public long countPendingApproval() {
        return em.createQuery("SELECT COUNT(e) FROM Event e WHERE e.approvalStatus = :status", Long.class)
                .setParameter("status", EventApprovalStatus.PENDING)
                .getSingleResult();
    }

    @Override
    public List<Event> search(final EventSearchCriteria criteria) {
        final SearchFilter filter = buildSearchFilter(criteria);
        final EventSort sort = SORT_COLUMNS.containsKey(criteria.getSort()) ? criteria.getSort() : EventSort.FECHA_ASC;

        // SQL nativo (ver arriba): solo los ids de la pagina, ya ordenados. Los ordenes por
        // cupos usan en el ORDER BY el mismo parametro que el WHERE (CONFIRMED_PARAM), que
        // buildSearchFilter() siempre carga en ese caso.
        final Query idsQuery = em.createNativeQuery(
                SEARCH_IDS_BASE + filter.where + " ORDER BY " + SORT_COLUMNS.get(sort));
        filter.bindTo(idsQuery);
        idsQuery.setFirstResult((criteria.getPage() - 1) * criteria.getPageSize());
        idsQuery.setMaxResults(criteria.getPageSize());

        final List<Long> ids = new ArrayList<>();
        for (final Object id : idsQuery.getResultList()) {
            ids.add(((Number) id).longValue());
        }
        return findOrderedByIds(ids);
    }

    @Override
    public List<Event> findAllMatching(final EventSearchCriteria criteria) {
        final SearchFilter filter = buildSearchFilter(criteria);
        // Siempre por fecha: el orden "de verdad" lo decide quien llama; este es solo el
        // desempate estable (ver EventServiceImpl / EventSimilarityServiceImpl).
        final Query idsQuery = em.createNativeQuery(
                SEARCH_IDS_BASE + filter.where + " ORDER BY " + SORT_COLUMNS.get(EventSort.FECHA_ASC));
        filter.bindTo(idsQuery);
        final List<Long> ids = new ArrayList<>();
        for (final Object id : idsQuery.getResultList()) {
            ids.add(((Number) id).longValue());
        }
        // De a tandas: un IN con miles de ids pasa el limite de parametros del driver.
        final List<Event> events = new ArrayList<>(ids.size());
        for (int from = 0; from < ids.size(); from += FETCH_CHUNK) {
            events.addAll(findOrderedByIds(ids.subList(from, Math.min(from + FETCH_CHUNK, ids.size()))));
        }
        return events;
    }

    @Override
    public List<BigDecimal> findPublicPaidPrices() {
        return em.createQuery(
                        "SELECT e.price FROM Event e WHERE e.visibility = :visibility "
                                + "AND e.approvalStatus = :approvalStatus AND e.cancelledAt IS NULL "
                                + "AND e.price > 0 ORDER BY e.price ASC", BigDecimal.class)
                .setParameter("visibility", EventVisibility.PUBLIC)
                .setParameter("approvalStatus", EventApprovalStatus.APPROVED)
                .getResultList();
    }

    @Override
    public long countSearch(final EventSearchCriteria criteria) {
        final SearchFilter filter = buildSearchFilter(criteria);
        // SQL nativo por el mismo motivo que search().
        final Query countQuery = em.createNativeQuery(SEARCH_COUNT_BASE + filter.where);
        filter.bindTo(countQuery);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    // Carga los eventos de "ids" (JPQL) y los devuelve en el mismo orden que los ids:
    // el IN no garantiza ningun orden, el que importa es el que ya decidio la query de ids.
    private List<Event> findOrderedByIds(final List<Long> ids) {
        final Map<Long, Event> byId = new HashMap<>();
        for (final Event event : findByIds(ids)) {
            byId.put(event.getId(), event);
        }
        final List<Event> ordered = new ArrayList<>(ids.size());
        for (final Long id : ids) {
            final Event event = byId.get(id);
            if (event != null) {
                ordered.add(event);
            }
        }
        return ordered;
    }

    // Arma el WHERE (y sus parametros) una sola vez, para que search() y countSearch()
    // filtren exactamente lo mismo y nunca se desincronicen.
    private SearchFilter buildSearchFilter(final EventSearchCriteria criteria) {
        // Cuatro reglas del listado publico (no del criteria, no son filtros
        // que el usuario pueda sacar): un evento ya ocurrido no aparece, uno
        // privado tampoco, uno cancelado tampoco, y uno que un admin todavia
        // no aprobo (o que rechazo) tampoco. findById/findByIdForUpdate no
        // llevan ninguna de las cuatro: el detalle sigue siendo accesible (con
        // su propio chequeo de acceso, ver EventAccessServiceImpl.canView()).
        final StringBuilder sql = new StringBuilder(
                " WHERE e.event_date >= :now AND e.visibility = :publicVisibility AND e.cancelled_at IS NULL"
                        + " AND e.approval_status = :approvedStatus");
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("now", new Date());
        params.put("publicVisibility", EventVisibility.PUBLIC.name());
        params.put("approvedStatus", EventApprovalStatus.APPROVED.name());

        if (criteria.getViewerId() != null) {
            // Los eventos propios no se listan en el home (siguen en "Mis eventos"). creator_id
            // puede ser null en una fila vieja: esa no es de nadie, asi que entra.
            sql.append(" AND (e.creator_id IS NULL OR e.creator_id <> :viewerId)");
            params.put("viewerId", criteria.getViewerId());
        }
        if (criteria.getCategoryIds() != null && !criteria.getCategoryIds().isEmpty()) {
            // EXISTS, no un JOIN: un evento con varias de las categorias
            // pedidas tiene que aparecer una sola vez, no una fila por cada
            // categoria que matchea.
            sql.append(" AND EXISTS (SELECT 1 FROM event_category ec"
                    + " WHERE ec.event_id = e.id AND ec.category_id IN (:categoryIds))");
            params.put("categoryIds", criteria.getCategoryIds());
        }
        if (criteria.getMinPrice() != null) {
            sql.append(" AND e.price >= :minPrice");
            params.put("minPrice", criteria.getMinPrice());
        }
        if (criteria.getMaxPrice() != null) {
            sql.append(" AND e.price <= :maxPrice");
            params.put("maxPrice", criteria.getMaxPrice());
        }
        if (criteria.getFromDate() != null) {
            sql.append(" AND e.event_date >= :fromDate");
            params.put("fromDate", criteria.getFromDate());
        }
        if (criteria.getToDate() != null) {
            sql.append(" AND e.event_date < :toDate");
            params.put("toDate", criteria.getToDate());
        }
        if (criteria.getBarrios() != null && !criteria.getBarrios().isEmpty()) {
            sql.append(" AND e.neighborhood IN (:barrios)");
            params.put("barrios", criteria.getBarrios().stream().map(Enum::name).collect(Collectors.toList()));
        }
        if (criteria.getMinOrganizerRating() != null) {
            // El promedio es de TODAS las reseñas del creador (todos sus eventos),
            // el mismo numero que se muestra en el detalle y en "Mis eventos"
            // (ver ReviewDao.findAverageRatingByCreatorId, que usa el mismo AVG):
            // no es el promedio de este evento solo. Si uno cambia, cambiar el otro.
            sql.append(" AND e.creator_id IN ("
                    + "SELECT e2.creator_id FROM review r2 JOIN event e2 ON e2.id = r2.event_id "
                    + "GROUP BY e2.creator_id HAVING AVG(CAST(r2.rating AS DOUBLE PRECISION)) >= :minRating)");
            params.put("minRating", criteria.getMinOrganizerRating());
        }

        final boolean fewSpots = criteria.isOnlyFewSpotsLeft();
        final boolean soldOut = criteria.isOnlySoldOut();
        if (fewSpots || soldOut) {
            // "Cupos libres" no es una columna: es una cuenta. Y tiene que
            // resolverse aca adentro, en el WHERE, y no filtrando en Java lo que
            // ya vino: search() trae UNA pagina y countSearch() cuenta el total,
            // asi que filtrar despues dejaria paginas incompletas y un total que
            // no coincide con lo que se ve.
            //
            // La cuenta es AVAILABLE_SPOTS_SQL, la misma que la pantalla y que el
            // orden por cupos: si contaran distinto, un evento podria aparecer en
            // "pocos cupos" y al entrar decir "0 disponibles".
            //
            // Con los dos tildados es un OR: "ultimos cupos" o "sold out".
            // Sin cupo maximo nunca entra en ninguno: no se le acaban los lugares.
            sql.append(" AND e.max_capacity IS NOT NULL AND (");
            if (fewSpots) {
                sql.append(AVAILABLE_SPOTS_SQL + " BETWEEN :fewSpotsMin AND :fewSpotsMax");
                params.put("fewSpotsMin", EventSearchCriteria.FEW_SPOTS_MIN);
                params.put("fewSpotsMax", EventSearchCriteria.FEW_SPOTS_MAX);
            }
            if (fewSpots && soldOut) {
                sql.append(" OR ");
            }
            if (soldOut) {
                // <= 0 y no = 0: por las dudas de un evento con mas gente que cupo.
                sql.append(AVAILABLE_SPOTS_SQL + " <= 0");
            }
            sql.append(")");
            params.put(CONFIRMED_PARAM, RegistrationStatus.CONFIRMED.name());
        }

        // Ordenar por cupos libres no muestra los agotados: a quien ordena por
        // "menos lugares disponibles" le sirven los que todavia tienen alguno.
        // Salvo que haya pedido justamente ver los "Sold out". Va en el WHERE (y
        // no solo en search) para que countSearch() cuente lo mismo que se ve.
        if (SORTS_BY_SPOTS.contains(criteria.getSort()) && !soldOut) {
            sql.append(" AND (e.max_capacity IS NULL OR " + AVAILABLE_SPOTS_SQL + " > 0)");
            params.put(CONFIRMED_PARAM, RegistrationStatus.CONFIRMED.name());
        }

        return new SearchFilter(sql.toString(), params);
    }

    private static final class SearchFilter {
        private final String where;
        private final Map<String, Object> params;

        private SearchFilter(final String where, final Map<String, Object> params) {
            this.where = where;
            this.params = params;
        }

        // Pone cada parametro con nombre en la query. Las fechas llevan su
        // TemporalType (java.util.Date no dice si es fecha, hora o las dos).
        private void bindTo(final Query query) {
            for (final Map.Entry<String, Object> param : params.entrySet()) {
                if (param.getValue() instanceof Date) {
                    query.setParameter(param.getKey(), (Date) param.getValue(), TemporalType.TIMESTAMP);
                } else {
                    query.setParameter(param.getKey(), param.getValue());
                }
            }
        }
    }
}
