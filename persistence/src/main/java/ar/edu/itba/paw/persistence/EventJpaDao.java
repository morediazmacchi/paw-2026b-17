// Implementa el contrato EventDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.dto.EventDTO;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventAccessSummary;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventCategory;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.model.SimilarityParameters;
import org.hibernate.jpa.QueryHints;
import org.springframework.stereotype.Repository;
import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.User;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class EventJpaDao implements EventDao {

    // Las consultas de la busqueda del listado (filtros, orden, cupos libres) estan en EventSearchSql: es SQL nativo a
    // proposito (el orden "por cupos libres" es una cuenta con subconsultas, y JPQL no las admite en el ORDER BY).
    // Aca solo se corre: una consulta SQL trae unicamente los IDS de la pagina y loadPageInOrderOf() carga los Event con
    // JPQL, ordenados POR LA POSICION de cada id en esa lista (no se reordena en Java).

    @PersistenceContext
    private EntityManager em;

    @Override
    public Event create(final long creatorId, final EventDTO data, final List<Long> imageIds) {
        // Del creador alcanza con una referencia (el service ya lo cargo en esta transaccion, asi que es esa misma
        // instancia); de el sale creator_email.
        final User creator = em.getReference(User.class, creatorId);
        final Event event = new Event(data.getTitle(), data.getDescription(), data.getEventDate(), data.getAddress(),
                data.getNeighborhood(), imageIds, data.getPrice(), data.getMaxCapacity(), creator.getEmail());
        event.setCreator(creator);
        event.setRequiresApproval(data.isRequiresApproval());
        event.setVisibility(data.getVisibility());
        event.setParking(data.isParking());
        event.setAddressDetails(data.getAddressDetails());
        event.setLatitude(data.getLatitude());
        event.setLongitude(data.getLongitude());
        event.setLocationManual(data.isLocationManual());
        for (final EventCategoryInfo info : data.getCategories()) {
            event.addCategory(new EventCategory(event, em.getReference(Category.class, info.getCategoryId()),
                    info.getExtraInfo()));
        }
        // persist() guarda el evento y, por el cascade, sus categorias; las fotos (ElementCollection)
        // tambien. Con IDENTITY el INSERT del evento sale aca mismo y deja el id en el objeto.
        em.persist(event);
        // Fuerza los INSERT que faltan ya (categorias y fotos): si algo viola un constraint
        // (FK, unique, check) la excepcion sale de este metodo y no de un flush posterior.
        em.flush();
        return event;
    }

    @Override
    public Optional<Event> update(final long eventId, final EventDTO data, final List<Long> imageIds) {
        final Event managed = em.find(Event.class, eventId);
        if (managed == null) {
            return Optional.empty();
        }
        // Mismo conjunto de columnas que siempre se pudo editar: nunca creator_email,
        // created_at, cancelled_at, approved_at, etc.
        // Si cambia la fecha, el recordatorio de 24 hs vuelve a "no mandado" para que
        // salga para la fecha nueva (hay que compararla antes de pisarla).
        if (managed.getEventDate().getTime() != data.getEventDate().getTime()) {
            managed.setReminderSentAt(null);
        }
        managed.setTitle(data.getTitle());
        managed.setDescription(data.getDescription());
        managed.setEventDate(data.getEventDate());
        managed.setAddress(data.getAddress());
        managed.setNeighborhood(data.getNeighborhood());
        managed.setPrice(data.getPrice());
        managed.setMaxCapacity(data.getMaxCapacity());
        managed.setRequiresApproval(data.isRequiresApproval());
        managed.setVisibility(data.getVisibility());
        managed.setLatitude(data.getLatitude());
        managed.setLongitude(data.getLongitude());
        managed.setParking(data.isParking());
        managed.setAddressDetails(data.getAddressDetails());
        managed.setLocationManual(data.isLocationManual());

        // Fotos: la galeria queda exactamente como llego en "imageIds". Se modifica la lista del
        // evento administrado "en el lugar" y Hibernate resuelve las filas de event_image (borra
        // las viejas y escribe las nuevas, en ese orden, por el UNIQUE(image_id)). Se copia antes porque
        // quien llama puede estar pasando la propia lista del evento (cuando no se subio ninguna foto nueva).
        final List<Long> newImageIds = new ArrayList<>(imageIds);
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
        for (final EventCategoryInfo incoming : data.getCategories()) {
            wanted.add(incoming.getCategoryId());
            final EventCategory existing = current.get(incoming.getCategoryId());
            if (existing != null) {
                existing.setExtraInfo(incoming.getExtraInfo());
            } else {
                managed.addCategory(new EventCategory(managed,
                        em.getReference(Category.class, incoming.getCategoryId()), incoming.getExtraInfo()));
            }
        }
        for (final Map.Entry<Long, EventCategory> entry : current.entrySet()) {
            if (!wanted.contains(entry.getKey())) {
                managed.removeCategory(entry.getValue());
            }
        }
        em.flush();
        return Optional.of(managed);
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
        // Desde ahora es visible para todos: las busquedas guardadas lo cuentan como nuevo a partir de aca.
        event.setApprovedAt(new Date());
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
    public boolean requestChanges(final long id, final String message) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setApprovalStatus(EventApprovalStatus.CHANGES_REQUESTED);
        event.setChangesRequestedMessage(message);
        event.setChangesRequestedAt(new Date());
        return true;
    }

    @Override
    public boolean resubmitForReview(final long id) {
        final Event event = em.find(Event.class, id);
        if (event == null) {
            return false;
        }
        event.setApprovalStatus(EventApprovalStatus.PENDING);
        return true;
    }

    @Override
    public List<Event> findMostRegistered(final int limit) {
        // Mismas cuatro reglas que EventSearchSql.filterFor() para search(): publico, no cancelado, no pasado, y
        // aprobado. El JOIN con las inscripciones confirmadas deja afuera solo a los eventos sin ninguna.
        // Todo (filtro, cuenta, orden y limite) en la base: nunca se traen las inscripciones a memoria.
        // El desempate por fecha y por id fija el orden cuando dos tienen la misma cantidad.
        // Dos pasos: primero solo los ids, ya ordenados y con el limite; despues los eventos con sus categorias, que
        // el banner muestra, en ese mismo orden (la cantidad de inscriptos no se puede repetir en el ORDER BY de JPQL).
        final List<Long> ids = em.createQuery(
                        "SELECT e.id FROM Registration r JOIN r.event e "
                                + "WHERE r.status = :confirmed AND e.eventDate >= :now "
                                + "AND e.visibility = :visibility AND e.cancelledAt IS NULL "
                                + "AND e.approvalStatus = :approvalStatus "
                                + "GROUP BY e.id, e.eventDate "
                                + "ORDER BY COUNT(r) DESC, e.eventDate ASC, e.id ASC", Long.class)
                .setParameter("confirmed", RegistrationStatus.CONFIRMED)
                .setParameter("now", new Date(), TemporalType.TIMESTAMP)
                .setParameter("visibility", EventVisibility.PUBLIC)
                .setParameter("approvalStatus", EventApprovalStatus.APPROVED)
                .setMaxResults(limit)
                .getResultList();
        return loadPageInOrderOf(ids);
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
    public Optional<EventAccessSummary> findAccessById(final long id) {
        // Proyeccion: las cuatro columnas que necesitan las reglas de acceso (organizador, visibilidad,
        // aprobacion), no el Event (sin sus ~29 columnas ni la subconsulta de la portada). Mismo
        // criterio que ImageJpaDao.findOwnerEventAccess. e.creator.id no hace JOIN con users: es la
        // propia columna creator_id del evento.
        final List<Object[]> rows = em.createQuery(
                        "SELECT e.id, e.creator.id, e.visibility, e.approvalStatus FROM Event e WHERE e.id = :id",
                        Object[].class)
                .setParameter("id", id)
                .getResultList();
        return rows.stream()
                .map(row -> new EventAccessSummary((Long) row[0], (Long) row[1],
                        row[2] == EventVisibility.PRIVATE, row[3] == EventApprovalStatus.APPROVED))
                .findFirst();
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

    // El orden de "Mis eventos": primero los que todavia no pasaron (el mas proximo primero), despues los pasados (el
    // mas reciente primero). Cada CASE ordena un solo grupo: en las filas del otro grupo da NULL, y como el primer
    // criterio ya las separo en dos bloques, ese NULL no cambia el orden dentro de cada bloque. Lo usan la consulta
    // de ids y la de eventos (el IN no ordena nada).
    private static final String CREATOR_ORDER_BY = "CASE WHEN e.eventDate >= :now THEN 0 ELSE 1 END ASC, "
            + "CASE WHEN e.eventDate >= :now THEN e.eventDate END ASC, "
            + "CASE WHEN e.eventDate < :now THEN e.eventDate END DESC, "
            + "e.id ASC";

    @Override
    public List<Event> findByCreatorId(final long creatorId, final int page, final int pageSize) {
        final Date now = new Date();
        final List<Long> ids = em.createQuery(
                        "SELECT e.id FROM Event e WHERE e.creator.id = :creatorId ORDER BY " + CREATOR_ORDER_BY, Long.class)
                .setParameter("creatorId", creatorId)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, CREATOR_ORDER_BY, Collections.singletonMap("now", now));
    }

    @Override
    public long countByCreatorId(final long creatorId) {
        return em.createQuery("SELECT COUNT(e) FROM Event e WHERE e.creator.id = :creatorId", Long.class)
                .setParameter("creatorId", creatorId)
                .getSingleResult();
    }

    // Perfil publico: los eventos del creador que cualquiera puede ver (publicos, aprobados, no cancelados: las
    // mismas reglas que EventSearchSql.filterFor() para search()), partidos por fecha en proximos / realizados. Los cuatro
    // metodos comparten el WHERE para que el total y la pagina coincidan.
    private static final String PUBLIC_BY_CREATOR_WHERE = "WHERE e.creator.id = :creatorId "
            + "AND e.visibility = :visibility AND e.approvalStatus = :approvalStatus AND e.cancelledAt IS NULL ";

    @Override
    public List<Event> findUpcomingPublicByCreatorId(final long creatorId, final Date now, final int page,
                                                     final int pageSize) {
        return findPublicByCreatorId(creatorId, "AND e.eventDate >= :now", "e.eventDate ASC, e.id ASC", now,
                page, pageSize);
    }

    @Override
    public long countUpcomingPublicByCreatorId(final long creatorId, final Date now) {
        return countPublicByCreatorId(creatorId, "AND e.eventDate >= :now", now);
    }

    @Override
    public List<Event> findPastPublicByCreatorId(final long creatorId, final Date now, final int page,
                                                 final int pageSize) {
        return findPublicByCreatorId(creatorId, "AND e.eventDate < :now", "e.eventDate DESC, e.id DESC", now,
                page, pageSize);
    }

    @Override
    public long countPastPublicByCreatorId(final long creatorId, final Date now) {
        return countPublicByCreatorId(creatorId, "AND e.eventDate < :now", now);
    }

    // Dos pasos: primero solo los ids de la pagina, ya ordenados y con OFFSET/LIMIT; despues los eventos con sus
    // categorias (que la tarjeta muestra), con el MISMO ORDER BY.
    private List<Event> findPublicByCreatorId(final long creatorId, final String dateFilter, final String orderBy,
                                              final Date now, final int page, final int pageSize) {
        final List<Long> ids = publicByCreatorParams(
                        em.createQuery("SELECT e.id FROM Event e " + PUBLIC_BY_CREATOR_WHERE + dateFilter
                                + " ORDER BY " + orderBy, Long.class),
                        creatorId, now)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        return loadPage(ids, orderBy, Collections.emptyMap());
    }

    private long countPublicByCreatorId(final long creatorId, final String date, final Date now) {
        return publicByCreatorParams(
                        em.createQuery("SELECT COUNT(e) FROM Event e " + PUBLIC_BY_CREATOR_WHERE + date, Long.class),
                        creatorId, now)
                .getSingleResult();
    }

    private static <T> TypedQuery<T> publicByCreatorParams(
            final TypedQuery<T> query, final long creatorId, final Date now) {
        return query.setParameter("creatorId", creatorId)
                .setParameter("visibility", EventVisibility.PUBLIC)
                .setParameter("approvalStatus", EventApprovalStatus.APPROVED)
                .setParameter("now", now, TemporalType.TIMESTAMP);
    }

    // El ORDER BY de cada orden del panel de admin. Es una lista blanca: lo que escribe la persona nunca se
    // concatena al JPQL, solo se elige una de estas cadenas. e.id desempata para que las paginas no se pisen.
    private static final Map<AdminEventSort, String> ADMIN_ORDER_BY = new EnumMap<>(AdminEventSort.class);

    static {
        ADMIN_ORDER_BY.put(AdminEventSort.CREATED_DESC, "e.createdAt DESC, e.id DESC");
        ADMIN_ORDER_BY.put(AdminEventSort.CREATED_ASC, "e.createdAt ASC, e.id ASC");
        ADMIN_ORDER_BY.put(AdminEventSort.PENDING_FIRST,
                "CASE WHEN e.approvalStatus = :pending THEN 0 ELSE 1 END, e.createdAt DESC, e.id DESC");
        ADMIN_ORDER_BY.put(AdminEventSort.EVENT_DATE_DESC, "e.eventDate DESC, e.id DESC");
        ADMIN_ORDER_BY.put(AdminEventSort.EVENT_DATE_ASC, "e.eventDate ASC, e.id ASC");
        ADMIN_ORDER_BY.put(AdminEventSort.TITLE_ASC, "LOWER(e.title) ASC, e.id ASC");
    }

    // '!' como caracter de escape del LIKE: asi un % o un _ (o un !) que escribe la persona busca ese caracter, no es
    // un comodin.
    private static final String ADMIN_TITLE_FILTER = " WHERE LOWER(e.title) LIKE :titlePattern ESCAPE '!'";

    private static String likeContains(final String text) {
        return "%" + text.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @Override
    public List<Event> findAll(final String titleQuery, final AdminEventSort sort, final int page,
                               final int pageSize) {
        // Sin filtro de estado a proposito (ver el comentario del contrato): el panel de admin tiene que poder
        // ver pendientes, rechazados y dados de baja, no solo lo que search() deja pasar.
        // Dos pasos: primero solo los ids de la pagina, ya filtrados, ordenados y con OFFSET/LIMIT; despues los
        // eventos con sus categorias, con el MISMO ORDER BY.
        final AdminEventSort effectiveSort = sort == null ? AdminEventSort.CREATED_DESC : sort;
        final boolean filtered = titleQuery != null && !titleQuery.isEmpty();
        final TypedQuery<Long> query = em.createQuery("SELECT e.id FROM Event e" + (filtered ? ADMIN_TITLE_FILTER : "")
                + " ORDER BY " + ADMIN_ORDER_BY.get(effectiveSort), Long.class);
        if (filtered) {
            query.setParameter("titlePattern", likeContains(titleQuery));
        }
        if (effectiveSort == AdminEventSort.PENDING_FIRST) {
            query.setParameter("pending", EventApprovalStatus.PENDING);
        }
        final List<Long> ids = query.setFirstResult((page - 1) * pageSize).setMaxResults(pageSize).getResultList();
        // Los eventos de esos ids con el MISMO ORDER BY (y su parametro, si lo usa).
        return loadPage(ids, ADMIN_ORDER_BY.get(effectiveSort), effectiveSort == AdminEventSort.PENDING_FIRST
                ? Collections.singletonMap("pending", EventApprovalStatus.PENDING) : Collections.emptyMap());
    }

    @Override
    public long countAll(final String titleQuery) {
        final boolean filtered = titleQuery != null && !titleQuery.isEmpty();
        final TypedQuery<Long> query = em.createQuery(
                "SELECT COUNT(e) FROM Event e" + (filtered ? ADMIN_TITLE_FILTER : ""), Long.class);
        if (filtered) {
            query.setParameter("titlePattern", likeContains(titleQuery));
        }
        return query.getSingleResult();
    }

    @Override
    public List<Event> search(final EventSearchCriteria criteria) {
        final EventSearchSql.Filter filter = EventSearchSql.filterFor(criteria);
        // SQL nativo (ver EventSearchSql): solo los ids de la pagina, ya ordenados. Los ordenes por cupos usan en el
        // ORDER BY el mismo parametro que el WHERE (CONFIRMED_PARAM), que el filtro siempre carga en ese caso.
        final Query idsQuery = em.createNativeQuery(
                EventSearchSql.SEARCH_IDS_BASE + filter.from + filter.where + " ORDER BY "
                        + EventSearchSql.orderBy(criteria));
        filter.bindTo(idsQuery);
        idsQuery.setFirstResult((criteria.getPage() - 1) * criteria.getPageSize());
        idsQuery.setMaxResults(criteria.getPageSize());

        final List<Long> ids = new ArrayList<>();
        for (final Object id : idsQuery.getResultList()) {
            ids.add(((Number) id).longValue());
        }
        return loadPageInOrderOf(ids);
    }

    @Override
    public List<Event> findRecommendedPage(final EventSearchCriteria criteria, final SimilarityParameters parameters) {
        Objects.requireNonNull(criteria.getViewerId(), "viewerId");
        final EventSearchSql.Filter filter = EventSearchSql.filterFor(criteria);
        final Map<String, Object> extra = new LinkedHashMap<>();
        // Los parametros de la bolsa (ver RecommendationSql.attendedWhere()). El estado y "now" pueden estar ya en el
        // filtro: es el mismo valor.
        extra.put(EventSearchSql.CONFIRMED_PARAM, RegistrationStatus.CONFIRMED.name());
        extra.put("historySize", parameters.getHistorySize());

        final Query idsQuery = recommendationQuery(RecommendationSql.Mode.RECOMMENDED, filter, extra, parameters);
        idsQuery.setFirstResult((criteria.getPage() - 1) * criteria.getPageSize());
        idsQuery.setMaxResults(criteria.getPageSize());
        return loadPageInOrderOf(idsOf(idsQuery));
    }

    @Override
    public List<Event> findSimilarTo(final long eventId, final Long viewerId, final SimilarityParameters parameters) {
        final EventSearchCriteria listing = new EventSearchCriteria();
        listing.setViewerId(viewerId);
        final EventSearchSql.Filter filter = EventSearchSql.filterFor(listing).and(" AND e.id <> :targetId");
        final Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("targetId", eventId);
        extra.put("minScore", parameters.getRelatedMinScore());

        final Query idsQuery = recommendationQuery(RecommendationSql.Mode.SIMILAR, filter, extra, parameters);
        idsQuery.setMaxResults(parameters.getRelatedLimit());
        return loadPageInOrderOf(idsOf(idsQuery));
    }

    // Arma la consulta de RecommendationSql con sus parametros: los del filtro, los de la bolsa y los coeficientes.
    private Query recommendationQuery(final RecommendationSql.Mode mode, final EventSearchSql.Filter filter,
                                      final Map<String, Object> extra, final SimilarityParameters parameters) {
        final boolean hasIgnored = !parameters.getIgnoredCategoryNames().isEmpty();
        final Query query = em.createNativeQuery(RecommendationSql.build(mode, filter.from, filter.where, hasIgnored));
        filter.bindTo(query);
        EventSearchSql.Filter.bind(query, extra);

        final Map<String, Object> coefficients = new LinkedHashMap<>();
        coefficients.put("weightCategory", parameters.getWeightCategory());
        coefficients.put("weightPrice", parameters.getWeightPrice());
        coefficients.put("weightNeighborhood", parameters.getWeightNeighborhood());
        coefficients.put("weightHour", parameters.getWeightHour());
        coefficients.put("weightWeekday", parameters.getWeightWeekday());
        coefficients.put("weightOrganizer", parameters.getWeightOrganizer());
        coefficients.put("freeVsPaidFactor", parameters.getFreeVsPaidFactor());
        coefficients.put("maxBarrioKm", Barrio.MAX_DISTANCE_KM);
        if (hasIgnored) {
            coefficients.put("ignoredCategories", new ArrayList<>(parameters.getIgnoredCategoryNames()));
        }
        EventSearchSql.Filter.bind(query, coefficients);
        return query;
    }

    private static List<Long> idsOf(final Query idsQuery) {
        final List<Long> ids = new ArrayList<>();
        for (final Object id : idsQuery.getResultList()) {
            ids.add(((Number) id).longValue());
        }
        return ids;
    }

    @Override
    public long countSearch(final EventSearchCriteria criteria) {
        final EventSearchSql.Filter filter = EventSearchSql.filterFor(criteria);
        // SQL nativo por el mismo motivo que search().
        final Query countQuery = em.createNativeQuery(EventSearchSql.SEARCH_COUNT_BASE + filter.from + filter.where);
        filter.bindTo(countQuery);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    // Segundo paso del 1+1: los eventos de "ids" (con sus categorias) con el MISMO ORDER BY que la consulta que eligio la
    // pagina (el IN no ordena nada). "orderBy" es lo que sigue a ORDER BY; "params" son los parametros que ese orden usa.
    private List<Event> loadPage(final List<Long> ids, final String orderBy, final Map<String, ?> params) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        final TypedQuery<Event> query = em.createQuery(
                        "SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.categories ec "
                                + "LEFT JOIN FETCH ec.category WHERE e.id IN :ids ORDER BY " + orderBy, Event.class)
                .setParameter("ids", ids)
                // DISTINCT solo en memoria (por el JOIN con las categorias): los ordenes por CASE no estan en el
                // SELECT, y con el DISTINCT de SQL Postgres los rechaza.
                .setHint(QueryHints.HINT_PASS_DISTINCT_THROUGH, false);
        params.forEach((name, value) -> {
            if (value instanceof Date) {
                query.setParameter(name, (Date) value, TemporalType.TIMESTAMP);
            } else {
                query.setParameter(name, value);
            }
        });
        return query.getResultList();
    }

    // Para los ordenes que NO se pueden escribir en JPQL (el puntaje de la busqueda de texto, los recomendados, la
    // cantidad de inscriptos: salen de SQL nativo o de un GROUP BY): la consulta de ids ya los resolvio, y aca el
    // ORDER BY es la posicion de cada id en esa lista, en SQL y no reordenando en Java.
    private List<Event> loadPageInOrderOf(final List<Long> ids) {
        final Map<String, Long> positions = new LinkedHashMap<>();
        final StringBuilder orderBy = new StringBuilder("CASE");
        for (int i = 0; i < ids.size(); i++) {
            positions.put("pos" + i, ids.get(i));
            orderBy.append(" WHEN e.id = :pos").append(i).append(" THEN ").append(i);
        }
        orderBy.append(" ELSE ").append(ids.size()).append(" END");
        return loadPage(ids, orderBy.toString(), positions);
    }

    @Override
    public long countOccupiedSpots(final long eventId) {
        final long confirmed = em.createQuery(
                        "SELECT COUNT(r) FROM Registration r WHERE r.event.id = :eventId AND r.status = :status",
                        Long.class)
                .setParameter("eventId", eventId)
                .setParameter("status", RegistrationStatus.CONFIRMED)
                .getSingleResult();
        // Invitaciones sin usar = cuyo mail todavia no tiene una inscripcion en ese evento.
        final long outstandingInvitations = em.createQuery(
                        "SELECT COUNT(i) FROM EventInvitation i WHERE i.event.id = :eventId AND NOT EXISTS ("
                                + "SELECT r.id FROM Registration r WHERE r.event.id = i.event.id AND r.email = i.email)",
                        Long.class)
                .setParameter("eventId", eventId)
                .getSingleResult();
        return confirmed + outstandingInvitations;
    }

    @Override
    public boolean hasAttendanceHistory(final long userId, final Date now) {
        // Alcanza con saber si hay UNA: LIMIT 1 y no se cuenta nada.
        return !em.createQuery(
                        "SELECT e.id FROM Registration r JOIN r.event e "
                                + "WHERE r.user.id = :userId AND r.status = :confirmed "
                                + "AND e.eventDate < :now AND e.cancelledAt IS NULL", Long.class)
                .setParameter("userId", userId)
                .setParameter("confirmed", RegistrationStatus.CONFIRMED)
                .setParameter("now", now, TemporalType.TIMESTAMP)
                .setMaxResults(1)
                .getResultList().isEmpty();
    }
}
