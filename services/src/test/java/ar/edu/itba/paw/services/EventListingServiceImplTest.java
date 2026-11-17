package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.EventSort;
import ar.edu.itba.paw.model.RegistrationStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito: los services que usa la pantalla son @Mock. Donde una rama no deberia consultar algo
 * (el visitante sin sesion, o "ver si ya esta guardada" sin pedirlo), el mock tiene un dato senuelo (con lenient())
 * que cambiaria el resultado si el service lo consultara igual.
 */
@RunWith(MockitoJUnitRunner.class)
public class EventListingServiceImplTest {

    private static final long VIEWER_ID = 7;
    private static final int FEATURED_COUNT = 5;

    @Mock
    private EventService eventService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private FavoriteService favoriteService;
    @Mock
    private RegistrationService registrationService;
    @Mock
    private SavedSearchService savedSearchService;

    private EventListingServiceImpl listingService;

    private final List<Event> pageEvents = Arrays.asList(ModelFixtures.event(1), ModelFixtures.event(2));
    private final List<Event> featured = Collections.singletonList(ModelFixtures.event(3));
    private final List<Category> categories = Collections.singletonList(new Category("Musica"));

    @Before
    public void setUp() {
        listingService = new EventListingServiceImpl(
                eventService, categoryService, favoriteService, registrationService, savedSearchService);
    }

    private EventSearchCriteria criteriaOf(final Long viewerId) {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setViewerId(viewerId);
        // Los mocks devuelven lo mismo para cualquier criterio: lo que importa de aca es el viewerId.
        when(eventService.search(criteria)).thenReturn(new EventSearchResult(
                new PageResult<>(pageEvents, 2, 1, 12), EventSort.RELEVANCIA, Arrays.asList(EventSort.RELEVANCIA)));
        when(eventService.findMostRegistered(FEATURED_COUNT)).thenReturn(featured);
        when(categoryService.findAll()).thenReturn(categories);
        return criteria;
    }

    @Test
    public void testTheListingCarriesThePageTheBannerAndTheCategories() {
        // Arrange
        final EventSearchCriteria criteria = criteriaOf(VIEWER_ID);

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, false);

        // Assert
        assertEquals(pageEvents, listing.getPage().getContent());
        assertEquals(featured, listing.getFeatured());
        assertEquals(categories, listing.getCategories());
        assertEquals(EventSort.RELEVANCIA, listing.getEffectiveSort());
    }

    @Test
    public void testAViewerWithSessionGetsTheFavoritesOfThePageOnly() {
        // Arrange
        final EventSearchCriteria criteria = criteriaOf(VIEWER_ID);
        when(favoriteService.findFavoriteEventIds(VIEWER_ID, Arrays.asList(1L, 2L)))
                .thenReturn(new HashSet<>(Collections.singletonList(2L)));

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, false);

        // Assert
        assertEquals(new HashSet<>(Collections.singletonList(2L)), listing.getFavoriteEventIds());
    }

    @Test
    public void testAViewerWithSessionGetsTheirRegistrationStatusesOfThePage() {
        // Arrange
        final EventSearchCriteria criteria = criteriaOf(VIEWER_ID);
        final Map<Long, RegistrationStatus> statuses = new HashMap<>();
        statuses.put(1L, RegistrationStatus.CONFIRMED);
        when(registrationService.findStatusesByEventIds(VIEWER_ID, Arrays.asList(1L, 2L))).thenReturn(statuses);

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, false);

        // Assert
        assertEquals(statuses, listing.getRegistrationStatuses());
    }

    @Test
    public void testAVisitorWithoutSessionGetsNoFavoritesNoStatusesAndNothingSaved() {
        // Arrange: senuelos que mostrarian algo si el service los consultara para el visitante.
        final EventSearchCriteria criteria = criteriaOf(null);
        final Set<Long> decoyFavorites = new HashSet<>(Arrays.asList(1L, 2L));
        final Map<Long, RegistrationStatus> decoyStatuses = new HashMap<>();
        decoyStatuses.put(1L, RegistrationStatus.CONFIRMED);
        lenient().when(favoriteService.findFavoriteEventIds(anyLong(), any())).thenReturn(decoyFavorites);
        lenient().when(registrationService.findStatusesByEventIds(anyLong(), any())).thenReturn(decoyStatuses);
        lenient().when(savedSearchService.isSaved(anyLong(), any())).thenReturn(true);

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, true);

        // Assert
        assertTrue(listing.getFavoriteEventIds().isEmpty());
        assertTrue(listing.getRegistrationStatuses().isEmpty());
        assertFalse(listing.isSearchSaved());
    }

    @Test
    public void testTheSavedFlagIsTrueWhenItWasAskedForAndTheFiltersAreSaved() {
        // Arrange
        final EventSearchCriteria criteria = criteriaOf(VIEWER_ID);
        when(savedSearchService.isSaved(VIEWER_ID, criteria)).thenReturn(true);

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, true);

        // Assert
        assertTrue(listing.isSearchSaved());
    }

    @Test
    public void testTheSavedFlagIsFalseWhenItWasNotAskedForEvenIfTheFiltersAreSaved() {
        // Arrange: senuelo, daria true si el service lo consultara sin que se lo pidan.
        final EventSearchCriteria criteria = criteriaOf(VIEWER_ID);
        lenient().when(savedSearchService.isSaved(VIEWER_ID, criteria)).thenReturn(true);

        // Act
        final EventListing listing = listingService.getListing(criteria, FEATURED_COUNT, false);

        // Assert
        assertFalse(listing.isSearchSaved());
    }
}
