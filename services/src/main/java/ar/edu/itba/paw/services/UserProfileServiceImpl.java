// Implementa el contrato UserProfileService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.ProfileTab;
import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.model.RatingSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    // Tamaño de pagina de la lista de eventos (el mismo que el listado de /eventos).
    static final int PAGE_SIZE = 12;

    private final UserService userService;
    private final EventService eventService;
    private final ReviewService reviewService;
    private final ImageService imageService;

    @Autowired
    public UserProfileServiceImpl(final UserService userService, final EventService eventService,
                                  final ReviewService reviewService, final ImageService imageService) {
        this.userService = userService;
        this.eventService = eventService;
        this.reviewService = reviewService;
        this.imageService = imageService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Image> findCoverImage(final long userId) {
        return userService.findActivePublicById(userId)
                .map(PublicUser::getCoverImageId)
                .flatMap(imageService::findById);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfile(final long userId, final Long viewerId, final ProfileTab requestedTab,
                                             final int page) {
        final Optional<PublicUser> found = userService.findActivePublicById(userId);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        final PublicUser user = found.get();
        // El mismo "ahora" para los dos grupos: un evento no puede caer en los dos ni en ninguno.
        final Date now = new Date();

        // Los dos totales se cuentan siempre (son los numeros de las pestañas), pero solo se carga la pagina de la
        // pestaña que se ve. Se divide, se ordena y se corta en SQL (COUNT + LIMIT/OFFSET con el mismo WHERE).
        final long upcomingCount = eventService.countUpcomingPublicByCreator(userId, now);
        final long pastCount = eventService.countPastPublicByCreator(userId, now);
        final ProfileTab tab = requestedTab != null ? requestedTab : defaultTab(upcomingCount, pastCount);
        final PageResult<Event> events = tab == ProfileTab.PAST
                ? eventService.findPastPublicByCreator(userId, now, page, PAGE_SIZE, pastCount)
                : eventService.findUpcomingPublicByCreator(userId, now, page, PAGE_SIZE, upcomingCount);

        // Cuantas reseñas y el promedio salen de UNA consulta (COUNT + AVG en la base, sin traer las reseñas).
        final RatingSummary rating = reviewService.findRatingSummaryByCreator(userId);

        return Optional.of(new UserProfile(user, user.isSameAccountAs(viewerId), tab, events, upcomingCount,
                pastCount, rating.getAverage(), rating.getCount(), pastCount >= SUPER_ORGANIZER_MIN_EVENTS));
    }

    // Sin pedir pestaña se abre la de proximos; si no tiene ninguno pero si realizados, esa (no mostrar una lista vacia
    // teniendo algo para ver).
    private static ProfileTab defaultTab(final long upcomingCount, final long pastCount) {
        return upcomingCount == 0 && pastCount > 0 ? ProfileTab.PAST : ProfileTab.UPCOMING;
    }
}
