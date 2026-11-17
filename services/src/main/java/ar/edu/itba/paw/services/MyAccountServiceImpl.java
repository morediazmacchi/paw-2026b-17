// Implementa el contrato MyAccountService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.SavedSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final CategoryService categoryService;

    @Autowired
    public MyAccountServiceImpl(final InboxService inboxService, final EventChatService eventChatService,
                                final EventService eventService, final ReviewService reviewService,
                                final RegistrationService registrationService,
                                final EventInvitationService eventInvitationService,
                                final SavedSearchService savedSearchService, final CategoryService categoryService) {
        this.inboxService = inboxService;
        this.eventChatService = eventChatService;
        this.eventService = eventService;
        this.reviewService = reviewService;
        this.registrationService = registrationService;
        this.eventInvitationService = eventInvitationService;
        this.savedSearchService = savedSearchService;
        this.categoryService = categoryService;
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
        final Map<Long, String> names = categoryNames();
        return new PageResult<>(searches.getContent().stream()
                .map(search -> withCategoryNames(search, names))
                .collect(Collectors.toList()),
                searches.getTotalElements(), searches.getPage(), searches.getPageSize());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedSearchWithCategoryNames> findSavedSearch(final long savedSearchId) {
        return savedSearchService.findById(savedSearchId)
                .map(search -> withCategoryNames(search, categoryNames()));
    }

    // Las categorias son pocas y fijas: se traen todas una vez por pantalla, no una consulta por lista.
    private Map<Long, String> categoryNames() {
        return categoryService.findAll().stream().collect(Collectors.toMap(Category::getId, Category::getName));
    }

    // En el orden de sus ids; una categoria que ya no existe (borrada despues de guardar la busqueda) no aparece.
    private static SavedSearchWithCategoryNames withCategoryNames(final SavedSearch search,
                                                                  final Map<Long, String> names) {
        final List<String> categoryNames = search.getCategoryIds().stream()
                .sorted()
                .map(names::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new SavedSearchWithCategoryNames(search, categoryNames);
    }
}
