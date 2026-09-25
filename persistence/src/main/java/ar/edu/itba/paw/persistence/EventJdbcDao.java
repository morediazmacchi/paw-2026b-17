// Implementa el contrato EventDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.InvitationStatus;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class EventJdbcDao implements EventDao {

    private static final String SELECT_BASE = "SELECT e.* FROM event e";

    private static final String COUNT_BASE = "SELECT COUNT(*) FROM event e";

    // Whitelist: el "columna real" de cada opcion de orden vive solo aca adentro,
    // nunca llega un string crudo del request hasta esta clase.
    // Cupos libres de un evento, como SQL. Es la MISMA cuenta que
    // RegistrationService.occupiedSpots()/availableSpots() (lo que ve la pantalla):
    // cupo - confirmados - pendientes de aprobacion - invitaciones de la lista de
    // espera sin contestar. Vive en un solo lugar porque la usan dos: el filtro
    // "quedan pocos cupos" y el orden por cupos; si contaran distinto, un evento
    // podria aparecer primero en "menos cupos" y al entrar decir otro numero.
    // Lleva 3 parametros: availableSpotsParams().
    // NULL si el evento no tiene cupo maximo.
    private static final String AVAILABLE_SPOTS_SQL = "(e.max_capacity"
            + " - (SELECT COUNT(*) FROM registration r2"
            + "     WHERE r2.event_id = e.id AND r2.status IN (?, ?))"
            + " - (SELECT COUNT(*) FROM waitlist_invitation i2"
            + "     JOIN registration r3 ON r3.id = i2.registration_id"
            + "     WHERE r3.event_id = e.id AND i2.status = ?))";

    // Los estados van como parametros y no escritos en el SQL: si cambia como se
    // guardan en la base, se cambia en Java y no en cada query.
    private static List<Object> availableSpotsParams() {
        return Arrays.asList(RegistrationStatus.CONFIRMED.name(), RegistrationStatus.PENDING.name(),
                InvitationStatus.PENDING.name());
    }

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

    // Los ordenes que usan AVAILABLE_SPOTS_SQL y necesitan sus parametros.
    private static final Set<EventSort> SORTS_BY_SPOTS = EnumSet.of(EventSort.CUPOS_ASC, EventSort.CUPOS_DESC);

    private static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> {
        final Event event = new Event();
        event.setId(rs.getLong("id"));
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setEventDate(rs.getTimestamp("event_date"));
        event.setAddress(rs.getString("address"));
        final String neighborhoodName = rs.getString("neighborhood");
        event.setNeighborhood(neighborhoodName == null ? null : Barrio.valueOf(neighborhoodName));
        event.setPrice(rs.getBigDecimal("price"));
        event.setMaxCapacity(rs.getObject("max_capacity", Integer.class));
        event.setCreatorEmail(rs.getString("creator_email"));
        event.setCreatedAt(rs.getTimestamp("created_at"));
        event.setRequiresApproval(rs.getBoolean("requires_approval"));
        event.setVisibility(EventVisibility.valueOf(rs.getString("visibility")));
        event.setCancelledAt(rs.getTimestamp("cancelled_at"));
        // rs.getObject(col, Double.class) no esta soportado por el driver de
        // Postgres para columnas NUMERIC (a diferencia de max_capacity, que
        // es INTEGER): hay que pasar por BigDecimal, que si es una conversion
        // que el driver entiende, y de ahi a Double a mano.
        final BigDecimal rawLatitude = rs.getBigDecimal("latitude");
        event.setLatitude(rawLatitude == null ? null : rawLatitude.doubleValue());
        final BigDecimal rawLongitude = rs.getBigDecimal("longitude");
        event.setLongitude(rawLongitude == null ? null : rawLongitude.doubleValue());
        return event;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("event")
                .usingGeneratedKeyColumns("id")
                .usingColumns("title", "description", "event_date", "address", "neighborhood",
                        "price", "max_capacity", "creator_email", "requires_approval",
                        "visibility", "latitude", "longitude");
    }

    @Override
    public Event create(final Event event) {
        final Map<String, Object> args = new HashMap<>();
        args.put("title", event.getTitle());
        args.put("description", event.getDescription());
        args.put("event_date", new java.sql.Timestamp(event.getEventDate().getTime()));
        args.put("address", event.getAddress());
        args.put("neighborhood", event.getNeighborhood() == null ? null : event.getNeighborhood().name());
        args.put("price", event.getPrice());
        args.put("max_capacity", event.getMaxCapacity());
        args.put("creator_email", event.getCreatorEmail());
        args.put("requires_approval", event.isRequiresApproval());
        args.put("visibility", event.getVisibility().name());
        args.put("latitude", event.getLatitude());
        args.put("longitude", event.getLongitude());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        event.setId(id.longValue());
        insertCategories(event);
        insertImages(event);
        return event;
    }

    @Override
    public void update(final Event event) {
        jdbcTemplate.update(
                "UPDATE event SET title = ?, description = ?, event_date = ?, address = ?, neighborhood = ?, "
                        + "price = ?, max_capacity = ?, requires_approval = ?, "
                        + "visibility = ?, latitude = ?, longitude = ? WHERE id = ?",
                event.getTitle(), event.getDescription(), new Timestamp(event.getEventDate().getTime()),
                event.getAddress(), event.getNeighborhood() == null ? null : event.getNeighborhood().name(),
                event.getPrice(), event.getMaxCapacity(),
                event.isRequiresApproval(), event.getVisibility().name(),
                event.getLatitude(), event.getLongitude(), event.getId());
        // Reemplaza todas las categorias de una: mas simple que calcular el
        // diff (altas/bajas/cambios de extra_info) entre lo que habia y lo
        // que llego en "event".
        jdbcTemplate.update("DELETE FROM event_category WHERE event_id = ?", event.getId());
        insertCategories(event);
        // Mismo criterio que las categorias: reemplaza toda la galeria de una.
        jdbcTemplate.update("DELETE FROM event_image WHERE event_id = ?", event.getId());
        insertImages(event);
    }

    private void insertCategories(final Event event) {
        for (final EventCategoryInfo info : event.getCategories()) {
            jdbcTemplate.update(
                    "INSERT INTO event_category (event_id, category_id, extra_info) VALUES (?, ?, ?)",
                    event.getId(), info.getCategoryId(), info.getExtraInfo());
        }
    }

    // El orden de insercion es el orden de la galeria (id ascendente, ver
    // attachImages): insertar en el orden de event.getImageIds() alcanza,
    // no hace falta una columna de posicion aparte.
    private void insertImages(final Event event) {
        for (final Long imageId : event.getImageIds()) {
            jdbcTemplate.update(
                    "INSERT INTO event_image (event_id, image_id) VALUES (?, ?)", event.getId(), imageId);
        }
    }

    // Trae, en una sola query extra, las categorias (con nombre y extra_info)
    // de todos los eventos pasados y se las asigna a cada uno. Evita el N+1
    // de consultar categoria por categoria por cada evento de un listado.
    private void attachCategories(final List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        final List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        final Map<Long, List<EventCategoryInfo>> byEvent = new HashMap<>();
        jdbcTemplate.query(
                "SELECT ec.event_id, ec.category_id, c.name AS category_name, ec.extra_info "
                        + "FROM event_category ec JOIN category c ON c.id = ec.category_id "
                        + "WHERE ec.event_id IN (" + placeholders(eventIds.size()) + ") ORDER BY c.name",
                rs -> {
                    final EventCategoryInfo info = new EventCategoryInfo(rs.getLong("category_id"));
                    info.setCategoryName(rs.getString("category_name"));
                    info.setExtraInfo(rs.getString("extra_info"));
                    byEvent.computeIfAbsent(rs.getLong("event_id"), k -> new ArrayList<>()).add(info);
                },
                eventIds.toArray());
        for (final Event event : events) {
            event.setCategories(byEvent.getOrDefault(event.getId(), Collections.emptyList()));
        }
    }

    // Mismo mecanismo que attachCategories: una sola query extra para todos
    // los eventos pasados, evitando el N+1. "id" ascendente en event_image es
    // el orden de subida, que es el orden de la galeria (la primera es la
    // portada, ver Event.getImageId()).
    private void attachImages(final List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        final List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        final Map<Long, List<Long>> byEvent = new HashMap<>();
        jdbcTemplate.query(
                "SELECT event_id, image_id FROM event_image "
                        + "WHERE event_id IN (" + placeholders(eventIds.size()) + ") ORDER BY id ASC",
                rs -> {
                    byEvent.computeIfAbsent(rs.getLong("event_id"), k -> new ArrayList<>())
                            .add(rs.getLong("image_id"));
                },
                eventIds.toArray());
        for (final Event event : events) {
            event.setImageIds(byEvent.getOrDefault(event.getId(), Collections.emptyList()));
        }
    }

    // Categorias e imagenes de cada evento, en dos queries extra (una por
    // relacion) en vez de una por evento. Todos los finders pasan por aca
    // para no desincronizarse entre si.
    private void attachRelated(final List<Event> events) {
        attachCategories(events);
        attachImages(events);
    }

    @Override
    public void cancel(final long id) {
        jdbcTemplate.update("UPDATE event SET cancelled_at = now() WHERE id = ?", id);
    }

    @Override
    public List<Long> findIdsNeedingReminder(final java.util.Date from, final java.util.Date until) {
        return jdbcTemplate.queryForList(
                "SELECT e.id FROM event e WHERE e.event_date > ? AND e.event_date <= ? "
                        + "AND e.cancelled_at IS NULL AND e.reminder_sent_at IS NULL",
                Long.class, new Timestamp(from.getTime()), new Timestamp(until.getTime()));
    }

    @Override
    public boolean markReminderSent(final long id, final java.util.Date when) {
        // El "IS NULL" es lo que lo hace atomico: si dos procesos llegaran a la vez,
        // la base deja que uno solo actualice la fila; el otro ve 0 filas cambiadas.
        return jdbcTemplate.update(
                "UPDATE event SET reminder_sent_at = ? WHERE id = ? AND reminder_sent_at IS NULL",
                new Timestamp(when.getTime()), id) == 1;
    }

    @Override
    public Optional<Event> findById(final long id) {
        final Optional<Event> event = jdbcTemplate.query(SELECT_BASE + " WHERE e.id = ?", ROW_MAPPER, id)
                .stream().findFirst();
        event.ifPresent(e -> attachRelated(Collections.singletonList(e)));
        return event;
    }

    @Override
    public Optional<Event> findByIdForUpdate(final long id) {
        final Optional<Event> event = jdbcTemplate.query(
                        SELECT_BASE + " WHERE e.id = ? FOR UPDATE OF e", ROW_MAPPER, id)
                .stream().findFirst();
        event.ifPresent(e -> attachRelated(Collections.singletonList(e)));
        return event;
    }

    @Override
    public List<Event> findAll() {
        final List<Event> events = jdbcTemplate.query(
                SELECT_BASE + " WHERE e.cancelled_at IS NULL ORDER BY e.event_date", ROW_MAPPER);
        attachRelated(events);
        return events;
    }

    @Override
    public List<Event> findByCategory(final long categoryId) {
        final List<Event> events = jdbcTemplate.query(
                SELECT_BASE + " WHERE EXISTS (SELECT 1 FROM event_category ec "
                        + "WHERE ec.event_id = e.id AND ec.category_id = ?) "
                        + "AND e.cancelled_at IS NULL ORDER BY e.event_date",
                ROW_MAPPER, categoryId);
        attachRelated(events);
        return events;
    }

    @Override
    public List<Event> findByCreatorEmail(final String email) {
        final List<Event> events = jdbcTemplate.query(SELECT_BASE + " WHERE e.creator_email = ? ORDER BY e.event_date DESC",
                ROW_MAPPER, email);
        attachRelated(events);
        return events;
    }

    @Override
    public List<Event> search(final EventSearchCriteria criteria) {
        final WhereClause where = buildWhereClause(criteria);
        final EventSort sort = SORT_COLUMNS.containsKey(criteria.getSort()) ? criteria.getSort() : EventSort.FECHA_ASC;

        final String sql = SELECT_BASE + where.sql + " ORDER BY " + SORT_COLUMNS.get(sort) + " LIMIT ? OFFSET ?";
        // Mismo orden que los "?" del SQL: primero los del WHERE, despues los del
        // ORDER BY (si ordena por cupos) y al final LIMIT/OFFSET.
        final List<Object> params = new ArrayList<>(where.params);
        if (SORTS_BY_SPOTS.contains(sort)) {
            params.addAll(availableSpotsParams());
        }
        params.add(criteria.getPageSize());
        params.add((criteria.getPage() - 1) * criteria.getPageSize());

        final List<Event> events = jdbcTemplate.query(sql, ROW_MAPPER, params.toArray());
        attachRelated(events);
        return events;
    }

    @Override
    public long countSearch(final EventSearchCriteria criteria) {
        final WhereClause where = buildWhereClause(criteria);
        final Long count = jdbcTemplate.queryForObject(COUNT_BASE + where.sql, Long.class, where.params.toArray());
        return count == null ? 0 : count;
    }

    // Arma el WHERE (y sus parametros) una sola vez, para que search() y countSearch()
    // filtren exactamente lo mismo y nunca se desincronicen.
    private WhereClause buildWhereClause(final EventSearchCriteria criteria) {
        // Tres reglas del listado publico (no del criteria, no son filtros que
        // el usuario pueda sacar): un evento ya ocurrido no aparece, uno
        // privado tampoco, y uno cancelado tampoco. findById/findByIdForUpdate
        // no llevan ninguna de las tres: el detalle sigue siendo accesible (con
        // su propio chequeo de acceso para el privado, ver EventController).
        final StringBuilder sql = new StringBuilder(
                " WHERE e.event_date >= ? AND e.visibility = ? AND e.cancelled_at IS NULL");
        final List<Object> params = new ArrayList<>();
        params.add(new Timestamp(System.currentTimeMillis()));
        params.add(EventVisibility.PUBLIC.name());

        if (criteria.getCategoryIds() != null && !criteria.getCategoryIds().isEmpty()) {
            // EXISTS, no un JOIN: un evento con varias de las categorias
            // pedidas tiene que aparecer una sola vez, no una fila por cada
            // categoria que matchea.
            sql.append(" AND EXISTS (SELECT 1 FROM event_category ec WHERE ec.event_id = e.id AND ec.category_id IN (")
                    .append(placeholders(criteria.getCategoryIds().size())).append("))");
            params.addAll(criteria.getCategoryIds());
        }
        if (criteria.getMinPrice() != null) {
            sql.append(" AND e.price >= ?");
            params.add(criteria.getMinPrice());
        }
        if (criteria.getMaxPrice() != null) {
            sql.append(" AND e.price <= ?");
            params.add(criteria.getMaxPrice());
        }
        if (criteria.getFromDate() != null) {
            sql.append(" AND e.event_date >= ?");
            params.add(new Timestamp(criteria.getFromDate().getTime()));
        }
        if (criteria.getToDate() != null) {
            sql.append(" AND e.event_date < ?");
            params.add(new Timestamp(criteria.getToDate().getTime()));
        }
        if (criteria.getBarrios() != null && !criteria.getBarrios().isEmpty()) {
            sql.append(" AND e.neighborhood IN (").append(placeholders(criteria.getBarrios().size())).append(")");
            criteria.getBarrios().forEach(b -> params.add(b.name()));
        }
        if (criteria.getMinOrganizerRating() != null) {
            // El promedio es de TODAS las reseñas del creador (todos sus eventos),
            // el mismo numero que se muestra en el detalle y en "Mis eventos"
            // (ver ReviewDao.findAverageRatingByCreatorEmail, que usa el mismo AVG):
            // no es el promedio de este evento solo. Si uno cambia, cambiar el otro.
            sql.append(" AND e.creator_email IN ("
                    + "SELECT e2.creator_email FROM review r2 JOIN event e2 ON e2.id = r2.event_id "
                    + "GROUP BY e2.creator_email HAVING AVG(CAST(r2.rating AS DOUBLE PRECISION)) >= ?)");
            params.add(criteria.getMinOrganizerRating());
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
                sql.append(AVAILABLE_SPOTS_SQL + " BETWEEN ? AND ?");
                params.addAll(availableSpotsParams());
                params.add(EventSearchCriteria.FEW_SPOTS_MIN);
                params.add(EventSearchCriteria.FEW_SPOTS_MAX);
            }
            if (fewSpots && soldOut) {
                sql.append(" OR ");
            }
            if (soldOut) {
                // <= 0 y no = 0: por las dudas de un evento con mas gente que cupo.
                sql.append(AVAILABLE_SPOTS_SQL + " <= 0");
                params.addAll(availableSpotsParams());
            }
            sql.append(")");
        }

        // Ordenar por cupos libres no muestra los agotados: a quien ordena por
        // "menos lugares disponibles" le sirven los que todavia tienen alguno.
        // Salvo que haya pedido justamente ver los "Sold out". Va en el WHERE (y
        // no solo en search) para que countSearch() cuente lo mismo que se ve.
        if (SORTS_BY_SPOTS.contains(criteria.getSort()) && !soldOut) {
            sql.append(" AND (e.max_capacity IS NULL OR " + AVAILABLE_SPOTS_SQL + " > 0)");
            params.addAll(availableSpotsParams());
        }

        return new WhereClause(sql.toString(), params);
    }

    // "?, ?, ?" para un IN (...) de n elementos.
    private static String placeholders(final int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private static final class WhereClause {
        private final String sql;
        private final List<Object> params;

        private WhereClause(final String sql, final List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
