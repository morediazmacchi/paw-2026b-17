package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.EventVisibility;
import ar.edu.itba.paw.model.RegistrationStatus;

import javax.persistence.Query;
import javax.persistence.TemporalType;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El SQL de la busqueda del listado /eventos: el WHERE con todos los filtros (que usan igual search(), countSearch() y
 * los recomendados, para que el total y la pagina nunca se desincronicen), el ORDER BY de cada orden y la cuenta de
 * cupos libres. EventJpaDao lo corre; aca solo se arma el texto y sus parametros.
 */
final class EventSearchSql {

    private EventSearchSql() {
    }

    // ---- SQL nativo de search()/countSearch() ----
    // Es la UNICA consulta de este DAO que no esta en JPQL, y a proposito: el listado ordena
    // "por cupos libres", que es una CUENTA (cupo - inscriptos confirmados - invitaciones sin usar)
    // calculada con subconsultas, y JPQL/HQL no permite subconsultas dentro del ORDER BY. Para no
    // duplicar la logica en dos lenguajes, TODA la busqueda (filtros + orden + paginado) se
    // resuelve en una sola consulta SQL que trae unicamente los IDS de la pagina; despues
    // loadPageInOrderOf() carga los Event con JPQL, ordenados POR LA POSICION de cada id en esa lista (ese orden sale
    // del SQL de arriba y no se puede repetir en JPQL: no se reordena en Java). Las tablas y
    // columnas que usa son las mismas que mapean las entidades (no hay SQL "paralelo" distinto).

    static final String SEARCH_IDS_BASE = "SELECT e.id FROM event e";

    static final String SEARCH_COUNT_BASE = "SELECT COUNT(*) FROM event e";

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
    static final String CONFIRMED_PARAM = "confirmedStatus";
    static final String AVAILABLE_SPOTS_SQL = "(e.max_capacity"
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
    static final String UNLIMITED_LAST = "CASE WHEN e.max_capacity IS NULL THEN 1 ELSE 0 END";

    // Whitelist: el "columna real" de cada opcion de orden vive solo aca adentro,
    // nunca llega un string crudo del request hasta esta clase. El ultimo
    // criterio (e.id) es el desempate: sin el, dos eventos con la misma fecha o
    // el mismo precio podrian cambiar de pagina entre una consulta y la otra.
    static final Map<EventSort, String> SORT_COLUMNS = new EnumMap<>(EventSort.class);
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
    static final Set<EventSort> SORTS_BY_SPOTS = EnumSet.of(EventSort.CUPOS_ASC, EventSort.CUPOS_DESC);


    // El ORDER BY de la busqueda. RELEVANCIA no es una columna: es el puntaje del JOIN de texto (TextSearchSql); sin texto
    // no hay puntaje, asi que cae al orden por fecha, como cualquier orden que esta clase no conoce.
    static String orderBy(final EventSearchCriteria criteria) {
        if (criteria.getSort() == EventSort.RELEVANCIA && criteria.hasSearchTerms()) {
            return TextSearchSql.ORDER_BY;
        }
        return SORT_COLUMNS.get(SORT_COLUMNS.containsKey(criteria.getSort()) ? criteria.getSort() : EventSort.FECHA_ASC);
    }

    // Arma el WHERE (y sus parametros) una sola vez, para que search() y countSearch()
    // filtren exactamente lo mismo y nunca se desincronicen.
    static Filter filterFor(final EventSearchCriteria criteria) {
        // Cuatro reglas del listado publico (no del criteria, no son filtros
        // que el usuario pueda sacar): un evento ya ocurrido no aparece, uno
        // privado tampoco, uno cancelado tampoco, y uno que un admin todavia
        // no aprobo (o que rechazo) tampoco. findById/findByIdForUpdate no
        // llevan ninguna de las cuatro: el detalle sigue siendo accesible (con
        // su propio chequeo de acceso, ver EventAccessServiceImpl.canView()).
        final StringBuilder sql = new StringBuilder(" WHERE " + listingRules("e"));
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("now", new Date());
        params.put("publicVisibility", EventVisibility.PUBLIC.name());
        params.put("approvedStatus", EventApprovalStatus.APPROVED.name());

        // Texto de la barra de busqueda: un JOIN que deja solo los eventos con todas las palabras (y su puntaje, que
        // usa el orden por relevancia). Es parte del filtro, no del orden: el total y la pagina cuentan lo mismo.
        String from = "";
        final List<String> searchTerms = criteria.getSearchTerms();
        if (!searchTerms.isEmpty()) {
            final TextSearchSql textSearch = TextSearchSql.of(searchTerms);
            from = textSearch.join();
            params.putAll(textSearch.params());
        }

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
        // Solo los aprobados en una ventana de tiempo: los eventos NUEVOS para una busqueda guardada (ver
        // SavedSearchService). Es el mismo WHERE del listado mas esta condicion, asi una busqueda guardada avisa
        // exactamente de los eventos que la persona veria en /eventos con esos filtros.
        if (criteria.getApprovedAfter() != null) {
            sql.append(" AND e.approved_at > :approvedAfter");
            params.put("approvedAfter", criteria.getApprovedAfter());
        }
        if (criteria.getApprovedUntil() != null) {
            sql.append(" AND e.approved_at <= :approvedUntil");
            params.put("approvedUntil", criteria.getApprovedUntil());
        }
        if (criteria.getBarrios() != null && !criteria.getBarrios().isEmpty()) {
            sql.append(" AND e.neighborhood IN (:barrios)");
            params.put("barrios", criteria.getBarrios().stream().map(Enum::name).collect(Collectors.toList()));
        }
        if (criteria.getMinOrganizerRating() != null) {
            // El promedio es de TODAS las reseñas del creador (todos sus eventos),
            // el mismo numero que se muestra en el detalle y en "Mis eventos"
            // (ver ReviewDao.findRatingSummaryByCreatorId, que usa el mismo AVG):
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

        return new Filter(from, sql.toString(), params);
    }

    // Las cuatro reglas del listado publico, para el evento con ese alias. Una sola definicion: la usan el WHERE del
    // listado (buildSearchFilter) y la busqueda de texto (TextSearchSql), que solo mira los eventos que se pueden ver.
    // Lleva los parametros :now, :publicVisibility y :approvedStatus, que buildSearchFilter siempre carga.
    static String listingRules(final String alias) {
        return alias + ".event_date >= :now AND " + alias + ".visibility = :publicVisibility AND "
                + alias + ".cancelled_at IS NULL AND " + alias + ".approval_status = :approvedStatus";
    }

    static final class Filter {
        // Lo que va entre "FROM event e" y el WHERE (vacio, o el JOIN de la busqueda de texto).
        final String from;
        final String where;
        private final Map<String, Object> params;

        private Filter(final String from, final String where, final Map<String, Object> params) {
            this.from = from;
            this.where = where;
            this.params = params;
        }

        // El mismo filtro con una condicion mas (empieza con " AND ...").
        Filter and(final String condition) {
            return new Filter(from, where + condition, params);
        }

        void bindTo(final Query query) {
            bind(query, params);
        }

        // Pone cada parametro con nombre en la query. Las fechas llevan su
        // TemporalType (java.util.Date no dice si es fecha, hora o las dos).
        static void bind(final Query query, final Map<String, Object> values) {
            for (final Map.Entry<String, Object> param : values.entrySet()) {
                if (param.getValue() instanceof Date) {
                    query.setParameter(param.getKey(), (Date) param.getValue(), TemporalType.TIMESTAMP);
                } else {
                    query.setParameter(param.getKey(), param.getValue());
                }
            }
        }
    }
}
