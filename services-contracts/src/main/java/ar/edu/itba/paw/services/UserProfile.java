// El perfil publico de una persona, ya armado para mostrarlo: quien es, su biografia, sus eventos organizados
// (proximos y realizados, cada grupo con su propio paginado), su promedio de estrellas y si es un "supereventista".
// Lo devuelve UserProfileService.findProfile(). No trae el mail ni nada privado de la cuenta.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.User;

public class UserProfile {

    private final User user;
    private final boolean own;
    private final PageResult<Event> upcoming;
    private final PageResult<Event> past;
    private final double averageRating;
    private final long reviewCount;
    private final boolean superOrganizer;

    public UserProfile(final User user, final boolean own, final PageResult<Event> upcoming,
                       final PageResult<Event> past, final double averageRating, final long reviewCount,
                       final boolean superOrganizer) {
        this.user = user;
        this.own = own;
        this.upcoming = upcoming;
        this.past = past;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.superOrganizer = superOrganizer;
    }

    public User getUser() { return user; }

    // Quien mira es la propia persona: ve el boton para editar su biografia.
    public boolean isOwn() { return own; }

    // Eventos publicos que todavia no pasaron, el mas cercano primero.
    public PageResult<Event> getUpcoming() { return upcoming; }

    // Eventos publicos que ya pasaron, el mas reciente primero.
    public PageResult<Event> getPast() { return past; }

    // Los eventos realizados que organizo: el total (COUNT), no solo los de esta pagina.
    public long getOrganizedCount() { return past.getTotalElements(); }

    public long getUpcomingCount() { return upcoming.getTotalElements(); }

    // Promedio de las reseñas de todos sus eventos organizados; 0 si no tiene.
    public double getAverageRating() { return averageRating; }

    public long getReviewCount() { return reviewCount; }

    public boolean isHasReviews() { return reviewCount > 0; }

    // Organizo al menos UserProfileService.SUPER_ORGANIZER_MIN_EVENTS eventos: la vista le da un cartel.
    public boolean isSuperOrganizer() { return superOrganizer; }
}
