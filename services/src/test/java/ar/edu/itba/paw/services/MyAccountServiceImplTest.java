package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.ChatGroup;
import ar.edu.itba.paw.model.EventInvitation;
import ar.edu.itba.paw.model.InboxMessage;
import ar.edu.itba.paw.model.Registration;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Review;
import ar.edu.itba.paw.model.SavedSearch;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Mockito puro: los services de los que se arma cada pantalla son @Mock. Cada stub responde solo con los
// argumentos correctos (el id y el indice de pagina de SU lista), asi el resultado depende de que el service
// le pida a cada uno lo que corresponde; y se asierta sobre lo que devuelve, nunca sobre las llamadas.
@RunWith(MockitoJUnitRunner.class)
public class MyAccountServiceImplTest {

    private static final long USER_ID = 4L;
    private static final long EVENT_ID = 9L;
    private static final long SEARCH_ID = 12L;

    @Mock
    private InboxService inboxService;
    @Mock
    private EventChatService eventChatService;
    @Mock
    private EventService eventService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private EventInvitationService eventInvitationService;
    @Mock
    private SavedSearchService savedSearchService;

    private MyAccountServiceImpl myAccountService;

    @Before
    public void setUp() {
        myAccountService = new MyAccountServiceImpl(inboxService, eventChatService, eventService, reviewService,
                registrationService, eventInvitationService, savedSearchService);
    }

    // Una pagina vacia con ese indice y suficientes elementos para que el indice sea valido.
    private static <T> PageResult<T> pageNumber(final int page) {
        return new PageResult<>(Collections.<T>emptyList(), 100, page, 10);
    }

    private static SavedSearch searchWithCategories(final Category... categories) {
        return new SavedSearch(ModelFixtures.user(USER_ID), Arrays.asList(categories), null, null, null, null, null);
    }

    // ---- findInbox() ----

    @Test
    public void testFindInboxUsesEachIndexForItsOwnList() {
        // Arrange
        when(inboxService.findPageWithEvents(USER_ID, 2))
                .thenReturn(new InboxPage(MyAccountServiceImplTest.<InboxMessage>pageNumber(2)));
        when(eventChatService.findGroupsPage(USER_ID, 3)).thenReturn(MyAccountServiceImplTest.<ChatGroup>pageNumber(3));

        // Act
        final MyInboxView result = myAccountService.findInbox(USER_ID, 2, 3);

        // Assert
        assertEquals(2, result.getInbox().getMessages().getPage());
        assertEquals(3, result.getChatGroups().getPage());
    }

    // ---- findMyEvents() ----

    @Test
    public void testFindMyEventsUsesEachIndexForItsOwnList() {
        // Arrange
        // Cada paso solo responde con lo que le da el anterior (mismo objeto): la pagina de eventos del creador pasa
        // por withRegistrationStats().
        final PageResult<Event> eventsPage = MyAccountServiceImplTest.<Event>pageNumber(2);
        when(eventService.findPageByCreatorId(USER_ID, 2)).thenReturn(eventsPage);
        when(registrationService.withRegistrationStats(eventsPage))
                .thenReturn(MyAccountServiceImplTest.<EventWithRegistrationStats>pageNumber(2));
        when(reviewService.findCreatorReputation(USER_ID, 5))
                .thenReturn(new CreatorReputation(MyAccountServiceImplTest.<Review>pageNumber(5), 4.5));

        // Act
        final MyEventsView result = myAccountService.findMyEvents(USER_ID, 2, 5);

        // Assert
        assertEquals(2, result.getEvents().getPage());
        assertEquals(5, result.getReputation().getPage());
    }

    // ---- findPendingRequests() ----

