package ar.edu.itba.paw.model;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Las palabras de la barra de busqueda de /eventos: lo que escribio la persona, ya normalizado. Es la unica
 * definicion de "que es una palabra" para todas las capas (la pantalla, el service que elige el orden y la
 * consulta que busca), asi nadie interpreta el texto distinto.
 *
 * Normalizar es: minusculas, sin tildes ni dieresis (la ñ es una n con tilde: queda n) y sin puntuacion (cualquier
 * cosa que no sea letra o numero separa palabras). Una palabra de PLURAL_MIN_LENGTH letras o mas que termina en s se
 * busca sin esa s ("bandas" busca "banda"; vale en espanol y en ingles). Una palabra repetida cuenta una vez, y el
 * texto se acota (ver los limites) para que una busqueda nunca arme una consulta desmedida.
 *
 * Como en esta salida solo hay letras y numeros, una palabra jamas lleva %, _ ni \: no hay nada que escapar para
 * usarla en un LIKE.
 */
public final class SearchTerms {

    /** Largo maximo del texto que se mira; lo que sobra se ignora. */
    public static final int MAX_TEXT_LENGTH = 100;

    /** Cuantas palabras distintas se usan como mucho (las primeras). */
    public static final int MAX_TERMS = 6;

    /** Largo maximo de una palabra; una mas larga se corta. */
    public static final int MAX_TERM_LENGTH = 30;

    /** Desde cuantas letras una palabra que termina en s se busca sin esa s (ver parse()). */
    public static final int PLURAL_MIN_LENGTH = 4;

    /**
     * Desde cuantas letras una palabra tolera un error de tipeo (ver toleratesTypos()). Las mas cortas tienen que
     * coincidir exacto: con tres letras, un error de una letra ya es otra palabra.
     */
    public static final int TYPO_TOLERANCE_MIN_LENGTH = 4;

    private SearchTerms() {
    }

    /** Las palabras del texto, en el orden en que aparecen. Vacia si no hay ninguna (texto nulo, en blanco o solo signos). */
    public static List<String> parse(final String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        final String bounded = text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
        final String folded = Normalizer.normalize(bounded, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        final Set<String> terms = new LinkedHashSet<>();
        for (final String word : folded.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isEmpty()) {
                terms.add(withoutFinalS(word.length() > MAX_TERM_LENGTH ? word.substring(0, MAX_TERM_LENGTH) : word));
            }
            if (terms.size() == MAX_TERMS) {
                break;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(terms));
    }

    // "bandas" -> "banda", "events" -> "event": la s final de un plural (sirve en espanol y en ingles) no cuenta. Solo la
    // s, y solo en palabras de PLURAL_MIN_LENGTH letras o mas ("bus" o "mas" no son plurales). Nunca se pierde un
    // resultado: la palabra sin la s es un prefijo de la original, asi que todo lo que contenia a una contiene a la otra.
    private static String withoutFinalS(final String word) {
        return word.length() >= PLURAL_MIN_LENGTH && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
    }

    /** Si la palabra tolera un error (una letra de mas, de menos, cambiada, o dos seguidas intercambiadas). */
    public static boolean toleratesTypos(final String term) {
        return term.length() >= TYPO_TOLERANCE_MIN_LENGTH;
    }
}
