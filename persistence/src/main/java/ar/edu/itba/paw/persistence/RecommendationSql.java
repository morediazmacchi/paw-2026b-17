package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;

/**
 * La consulta de las recomendaciones: S(a, b) y el ranking, enteros en SQL. Lo usa EventJpaDao para
 * dos cosas que comparten la misma cuenta:
 *
 *  - findRecommendedPage(): cada evento candidato suma, por cada evento de la "bolsa" de la persona (sus
 *    ultimos N eventos asistidos), exactamente S(candidato, evento de la bolsa); orden por puntos desc,
 *    fecha asc, id asc, ya paginado.
 *  - findSimilarTo(): la bolsa es UN evento (el que se esta mirando) y solo quedan los candidatos con S >= piso.
 *
 * S(a, b) = suma(peso * parcial) / suma(peso), con seis parciales en [0, 1]: categorias (Jaccard sin las
 * ignoradas), precio (percentil entre los eventos pagos), barrio (1 - distancia entre centroides / la
 * maxima), hora (circular, 12 hs = 0), dia (mismo 1, mismo grupo lun-jue / vie-dom 0,5, distinto 0) y
 * organizador (1 o 0). Si a uno de los dos le falta barrio u organizador, o ninguno tiene categorias
 * comparables, esa feature sale de la suma y los pesos se renormalizan.
 *
 * Forma de la consulta (una sola sentencia, los CTE se leen de arriba hacia abajo):
 *
 *   barrio, bsim : los 48 centroides (VALUES generado desde el enum Barrio; no hay tabla de barrios) y la
 *                  similitud de barrio de cada par de barrios (48 x 48, haversine)
 *   cand, att    : los candidatos (el mismo WHERE del listado, buildSearchFilter) y la bolsa, con sus datos
 *                  derivados (hora, dia, cantidad de categorias), calculados UNA vez por evento
 *   pop, pct     : la distribucion de precios y el percentil de cada precio que aparece en cand/att
 *   inter        : categorias en comun de cada par (candidato, bolsa) que comparten alguna
 *   parts, pairs : los seis parciales de cada par, y S de cada par
 *   SELECT  : SUM(S) por candidato (GROUP BY), ORDER BY, LIMIT/OFFSET
 *
 * La consulta corre igual en Postgres y en HSQLDB (los tests): nada de funciones propias de un motor.
 */
final class RecommendationSql {

    // La hora y el dia se miran en hora argentina, no en la del servidor.
    static final String ZONE = "America/Argentina/Buenos_Aires";

    // Los puntos se comparan redondeados a 9 decimales y recien despues de SUMAR (no par por par): dos eventos
    // con los mismos matches suman lo mismo salvo por el ruido de punto flotante (1e-15), y ese ruido no tiene que
    // desempatar. Redondeando cada par antes de sumar, 1/3 + 1/3 + 1/3 daria 0,999999999 y no empataria con 1.
    // Tambien es el piso de "Te puede interesar": un S que en papel es 0,5 y en doubles 0,49999999999999994 entra.
    private static final String POINTS_AS_DECIMAL = "ROUND(CAST(SUM(s) AS DECIMAL(30,12)), 9)";

    private RecommendationSql() {
    }

    /** De quien sale la bolsa y que se hace con los puntos. */
    enum Mode {
        /** Bolsa = los ultimos asistidos de :viewerId; todos los candidatos salen, ordenados por puntos. */
        RECOMMENDED,
        /** Bolsa = el evento :targetId; salen solo los candidatos con S >= :minScore. */
        SIMILAR
    }

