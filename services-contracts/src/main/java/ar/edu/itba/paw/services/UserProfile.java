// El perfil publico de una persona, ya armado para mostrarlo: quien es, su biografia, los eventos organizados de la
// pestaña que se ve (proximos o realizados) con su paginado, cuantos tiene en cada una, su promedio de estrellas y si
// es un "supereventista". Lo devuelve UserProfileService.findProfile(). No trae el mail ni nada privado de la cuenta:
// el usuario es una proyeccion (PublicUser) que ni lo tiene.
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.ProfileTab;
import ar.edu.itba.paw.model.PublicUser;

public class UserProfile {

    private final PublicUser user;
    private final boolean own;
    private final ProfileTab tab;
    private final PageResult<Event> events;
    private final long upcomingCount;
    private final long pastCount;
    private final double averageRating;
    private final long reviewCount;
    private final boolean superOrganizer;
    private final boolean followedByViewer;

    public UserProfile(final PublicUser user, final boolean own, final ProfileTab tab,
                       final PageResult<Event> events, final long upcomingCount, final long pastCount,
                       final double averageRating, final long reviewCount, final boolean superOrganizer,
                       final boolean followedByViewer) {
        this.user = user;
        this.own = own;
        this.tab = tab;
        this.events = events;
        this.upcomingCount = upcomingCount;
        this.pastCount = pastCount;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.superOrganizer = superOrganizer;
        this.followedByViewer = followedByViewer;
    }

    public PublicUser getUser() { return user; }

    // Quien mira es la propia persona: ve el boton para editar su biografia.
    public boolean isOwn() { return own; }

    // La pestaña que se muestra: la que pidio la URL o, sin pedir ninguna, la que decide el service.
    public ProfileTab getTab() { return tab; }

    // La pagina de eventos de la pestaña que se muestra (solo esa se carga; la otra solo se cuenta).
    public PageResult<Event> getEvents() { return events; }

    // Eventos publicos que todavia no pasaron: el total (COUNT), no solo los de esta pagina.
    public long getUpcomingCount() { return upcomingCount; }

    // Los eventos realizados que organizo (publicos, aprobados, no cancelados, ya pasados): el total (COUNT).
    public long getPastCount() { return pastCount; }

    // Promedio de las reseñas de todos sus eventos organizados; 0 si no tiene.
    public double getAverageRating() { return averageRating; }

    public long getReviewCount() { return reviewCount; }

    public boolean isHasReviews() { return reviewCount > 0; }

    // Organizo al menos UserProfileService.SUPER_ORGANIZER_MIN_EVENTS eventos: la vista le da un cartel.
    public boolean isSuperOrganizer() { return superOrganizer; }
    // Quien mira (con sesion) ya sigue a esta persona: el boton dice "Dejar de seguir" en vez de "Seguir".
    public boolean isFollowedByViewer() { return followedByViewer; }
}