    @Test
    public void testFindPendingRequestsBringsTheEventAndThePageOfRequests() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(ModelFixtures.event(EVENT_ID)));
        when(registrationService.findPendingSummary(EVENT_ID, 2))
                .thenReturn(new PendingRegistrations(MyAccountServiceImplTest.<Registration>pageNumber(2), 7, false));

        // Act
        final PendingRequestsView result = myAccountService.findPendingRequests(EVENT_ID, 2).get();

        // Assert
        assertEquals(EVENT_ID, result.getEvent().getId().longValue());
        assertEquals(2, result.getPending().getPage());
    }

    @Test
    public void testFindPendingRequestsOfAMissingEventIsEmpty() {
        // Arrange: senuelo -- si el service armara la pantalla igual, no saldria vacia.
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());
        lenient().when(registrationService.findPendingSummary(anyLong(), anyInt()))
                .thenReturn(new PendingRegistrations(MyAccountServiceImplTest.<Registration>pageNumber(1), 7, false));

        // Act
        final Optional<PendingRequestsView> result = myAccountService.findPendingRequests(EVENT_ID, 1);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findInviteScreen() ----

    @Test
    public void testFindInviteScreenBringsTheEventAndThePageOfInvitations() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(ModelFixtures.event(EVENT_ID)));
        when(eventInvitationService.findPageByEvent(EVENT_ID, 3))
                .thenReturn(MyAccountServiceImplTest.<EventInvitation>pageNumber(3));

        // Act
        final InviteView result = myAccountService.findInviteScreen(EVENT_ID, 3).get();

        // Assert
        assertEquals(EVENT_ID, result.getEvent().getId().longValue());
        assertEquals(3, result.getInvitations().getPage());
    }

    @Test
    public void testFindInviteScreenOfAMissingEventIsEmpty() {
        // Arrange: senuelo, como en findPendingRequests().
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());
        lenient().when(eventInvitationService.findPageByEvent(anyLong(), anyInt()))
                .thenReturn(MyAccountServiceImplTest.<EventInvitation>pageNumber(1));

        // Act
        final Optional<InviteView> result = myAccountService.findInviteScreen(EVENT_ID, 1);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findSavedSearches() / findSavedSearch() ----

    @Test
    public void testFindSavedSearchesNamesTheCategoriesInIdOrder() {
        // Arrange
        when(savedSearchService.findPageByUser(USER_ID, 1)).thenReturn(new PageResult<>(
                Collections.singletonList(searchWithCategories(
                        ModelFixtures.category(3L, "Teatro"), ModelFixtures.category(1L, "Musica"))), 1, 1, 10));

        // Act
        final PageResult<SavedSearchWithCategoryNames> result = myAccountService.findSavedSearches(USER_ID, 1);

        // Assert
        assertEquals(Arrays.asList("Musica", "Teatro"), result.getContent().get(0).getCategoryNames());
    }

    @Test
    public void testFindSavedSearchesKeepsThePagingOfTheSearches() {
        // Arrange
        when(savedSearchService.findPageByUser(USER_ID, 2)).thenReturn(new PageResult<>(
                Collections.singletonList(searchWithCategories()), 11, 2, 10));

        // Act
        final PageResult<SavedSearchWithCategoryNames> result = myAccountService.findSavedSearches(USER_ID, 2);

        // Assert
        assertEquals(2, result.getPage());
        assertEquals(2, result.getTotalPages());
    }

    @Test
    public void testFindSavedSearchNamesItsCategories() {
        // Arrange
        when(savedSearchService.findById(SEARCH_ID))
                .thenReturn(Optional.of(searchWithCategories(ModelFixtures.category(1L, "Musica"))));

        // Act
        final List<String> result = myAccountService.findSavedSearch(SEARCH_ID).get().getCategoryNames();

        // Assert
        assertEquals(Collections.singletonList("Musica"), result);
    }

    @Test
    public void testFindSavedSearchThatDoesNotExistIsEmpty() {
        // Arrange
        when(savedSearchService.findById(SEARCH_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<SavedSearchWithCategoryNames> result = myAccountService.findSavedSearch(SEARCH_ID);

        // Assert
        assertFalse(result.isPresent());
    }
}
