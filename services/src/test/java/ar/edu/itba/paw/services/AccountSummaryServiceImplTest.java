package ar.edu.itba.paw.services;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AccountSummaryServiceImplTest {

    private static final long USER_ID = 5;

    @Mock
    private RegistrationService registrationService;
    @Mock
    private FavoriteService favoriteService;
    @Mock
    private EventService eventService;
    @Mock
    private EventInvitationService eventInvitationService;

    private AccountSummaryServiceImpl accountSummaryService;

    @Before
    public void setUp() {
        accountSummaryService = new AccountSummaryServiceImpl(registrationService, favoriteService, eventService,
                eventInvitationService);
        when(registrationService.countByUserId(USER_ID)).thenReturn(3L);
        when(eventService.countByCreatorId(USER_ID)).thenReturn(2L);
        when(eventInvitationService.countByUser(USER_ID)).thenReturn(1L);
    }

    @Test
    public void testCountsForARegularUserHoldEachCount() {
        // Arrange
        when(favoriteService.countViewable(USER_ID, false)).thenReturn(4L);

        // Act
        final AccountCounts result = accountSummaryService.countsFor(USER_ID, false);

        // Assert
        assertEquals(3L, result.getRegistrations());
        assertEquals(4L, result.getFavorites());
        assertEquals(2L, result.getEvents());
        assertEquals(1L, result.getInvitations());
    }

    @Test
    public void testCountsForAnAdminUseTheAdminFavoritesCount() {
        // Arrange: el admin ve mas favoritos que el resto
        when(favoriteService.countViewable(USER_ID, true)).thenReturn(9L);
        lenient().when(favoriteService.countViewable(USER_ID, false)).thenReturn(4L);

        // Act
        final AccountCounts result = accountSummaryService.countsFor(USER_ID, true);

        // Assert
        assertEquals(9L, result.getFavorites());
    }
}
