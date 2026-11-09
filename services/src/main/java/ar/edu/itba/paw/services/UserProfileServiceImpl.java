// Implementa el contrato UserProfileService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.ImageDao;
import ar.edu.itba.paw.persistence.ReviewDao;
import ar.edu.itba.paw.persistence.UserDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    // Tamaño de pagina de cada grupo de eventos (el mismo que el listado de /eventos).
    static final int PAGE_SIZE = 12;

    private final UserDao userDao;
    private final EventDao eventDao;
    private final ReviewDao reviewDao;
    private final ImageDao imageDao;

    @Autowired
    public UserProfileServiceImpl(final UserDao userDao, final EventDao eventDao, final ReviewDao reviewDao,
                                  final ImageDao imageDao) {
        this.userDao = userDao;
        this.eventDao = eventDao;
        this.reviewDao = reviewDao;
        this.imageDao = imageDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Image> findCoverImage(final long userId) {
        return userDao.findById(userId)
                .filter(User::isActive)
                .map(User::getCoverImageId)
                .flatMap(imageDao::findById);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserProfile> findProfile(final long userId, final Long viewerId, final int upcomingPage,
                                             final int pastPage) {
        final Optional<User> found = userDao.findById(userId).filter(User::isActive);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        final User user = found.get();
        // El mismo "ahora" para los dos grupos: un evento no puede caer en los dos ni en ninguno.
        final Date now = new Date();

        // Cada grupo se divide, se ordena y se corta en SQL (COUNT + LIMIT/OFFSET con el mismo WHERE).
        final long upcomingTotal = eventDao.countUpcomingPublicByCreatorId(userId, now);
        final int upcomingEffective = PageResult.clampPage(upcomingPage, upcomingTotal, PAGE_SIZE);
        final List<Event> upcoming = eventDao.findUpcomingPublicByCreatorId(userId, now, upcomingEffective, PAGE_SIZE);

        final long pastTotal = eventDao.countPastPublicByCreatorId(userId, now);
        final int pastEffective = PageResult.clampPage(pastPage, pastTotal, PAGE_SIZE);
        final List<Event> past = eventDao.findPastPublicByCreatorId(userId, now, pastEffective, PAGE_SIZE);

        // El promedio sale de un AVG en la base (sin traer las reseñas); sin reseñas no hace falta preguntarlo.
        final long reviewCount = reviewDao.countByCreatorId(userId);
        final double averageRating = reviewCount == 0 ? 0 : reviewDao.findAverageRatingByCreatorId(userId);

        return Optional.of(new UserProfile(user, user.isSameAccountAs(viewerId),
                new PageResult<>(upcoming, upcomingTotal, upcomingEffective, PAGE_SIZE),
                new PageResult<>(past, pastTotal, pastEffective, PAGE_SIZE),
                averageRating, reviewCount, pastTotal >= SUPER_ORGANIZER_MIN_EVENTS));
    }
}
