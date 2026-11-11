package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.SearchTerms;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La barra de busqueda de /eventos como SQL: el pedazo de FROM (un JOIN) que deja solo los eventos que contienen
 * TODAS las palabras buscadas y les da un puntaje de relevancia. Lo usan search(), countSearch() y
 * findRecommendedPage() de EventJpaDao (el filtro es el mismo en las tres, asi el total, la pagina y el orden por
 * recomendados nunca se desincronizan), y search() ademas ordena por ese puntaje con EventSort.RELEVANCIA.
 *
 * Que es una palabra lo define SearchTerms (minusculas, sin tildes, sin puntuacion). Aca se hace lo mismo del lado
 * de la base con el titulo y la descripcion: TRANSLATE saca tildes y vuelve espacio la puntuacion, LOWER pasa a
 * minusculas. TRANSLATE va ANTES de LOWER a proposito: asi tambien anda con una base de locale "C", donde LOWER no
 * conoce las mayusculas acentuadas. Nada de esto usa extensiones (unaccent, pg_trgm): son funciones estandar que
 * corren igual en Postgres y en HSQLDB.
 *
 * Por cada palabra se prueba, en este orden (gana el primer escalon que se cumple):
 *
 *   TITLE_WORD_STARTS   5   alguna palabra del titulo EMPIEZA con ella
 *   TITLE_CONTAINS      4   el titulo la CONTIENE (en cualquier lugar)
 *   TITLE_TYPO          3   el titulo tiene una palabra a UN error de distancia (solo si la palabra tiene
 *                           SearchTerms.TYPO_TOLERANCE_MIN_LENGTH letras o mas): una letra de mas, de menos, distinta,
 *                           o dos letras seguidas intercambiadas
 *   DESCRIPTION_CONTAINS 2  la descripcion la CONTIENE (exacto: la descripcion no tolera errores)
 *
 * Una palabra que no cumple ninguno no tiene puntaje y el evento queda afuera (AND entre palabras). El puntaje del
 * evento es la suma de los de sus palabras; el desempate lo pone quien ordena (fecha, id).
 *
 * El error de una letra no se calcula con una distancia: se generan (en Java) todas las palabras posibles a un error
 * de la buscada, como patrones de LIKE donde "_" es "una letra cualquiera", y se compara contra palabras enteras del
 * titulo (el texto va rodeado de espacios). Una palabra de n letras genera unos 4n patrones. Limite conocido: "_"
 * tambien matchea un espacio, asi que dos palabras muy cortas seguidas pueden pasar por una palabra con un error.
 */
final class TextSearchSql {

    static final int TITLE_WORD_STARTS = 5;
    static final int TITLE_CONTAINS = 4;
    static final int TITLE_TYPO = 3;
    static final int DESCRIPTION_CONTAINS = 2;

    /** Mas relevante primero; el desempate es el mismo que el de los otros ordenes (el que ocurre antes, despues el id). */
    static final String ORDER_BY = "m.score DESC, e.event_date ASC, e.id ASC";

    // Letras con tilde (y la ñ) que se vuelven su letra sin tilde. Mayusculas y minusculas van a la minuscula.
    private static final String[][] PLAIN_LETTERS = {
            {"àáâãäå", "a"}, {"ç", "c"}, {"èéêë", "e"}, {"ìíîï", "i"}, {"ñ", "n"},
            {"òóôõö", "o"}, {"ùúûü", "u"}, {"ýÿ", "y"}};

    // Signos que separan palabras en el texto del evento (lo mismo que SearchTerms hace con lo que escribe la persona).
    private static final String SEPARATORS = ".,;:!?¡¿()[]{}\"'-_/\\+*&#@|<>=~^%$`·•«»“”‘’–—…\n\r\t";

    private static final String TRANSLATE_FROM;
    private static final String TRANSLATE_TO;

    static {
        final StringBuilder from = new StringBuilder();
        final StringBuilder to = new StringBuilder();
        for (final String[] group : PLAIN_LETTERS) {
            for (final char letter : group[0].toCharArray()) {
                from.append(letter).append(Character.toUpperCase(letter));
                to.append(group[1]).append(group[1]);
            }
        }
        for (final char separator : SEPARATORS.toCharArray()) {
            from.append(separator);
            to.append(' ');
        }
        TRANSLATE_FROM = from.toString();
        TRANSLATE_TO = to.toString();
    }

    private final String join;
    private final Map<String, Object> params;

    private TextSearchSql(final String join, final Map<String, Object> params) {
        this.join = join;
        this.params = params;
    }

    /** El JOIN (empieza con " JOIN ...") que va despues de "FROM event e": deja m.id y m.score. */
    String join() {
        return join;
    }

