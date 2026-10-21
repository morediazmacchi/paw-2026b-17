package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventCategory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * S(a, b) en [0, 1]: que tan parecidos son dos eventos. Una suma ponderada de seis similitudes
 * parciales, cada una tambien en [0, 1]:
 *
 *  - categorias: Jaccard entre los conjuntos (sin las categorias ignoradas, ver
 *    SimilarityParameters.getIgnoredCategoryNames());
 *  - precio: por percentil dentro de la distribucion de precios pagos (ver PriceDistribution);
 *  - barrio: 1 - distancia entre centroides / la mayor distancia entre dos barrios;
 *  - hora del dia: distancia circular, 12 hs de diferencia = 0;
 *  - dia de la semana: mismo dia 1, mismo grupo (lun-jue / vie-dom) 0,5, distinto 0;
 *  - mismo organizador: 1 o 0.
 *
 * S = suma(peso * parcial) / suma(peso), donde la suma solo incluye las features que los DOS
 * eventos tienen. Si falta un dato (barrio, organizador, o ninguna categoria comparable) esa
 * feature se descarta y el resto se renormaliza, en vez de inventar un valor neutro.
 *
 * Es una funcion pura: no toca la base ni el reloj (la distribucion de precios se la pasan).
 * Sin estado salvo los parametros, asi que se puede compartir entre hilos.
 */
final class EventSimilarity {

    // La hora y el dia se miran en hora argentina, no en la del servidor.
    static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final int MINUTES_PER_DAY = 24 * 60;
    private static final int HALF_DAY_MINUTES = MINUTES_PER_DAY / 2;
    // De viernes a domingo cuenta como "fin de semana".
    private static final int FIRST_WEEKEND_DAY = 5;

    private final SimilarityParameters parameters;

    EventSimilarity(final SimilarityParameters parameters) {
        this.parameters = parameters;
    }

    double similarity(final Event a, final Event b, final PriceDistribution prices) {
        double weighted = 0;
        double totalWeight = 0;

        final Set<Long> categoriesA = comparableCategories(a);
        final Set<Long> categoriesB = comparableCategories(b);
        if (!categoriesA.isEmpty() || !categoriesB.isEmpty()) {
            weighted += parameters.getWeightCategory() * categoryScore(categoriesA, categoriesB);
            totalWeight += parameters.getWeightCategory();
        }

        weighted += parameters.getWeightPrice() * priceScore(a, b, prices);
        totalWeight += parameters.getWeightPrice();

        if (a.getNeighborhood() != null && b.getNeighborhood() != null) {
            weighted += parameters.getWeightNeighborhood() * neighborhoodScore(a.getNeighborhood(), b.getNeighborhood());
            totalWeight += parameters.getWeightNeighborhood();
        }

        weighted += parameters.getWeightHour() * hourScore(a.getEventDate(), b.getEventDate());
        totalWeight += parameters.getWeightHour();

        weighted += parameters.getWeightWeekday() * weekdayScore(a.getEventDate(), b.getEventDate());
        totalWeight += parameters.getWeightWeekday();

        if (a.getCreatorId() != null && b.getCreatorId() != null) {
            weighted += parameters.getWeightOrganizer() * (a.getCreatorId().equals(b.getCreatorId()) ? 1 : 0);
            totalWeight += parameters.getWeightOrganizer();
        }

        return totalWeight <= 0 ? 0 : weighted / totalWeight;
    }

    // Los ids de las categorias del evento sin las ignoradas ("Otros").
    private Set<Long> comparableCategories(final Event event) {
        final Set<Long> ids = new HashSet<>();
        for (final EventCategory eventCategory : event.getCategories()) {
            if (!parameters.getIgnoredCategoryNames().contains(eventCategory.getCategoryName())) {
                ids.add(eventCategory.getCategoryId());
            }
        }
        return ids;
    }

    // Jaccard. Quien llama garantiza que al menos uno de los dos conjuntos no es vacio.
    static double categoryScore(final Set<Long> a, final Set<Long> b) {
        final Set<Long> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        final int union = a.size() + b.size() - intersection.size();
        return union == 0 ? 0 : (double) intersection.size() / union;
    }

    double priceScore(final Event a, final Event b, final PriceDistribution prices) {
        final boolean freeA = a.isFree();
        final boolean freeB = b.isFree();
        if (freeA && freeB) {
            return 1;
        }
        if (freeA || freeB) {
            // Gratis contra pago: mientras mas barato el pago, mas cerca queda de un gratis, pero
            // nunca llega a parecerse como dos gratis (tope: freeVsPaidFactor).
            final Event paid = freeA ? b : a;
            return parameters.getFreeVsPaidFactor() * (1 - prices.percentile(paid.getPrice()));
        }
        return 1 - Math.abs(prices.percentile(a.getPrice()) - prices.percentile(b.getPrice()));
    }

    static double neighborhoodScore(final Barrio a, final Barrio b) {
        return 1 - a.distanceKmTo(b) / Barrio.MAX_DISTANCE_KM;
    }

    static double hourScore(final Date a, final Date b) {
        final int diff = Math.abs(minuteOfDay(a) - minuteOfDay(b));
        // Circular: las 23:00 y la 01:00 estan a 2 horas, no a 22.
        final int circular = Math.min(diff, MINUTES_PER_DAY - diff);
        return 1 - (double) circular / HALF_DAY_MINUTES;
    }

    static double weekdayScore(final Date a, final Date b) {
        final int dayA = dayOfWeek(a);
        final int dayB = dayOfWeek(b);
        if (dayA == dayB) {
            return 1;
        }
        return (dayA >= FIRST_WEEKEND_DAY) == (dayB >= FIRST_WEEKEND_DAY) ? 0.5 : 0;
    }

    private static int minuteOfDay(final Date date) {
        final ZonedDateTime local = date.toInstant().atZone(ZONE);
        return local.getHour() * 60 + local.getMinute();
    }

    // 1 = lunes ... 7 = domingo.
    private static int dayOfWeek(final Date date) {
        return date.toInstant().atZone(ZONE).getDayOfWeek().getValue();
    }
}