    /**
     * @param candidatesFrom lo que va entre "FROM event e" y el WHERE de los candidatos (vacio, o el JOIN de la busqueda de texto)
     * @param candidatesWhere el WHERE del listado (empieza con " WHERE ..."), con alias e para event
     * @param hasIgnoredCategories si hay categorias ignoradas (agrega el parametro :ignoredCategories)
     */
    static String build(final Mode mode, final String candidatesFrom, final String candidatesWhere,
                        final boolean hasIgnoredCategories) {
        final String notIgnored = hasIgnoredCategories ? " AND cat.name NOT IN (:ignoredCategories)" : "";
        final StringBuilder sql = new StringBuilder("WITH ");

        // ---- barrios: similitud de cada par de barrios (48 x 48) ----
        sql.append("barrio(name, lat, lon) AS (VALUES ").append(barrioValues()).append("), ");
        sql.append("bsim(a, b, s) AS (SELECT x.name, y.name, 1.0 - 2 * 6371.0 * ASIN(SQRT("
                + "POWER(SIN(RADIANS(y.lat - x.lat) / 2), 2)"
                + " + COS(RADIANS(x.lat)) * COS(RADIANS(y.lat)) * POWER(SIN(RADIANS(y.lon - x.lon) / 2), 2)"
                + ")) / :maxBarrioKm FROM barrio x CROSS JOIN barrio y), ");

        // ---- candidatos y bolsa, con lo que se calcula una vez por evento ----
        sql.append("cand AS (").append(eventsWith(candidatesFrom, candidatesWhere, hasIgnoredCategories)).append("), ");
        sql.append("att AS (").append(eventsWith("", attendedWhere(mode), hasIgnoredCategories)).append("), ");

        // ---- distribucion de precios: eventos publicos, aprobados y no cancelados -- pasados incluidos -- con
        // precio. Un solo recorrido agrupado por precio; despues cada precio que importa se ubica contra esa
        // tabla chica (hay pocos precios distintos), sin volver a tocar event. Percentil = (cuantos hay por debajo
        // + cuantos hay por debajo o iguales) / 2 / total: cien eventos a $5.000 quedan en el medio de su grupo.
        // 0,5 si no hay ningun evento pago de referencia.
        sql.append("pop AS (SELECT p.price AS price, COUNT(*) AS n FROM event p"
                + " WHERE p.visibility = :publicVisibility AND p.approval_status = :approvedStatus"
                + " AND p.cancelled_at IS NULL AND p.price > 0 GROUP BY p.price), ");
        sql.append("tot AS (SELECT COALESCE(SUM(n), 0) AS total FROM pop), ");
        sql.append("need AS (SELECT price FROM cand WHERE price > 0 UNION SELECT price FROM att WHERE price > 0), ");
        sql.append("pct AS (SELECT need.price AS price, CAST(CASE WHEN tot.total = 0 THEN 0.5 ELSE"
                + " ((SELECT COALESCE(SUM(q.n), 0) FROM pop q WHERE q.price < need.price)"
                + " + (SELECT COALESCE(SUM(q.n), 0) FROM pop q WHERE q.price <= need.price)) / 2.0E0 / tot.total END"
                + " AS DOUBLE PRECISION) AS p FROM need CROSS JOIN tot), ");

        // ---- categorias en comun, solo de los pares que comparten alguna ----
        sql.append("inter AS (SELECT cc.event_id AS cid, ac.event_id AS aid, COUNT(*) AS k"
                + " FROM event_category ac JOIN event_category cc ON cc.category_id = ac.category_id"
                + (hasIgnoredCategories ? " JOIN category cat ON cat.id = ac.category_id" : "")
                + " WHERE ac.event_id IN (SELECT id FROM att) AND cc.event_id IN (SELECT id FROM cand)"
                + notIgnored
                + " GROUP BY cc.event_id, ac.event_id), ");

        // ---- los seis parciales de cada par (candidato, bolsa) ----
        // Un parcial en NULL no existe: w_* es el peso que cuenta en el par (0 si falta el dato).
        sql.append("parts AS (SELECT c.id AS cid, c.event_date AS event_date, a.id AS aid,"
                + " CASE WHEN c.ncat + a.ncat > 0 THEN CAST(:weightCategory AS DOUBLE PRECISION) ELSE 0.0 END AS w_cat,"
                + " CASE WHEN c.ncat + a.ncat > 0"
                + "   THEN CAST(COALESCE(i.k, 0) AS DOUBLE PRECISION) / (c.ncat + a.ncat - COALESCE(i.k, 0))"
                + "   ELSE 0.0 END AS s_cat,"
                // Gratis contra gratis 1; gratis contra pago: tope freeVsPaidFactor, mas cerca mientras mas barato el pago.
                + " CASE WHEN c.price = 0 AND a.price = 0 THEN 1.0"
                + "   WHEN c.price = 0 THEN CAST(:freeVsPaidFactor AS DOUBLE PRECISION) * (1.0 - pa.p)"
                + "   WHEN a.price = 0 THEN CAST(:freeVsPaidFactor AS DOUBLE PRECISION) * (1.0 - pc.p)"
                + "   ELSE 1.0 - ABS(pc.p - pa.p) END AS s_price,"
                + " CASE WHEN c.nbh IS NOT NULL AND a.nbh IS NOT NULL"
                + "   THEN CAST(:weightNeighborhood AS DOUBLE PRECISION) ELSE 0.0 END AS w_nbh,"
                + " COALESCE(b.s, 0.0) AS s_nbh,"
                // Hora circular: las 23:00 y la 01:00 estan a 2 horas, no a 22.
                + " 1.0 - CAST(LEAST(ABS(c.minute_of_day - a.minute_of_day),"
                + "   1440 - ABS(c.minute_of_day - a.minute_of_day)) AS DOUBLE PRECISION) / 720.0 AS s_hour,"
                + " CASE WHEN c.dow = a.dow THEN 1.0"
                + "   WHEN (c.dow >= 5 AND a.dow >= 5) OR (c.dow < 5 AND a.dow < 5) THEN 0.5"
                + "   ELSE 0.0 END AS s_day,"
                + " CASE WHEN c.creator IS NOT NULL AND a.creator IS NOT NULL"
                + "   THEN CAST(:weightOrganizer AS DOUBLE PRECISION) ELSE 0.0 END AS w_org,"
                + " CASE WHEN c.creator = a.creator THEN 1.0 ELSE 0.0 END AS s_org"
                // LEFT JOIN y no CROSS JOIN: con la bolsa vacia (nadie asistio a nada) cada candidato queda igual, con
                // todo en NULL, y suma 0 en vez de desaparecer.
                + " FROM cand c LEFT JOIN att a ON 1 = 1"
                + " LEFT JOIN inter i ON i.cid = c.id AND i.aid = a.id"
                + " LEFT JOIN pct pc ON pc.price = c.price"
                + " LEFT JOIN pct pa ON pa.price = a.price"
                + " LEFT JOIN bsim b ON b.a = c.nbh AND b.b = a.nbh), ");

        // ---- S de cada par: suma ponderada / suma de los pesos que cuentan ----
        sql.append("pairs AS (SELECT cid, event_date, CASE WHEN den <= 0 THEN 0.0 ELSE num / den END AS s"
                + " FROM (SELECT cid, event_date,"
                + " w_cat * s_cat + CAST(:weightPrice AS DOUBLE PRECISION) * s_price + w_nbh * s_nbh"
                + "   + CAST(:weightHour AS DOUBLE PRECISION) * s_hour + CAST(:weightWeekday AS DOUBLE PRECISION) * s_day"
                + "   + w_org * s_org AS num,"
                + " w_cat + CAST(:weightPrice AS DOUBLE PRECISION) + w_nbh + CAST(:weightHour AS DOUBLE PRECISION)"
                + "   + CAST(:weightWeekday AS DOUBLE PRECISION) + w_org AS den"
                + " FROM parts) t) ");

        // ---- puntos por candidato y salida ----
        // Una sola agregacion sobre los pares (nunca un join de vuelta contra los candidatos). Con la bolsa vacia la
        // suma es NULL: 0 puntos y el orden queda por fecha.
        if (mode == Mode.RECOMMENDED) {
            sql.append("SELECT cid FROM pairs GROUP BY cid, event_date"
                    + " ORDER BY COALESCE(" + POINTS_AS_DECIMAL + ", 0) DESC, event_date ASC, cid ASC");
        } else {
            sql.append("SELECT cid FROM pairs GROUP BY cid, event_date"
                    + " HAVING " + POINTS_AS_DECIMAL + " >= CAST(:minScore AS DECIMAL(30,9))"
                    + " ORDER BY " + POINTS_AS_DECIMAL + " DESC, event_date ASC, cid ASC");
        }
        return sql.toString();
    }

