// Implementa el contrato MyAccountService (modulo services-contracts).
package ar.edu.itba.paw.services;

import java.util.Comparator;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.SavedSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MyAccountServiceImpl implements MyAccountService {

    private final InboxService inboxService;
    private final EventChatService eventChatService;
    private final EventService eventService;
    private final ReviewService reviewService;
    private final RegistrationService registrationService;
    private final EventInvitationService eventInvitationService;
    private final SavedSearchService savedSearchService;

    @Autowired
    public MyAccountServiceImpl(final InboxService inboxService, final EventChatService eventChatService,
                                final EventService eventService, final ReviewService reviewService,
                                final RegistrationService registrationService,
                                final EventInvitationService eventInvitationService,
                                final SavedSearchService savedSearchService) {
        this.inboxService = inboxService;
        this.eventChatService = eventChatService;
        this.eventService = eventService;
        this.reviewService = reviewService;
        this.registrationService = registrationService;
        this.eventInvitationService = eventInvitationService;
        this.savedSearchService = savedSearchService;
    }

    @Override
    @Transactional(readOnly = true)
    public MyInboxView findInbox(final long userId, final int page, final int groupsPage) {
        return new MyInboxView(inboxService.findPageWithEvents(userId, page),
                eventChatService.findGroupsPage(userId, groupsPage));
    }

    @Override
    @Transactional(readOnly = true)
    public MyEventsView findMyEvents(final long userId, final int page, final int reviewsPage) {
        return new MyEventsView(registrationService.withRegistrationStats(eventService.findPageByCreatorId(userId, page)),
                reviewService.findCreatorReputation(userId, reviewsPage));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PendingRequestsView> findPendingRequests(final long eventId, final int page) {
        return eventService.findById(eventId)
                .map(event -> new PendingRequestsView(event, registrationService.findPendingSummary(eventId, page)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InviteView> findInviteScreen(final long eventId, final int page) {
        return eventService.findById(eventId)
                .map(event -> new InviteView(event, eventInvitationService.findPageByEvent(eventId, page)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<SavedSearchWithCategoryNames> findSavedSearches(final long userId, final int page) {
        final PageResult<SavedSearch> searches = savedSearchService.findPageByUser(userId, page);
        return new PageResult<>(searches.getContent().stream()
                .map(MyAccountServiceImpl::withCategoryNames)
                .collect(Collectors.toList()),
                searches.getTotalElements(), searches.getPage(), searches.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedSearchWithCategoryNames> findSavedSearch(final long savedSearchId) {
        return savedSearchService.findById(savedSearchId)
                .map(MyAccountServiceImpl::withCategoryNames);
    }

    // En el orden de los ids de las categorias. Los nombres vienen con la busqueda (la relacion con sus categorias).
    private static SavedSearchWithCategoryNames withCategoryNames(final SavedSearch search) {
        final List<String> categoryNames = search.getCategories().stream()
                .sorted(Comparator.comparing(Category::getId))
                .map(Category::getName)
                .collect(Collectors.toList());
        return new SavedSearchWithCategoryNames(search, categoryNames);
    }
}
