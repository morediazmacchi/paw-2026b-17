package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventSearchCriteria;
import ar.edu.itba.paw.model.SavedSearch;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventDao;
import ar.edu.itba.paw.persistence.SavedSearchDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: los DAOs y los otros services son mocks (@Mock + when) y cada test asierta sobre lo que
// DEVUELVE el service (el contrato), sin verify.
@RunWith(MockitoJUnitRunner.class)
public class SavedSearchServiceImplTest {

    private static final long USER_ID = 1;
    private static final long OTHER_USER_ID = 2;
    private static final long SEARCH_ID = 7;

    @Mock
    private SavedSearchDao savedSearchDao;
    @Mock
    private EventDao eventDao;
    @Mock
    private InboxService inboxService;
    @Mock
    private NotificationService notificationService;

    private SavedSearchServiceImpl savedSearchService;

    @Before
    public void setUp() {
        savedSearchService = new SavedSearchServiceImpl(savedSearchDao, eventDao, inboxService, notificationService);
    }

    private static EventSearchCriteria palermoCriteria() {
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setBarrios(Collections.singletonList(Barrio.PALERMO));
        criteria.setMaxPrice(new BigDecimal("500"));
        return criteria;
    }

    private static String palermoKey() {
        return SavedSearch.keyOf(null, Collections.singletonList(Barrio.PALERMO), null, new BigDecimal("500"),
                null, null);
    }

    private static SavedSearch savedSearchOf(final long ownerId) {
        final User owner = ModelFixtures.user(ownerId);
        owner.setEmail("ana@mail.com");
        return new SavedSearch(owner, null, Collections.singletonList(Barrio.PALERMO), null, null, null, null);
    }

    // ---- save() ----

    @Test
    public void testSaveStoresANewSearch() {
        // Arrange
        when(savedSearchDao.existsByKey(USER_ID, palermoKey())).thenReturn(false);

        // Act
        final SaveSearchResult result = savedSearchService.save(USER_ID, palermoCriteria());

        // Assert
        assertEquals(SaveSearchResult.SAVED, result);
    }

    @Test
    public void testSaveTheSameFiltersTwiceIsAlreadySaved() {
        // Arrange
        when(savedSearchDao.existsByKey(USER_ID, palermoKey())).thenReturn(true);

        // Act
        final SaveSearchResult result = savedSearchService.save(USER_ID, palermoCriteria());

        // Assert
        assertEquals(SaveSearchResult.ALREADY_SAVED, result);
    }

    // ---- findPageByUser() ----