    // Los eventos de la bolsa.
    private static String attendedWhere(final Mode mode) {
        if (mode == Mode.SIMILAR) {
            // Solo si el evento existe, es publico y esta aprobado; si no, la bolsa queda vacia y no sale nada.
            return " WHERE e.id = :targetId AND e.visibility = :publicVisibility AND e.approval_status = :approvedStatus";
        }
        // "Asistio" = inscripcion confirmada a un evento que ya paso y no se cancelo; los N mas recientes.
        return " WHERE e.id IN (SELECT e2.id FROM registration r JOIN event e2 ON e2.id = r.event_id"
                + " WHERE r.user_id = :viewerId AND r.status = :confirmedStatus"
                + " AND e2.event_date < :now AND e2.cancelled_at IS NULL"
                + " ORDER BY e2.event_date DESC, e2.id DESC LIMIT :historySize)";
    }

    // Un evento con todo lo que S le mira, calculado UNA vez por evento (no por par): precio, barrio,
    // organizador, cuantas categorias comparables tiene, minuto del dia y dia de la semana (1 = lunes ... 7 = domingo).
    private static String eventsWith(final String from, final String where, final boolean hasIgnoredCategories) {
        final String local = "(e.event_date AT TIME ZONE '" + ZONE + "')";
        return "SELECT t.id AS id, t.event_date AS event_date, t.price AS price, t.nbh AS nbh, t.creator AS creator,"
                + " t.ncat AS ncat, t.hh * 60 + t.mi AS minute_of_day,"
                // Dia de la semana desde ano/mes/dia (Zeller, division entera: 0 = domingo), llevado a 1 = lunes ... 7.
                // No hay un EXTRACT del dia de la semana que sirva en los dos motores.
                + " MOD(t.d + (t.y - (14 - t.m) / 12) + (t.y - (14 - t.m) / 12) / 4 - (t.y - (14 - t.m) / 12) / 100"
                + "   + (t.y - (14 - t.m) / 12) / 400 + (31 * (t.m + 12 * ((14 - t.m) / 12) - 2)) / 12 + 6, 7) + 1 AS dow"
                + " FROM (SELECT e.id AS id, e.event_date AS event_date, e.price AS price,"
                + " e.neighborhood AS nbh, e.creator_id AS creator,"
                + " (SELECT COUNT(*) FROM event_category ec JOIN category cat ON cat.id = ec.category_id"
                + "   WHERE ec.event_id = e.id"
                + (hasIgnoredCategories ? " AND cat.name NOT IN (:ignoredCategories)" : "") + ") AS ncat,"
                + " " + part("HOUR", local) + " AS hh, " + part("MINUTE", local) + " AS mi,"
                + " " + part("YEAR", local) + " AS y, " + part("MONTH", local) + " AS m, " + part("DAY", local) + " AS d"
                + " FROM event e" + from + where + ") t";
    }

    private static String part(final String field, final String local) {
        return "CAST(EXTRACT(" + field + " FROM " + local + ") AS INTEGER)";
    }

    // Los 48 centroides como VALUES: el enum Barrio es la unica fuente de las coordenadas (nada que mantener
    // dos veces ni sembrar en una tabla). Los nombres son constantes del enum, no texto de nadie; las
    // coordenadas llevan E0 para que sean DOUBLE en los dos motores.
    private static String barrioValues() {
        final StringBuilder values = new StringBuilder();
        for (final Barrio barrio : Barrio.values()) {
            if (values.length() > 0) {
                values.append(", ");
            }
            values.append("('").append(barrio.name()).append("', ")
                    .append(barrio.getLatitude()).append("E0, ")
                    .append(barrio.getLongitude()).append("E0)");
        }
        return values.toString();
    }
}
