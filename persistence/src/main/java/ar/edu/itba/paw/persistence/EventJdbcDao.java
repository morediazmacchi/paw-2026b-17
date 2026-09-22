// Implementa el contrato EventDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategoryInfo;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.InvitationStatus;
import ar.edu.itba.paw.model.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class EventJdbcDao implements EventDao {

    private static final String SELECT_BASE = "SELECT e.* FROM event e";

    private static final String COUNT_BASE = "SELECT COUNT(*) FROM event e";

    // Whitelist: el "columna real" de cada opcion de orden vive solo aca adentro,
    // nunca llega un string crudo del request hasta esta clase.
    private static final Map<EventSort, String> SORT_COLUMNS = new EnumMap<>(EventSort.class);
    static {
        SORT_COLUMNS.put(EventSort.FECHA, "e.event_date ASC");
        SORT_COLUMNS.put(EventSort.PRECIO, "e.price ASC");
    }

    private static final RowMapper<Event> ROW_MAPPER = (rs, rowNum) -> {
        final Event event = new Event();
        event.setId(rs.getLong("id"));
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setEventDate(rs.getTimestamp("event_date"));
        event.setAddress(rs.getString("address"));
        final String neighborhoodName = rs.getString("neighborhood");
        event.setNeighborhood(neighborhoodName == null ? null : Barrio.valueOf(neighborhoodName));
        event.setPrice(rs.getDouble("price"));
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
        final String sortColumn = SORT_COLUMNS.getOrDefault(criteria.getSort(), SORT_COLUMNS.get(EventSort.FECHA));

        final String sql = SELECT_BASE + where.sql + " ORDER BY " + sortColumn + " LIMIT ? OFFSET ?";
        final List<Object> params = new ArrayList<>(where.params);
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
            // (ver ReviewDao.findByCreatorEmail): no es el promedio de este evento solo.
            sql.append(" AND e.creator_email IN ("
                    + "SELECT e2.creator_email FROM review r2 JOIN event e2 ON e2.id = r2.event_id "
                    + "GROUP BY e2.creator_email HAVING AVG(r2.rating) >= ?)");
            params.add(criteria.getMinOrganizerRating());
        }

        if (criteria.isOnlyFewSpotsLeft()) {
            // "Cupos libres" no es una columna: es una cuenta. Y tiene que
            // resolverse aca adentro, en el WHERE, y no filtrando en Java lo que
            // ya vino: search() trae UNA pagina y countSearch() cuenta el total,
            // asi que filtrar despues dejaria paginas incompletas y un total que
            // no coincide con lo que se ve.
            //
            // Se restan dos cosas, las mismas que mira availableSpots(): los
            // confirmados (el lugar es de alguien) y las invitaciones de lista de
            // espera sin contestar (el lugar esta reservado mientras esa persona
            // decide). Si el filtro contara distinto que la pantalla, un evento
            // podria aparecer en "pocos cupos" y al entrar decir "0 disponibles".
            sql.append(" AND e.max_capacity IS NOT NULL AND ("
                    + "e.max_capacity"
                    + " - (SELECT COUNT(*) FROM registration r2"
                    + "     WHERE r2.event_id = e.id AND r2.status = ?)"
                    + " - (SELECT COUNT(*) FROM waitlist_invitation i2"
                    + "     JOIN registration r3 ON r3.id = i2.registration_id"
                    + "     WHERE r3.event_id = e.id AND i2.status = ?)"
                    + ") BETWEEN ? AND ?");
            params.add(Registration.STATUS_CONFIRMED);
            params.add(InvitationStatus.PENDING.name());
            params.add(EventSearchCriteria.FEW_SPOTS_MIN);
            params.add(EventSearchCriteria.FEW_SPOTS_MAX);
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