    /** Los parametros con nombre que usa el JOIN. */
    Map<String, Object> params() {
        return params;
    }

    /** terms: no vacia (SearchTerms.parse()). */
    static TextSearchSql of(final List<String> terms) {
        final Map<String, Object> params = new LinkedHashMap<>();
        params.put("tsFrom", TRANSLATE_FROM);
        params.put("tsTo", TRANSLATE_TO);

        final StringBuilder scores = new StringBuilder();
        final StringBuilder total = new StringBuilder();
        final StringBuilder allMatch = new StringBuilder();
        for (int k = 0; k < terms.size(); k++) {
            final String term = terms.get(k);
            scores.append(", CASE WHEN n.t LIKE ").append(bind(params, "% " + term + "%"))
                    .append(" THEN ").append(TITLE_WORD_STARTS)
                    .append(" WHEN n.t LIKE ").append(bind(params, "%" + term + "%"))
                    .append(" THEN ").append(TITLE_CONTAINS);
            if (SearchTerms.toleratesTypos(term)) {
                scores.append(" WHEN (");
                String separator = "";
                for (final String pattern : typoPatterns(term)) {
                    scores.append(separator).append("n.t LIKE ").append(bind(params, "% " + pattern + " %"));
                    separator = " OR ";
                }
                scores.append(") THEN ").append(TITLE_TYPO);
            }
            scores.append(" WHEN n.d LIKE ").append(bind(params, "%" + term + "%"))
                    .append(" THEN ").append(DESCRIPTION_CONTAINS)
                    .append(" ELSE 0 END AS s").append(k);
            total.append(k == 0 ? "" : " + ").append("x.s").append(k);
            allMatch.append(k == 0 ? "" : " AND ").append("x.s").append(k).append(" > 0");
        }

        // El OFFSET 0 de la subconsulta de adentro es una valla a proposito, no un resto: sin ella Postgres "aplana" la
        // subconsulta y vuelve a calcular el TRANSLATE/LOWER del titulo y la descripcion en CADA LIKE (cientos por fila),
        // y la busqueda pasa de ~0,3 s a mas de 4 s con 3.000 eventos. Con la valla se normaliza una vez por evento.
        // Solo se normalizan los eventos que el listado puede mostrar (las mismas reglas que el WHERE de afuera).
        final String join = " JOIN (SELECT x.id AS id, (" + total + ") AS score FROM ("
                + "SELECT n.id AS id" + scores + " FROM ("
                + "SELECT y.id AS id, " + normalized("y.title") + " AS t, " + normalized("y.description") + " AS d"
                + " FROM event y WHERE " + EventJpaDao.listingRules("y") + " OFFSET 0) n) x"
                + " WHERE " + allMatch + ") m ON m.id = e.id";
        return new TextSearchSql(join, params);
    }

    // El texto en minusculas, sin tildes ni puntuacion, con un espacio al principio y otro al final (para poder
    // buscar "palabra entera" como "% palabra %").
    private static String normalized(final String column) {
        return "LOWER(' ' || TRANSLATE(COALESCE(" + column + ", ''), CAST(:tsFrom AS VARCHAR(200)),"
                + " CAST(:tsTo AS VARCHAR(200))) || ' ')";
    }

    // Todas las palabras a un error de "term", como patrones de LIKE.
    static Set<String> typoPatterns(final String term) {
        final Set<String> patterns = new LinkedHashSet<>();
        final int n = term.length();
        for (int i = 0; i < n; i++) {
            // una letra distinta (con "_" en su lugar tambien entra la palabra exacta)
            patterns.add(term.substring(0, i) + "_" + term.substring(i + 1));
        }
        for (int i = 0; i <= n; i++) {
            // la palabra del evento tiene una letra de mas
            patterns.add(term.substring(0, i) + "_" + term.substring(i));
        }
        for (int i = 0; i < n; i++) {
            // a la palabra del evento le falta una letra
            patterns.add(term.substring(0, i) + term.substring(i + 1));
        }
        for (int i = 0; i + 1 < n; i++) {
            // dos letras seguidas intercambiadas
            final char[] letters = term.toCharArray();
            final char first = letters[i];
            letters[i] = letters[i + 1];
            letters[i + 1] = first;
            patterns.add(new String(letters));
        }
        return patterns;
    }

    // Registra el valor con un nombre nuevo y devuelve su referencia (":nombre") para el SQL.
    private static String bind(final Map<String, Object> params, final Object value) {
        final String name = "ts" + params.size();
        params.put(name, value);
        return ":" + name;
    }
}