    @Test
    public void testFindPageByUserReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a SavedSearchServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<SavedSearch> pageTwo = Collections.singletonList(savedSearchOf(USER_ID));
        when(savedSearchDao.countByUserId(USER_ID)).thenReturn(25L);
        when(savedSearchDao.findByUserId(USER_ID, 2, SavedSearchServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<SavedSearch> result = savedSearchService.findPageByUser(USER_ID, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testFindPageByUserClampsAPageBeyondTheLastToTheLastOne() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<SavedSearch> lastPage = Collections.singletonList(savedSearchOf(USER_ID));
        when(savedSearchDao.countByUserId(USER_ID)).thenReturn(25L);
        when(savedSearchDao.findByUserId(USER_ID, 3, SavedSearchServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<SavedSearch> result = savedSearchService.findPageByUser(USER_ID, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    // ---- findById() / findOwnerId() ----

    @Test
    public void testFindByIdReturnsTheSearchWithItsFilters() {
        // Arrange
        final SavedSearch search = savedSearchOf(USER_ID);
        when(savedSearchDao.findByIdWithFilters(SEARCH_ID)).thenReturn(Optional.of(search));

        // Act
        final Optional<SavedSearch> result = savedSearchService.findById(SEARCH_ID);

        // Assert
        assertEquals(Optional.of(search), result);
    }

    @Test
    public void testFindByIdIsEmptyWhenTheSearchDoesNotExist() {
        // Arrange
        when(savedSearchDao.findByIdWithFilters(SEARCH_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<SavedSearch> result = savedSearchService.findById(SEARCH_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindOwnerIdReturnsTheIdOfTheOwner() {
        // Arrange
        when(savedSearchDao.findUserIdById(SEARCH_ID)).thenReturn(Optional.of(USER_ID));

        // Act
        final Optional<Long> result = savedSearchService.findOwnerId(SEARCH_ID);

        // Assert
        assertEquals(Optional.of(USER_ID), result);
    }

    @Test
    public void testFindOwnerIdIsEmptyWhenTheSearchDoesNotExist() {
        // Arrange
        when(savedSearchDao.findUserIdById(SEARCH_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<Long> result = savedSearchService.findOwnerId(SEARCH_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- delete() ----

    @Test
    public void testDeleteReturnsTrueWhenTheSearchExisted() {
        // Arrange
        when(savedSearchDao.deleteById(SEARCH_ID)).thenReturn(true);

        // Act
        final boolean result = savedSearchService.delete(SEARCH_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testDeleteReturnsFalseWhenTheSearchWasAlreadyGone() {
        // Arrange
        when(savedSearchDao.deleteById(SEARCH_ID)).thenReturn(false);

        // Act
        final boolean result = savedSearchService.delete(SEARCH_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testSaveIgnoresTheSearchTextAndOnlyLooksAtTheFilters() {
        // Arrange: los mismos filtros ya estaban guardados (sin texto); ahora se guardan con texto escrito
        final EventSearchCriteria criteria = palermoCriteria();
        criteria.setText("rock");
        when(savedSearchDao.existsByKey(USER_ID, palermoKey())).thenReturn(true);

        // Act
        final SaveSearchResult result = savedSearchService.save(USER_ID, criteria);

        // Assert
        assertEquals(SaveSearchResult.ALREADY_SAVED, result);
    }

    // ---- isSaved() ----

    @Test
    public void testIsSavedIgnoresTheSearchText() {
        // Arrange: lo guardado son los filtros; el texto de la barra no es parte de la busqueda guardada
        final EventSearchCriteria criteria = palermoCriteria();
        criteria.setText("rock");
        when(savedSearchDao.existsByKey(USER_ID, palermoKey())).thenReturn(true);

        // Act
        final boolean result = savedSearchService.isSaved(USER_ID, criteria);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsSavedIgnoresTheOrderInWhichTheFiltersWereChosen() {
        // Arrange: guardada con Palermo y Recoleta, consultada con Recoleta y Palermo
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setBarrios(Arrays.asList(Barrio.RECOLETA, Barrio.PALERMO));
        final String savedKey = SavedSearch.keyOf(null, Arrays.asList(Barrio.PALERMO, Barrio.RECOLETA),
                null, null, null, null);
        when(savedSearchDao.existsByKey(USER_ID, savedKey)).thenReturn(true);

        // Act
        final boolean result = savedSearchService.isSaved(USER_ID, criteria);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testIsSavedTreatsTheSamePriceWrittenDifferentlyAsTheSameSearch() {
        // Arrange: 500 y 500.00 son el mismo precio
        final EventSearchCriteria criteria = new EventSearchCriteria();
        criteria.setBarrios(Collections.singletonList(Barrio.PALERMO));
        criteria.setMaxPrice(new BigDecimal("500.00"));
        when(savedSearchDao.existsByKey(USER_ID, palermoKey())).thenReturn(true);

        // Act
        final boolean result = savedSearchService.isSaved(USER_ID, criteria);

        // Assert
        assertTrue(result);
    }

    // ---- notifyNewEvents() ----

    @Test
    public void testNotifyNewEventsOfASearchThatNoLongerExistsNotifiesNothing() {
        // Arrange
        when(savedSearchDao.findByIdWithFilters(SEARCH_ID)).thenReturn(Optional.empty());

        // Act
        final boolean result = savedSearchService.notifyNewEvents(SEARCH_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testNotifyNewEventsWithoutNewEventsNotifiesNothing() {
        // Arrange
        when(savedSearchDao.findByIdWithFilters(SEARCH_ID)).thenReturn(Optional.of(savedSearchOf(USER_ID)));
        when(eventDao.countSearch(any(EventSearchCriteria.class))).thenReturn(0L);

        // Act
        final boolean result = savedSearchService.notifyNewEvents(SEARCH_ID);

        // Assert
        assertFalse(result);
    }

    @Test
    public void testNotifyNewEventsWithNewEventsNotifiesTheOwner() {
        // Arrange
        final Event example = ModelFixtures.event(20L);
        when(savedSearchDao.findByIdWithFilters(SEARCH_ID)).thenReturn(Optional.of(savedSearchOf(USER_ID)));
        when(eventDao.countSearch(any(EventSearchCriteria.class))).thenReturn(3L);
        when(eventDao.search(any(EventSearchCriteria.class))).thenReturn(Collections.singletonList(example));

        // Act
        final boolean result = savedSearchService.notifyNewEvents(SEARCH_ID);

        // Assert
        assertTrue(result);
    }
}
