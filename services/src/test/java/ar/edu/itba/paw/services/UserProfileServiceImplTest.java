package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.Image;
import ar.edu.itba.paw.model.ProfileTab;
import ar.edu.itba.paw.model.PublicUser;
import ar.edu.itba.paw.model.RatingSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios con Mockito. Donde una rama no deberia consultar algo, el mock tiene un dato senuelo (con
 * lenient()) que cambiaria el resultado si el service lo consultara igual. El corte de cada grupo en paginas (COUNT +
 * LIMIT/OFFSET, pagina fuera de rango) es de EventService y se prueba en EventServiceImplTest.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserProfileServiceImplTest {

    private static final long USER_ID = 7;
    private static final long OTHER_ID = 8;
    private static final int PAGE_SIZE = UserProfileServiceImpl.PAGE_SIZE;

    @Mock
    private UserService userService;
    @Mock
    private EventService eventService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private ImageService imageService;
    @Mock
    private FollowService followService;

    private UserProfileServiceImpl service;

    @Before
    public void setUp() {
        service = new UserProfileServiceImpl(userService, eventService, reviewService, imageService,
                followService);
        // Por defecto la persona no tiene eventos de ningun grupo ni reseñas (cada test pisa lo que le importa).
        lenient().when(eventService.findUpcomingPublicByCreator(eq(USER_ID), any(Date.class), anyInt(), eq(PAGE_SIZE),
                anyLong())).thenReturn(pageOf(Collections.emptyList(), 0, 1));
        lenient().when(eventService.findPastPublicByCreator(eq(USER_ID), any(Date.class), anyInt(), eq(PAGE_SIZE),
                anyLong())).thenReturn(pageOf(Collections.emptyList(), 0, 1));
        lenient().when(reviewService.findRatingSummaryByCreator(USER_ID)).thenReturn(new RatingSummary(0, 0));
    }

    private static PublicUser activeUser() {
        return new PublicUser(USER_ID, "Tomas Rivas", null, null);
    }

    private static List<Event> events(final long... ids) {
        final Event[] events = new Event[ids.length];
        for (int i = 0; i < ids.length; i++) {
            events[i] = ModelFixtures.event(ids[i]);
        }
        return Arrays.asList(events);
    }

    private static PageResult<Event> pageOf(final List<Event> content, final long total, final int page) {
        return new PageResult<>(content, total, page, PAGE_SIZE);
    }

    private static List<Long> idsOf(final PageResult<Event> page) {
        final Long[] ids = page.getContent().stream().map(Event::getId).toArray(Long[]::new);
        return Arrays.asList(ids);
    }

    private void givenAnActiveUser() {
        when(userService.findActivePublicById(USER_ID)).thenReturn(Optional.of(activeUser()));
    }

    // Los dos totales se cuentan siempre (son los numeros de las pestañas).
    private void givenCounts(final long upcoming, final long past) {
        when(eventService.countUpcomingPublicByCreator(eq(USER_ID), any(Date.class))).thenReturn(upcoming);
        when(eventService.countPastPublicByCreator(eq(USER_ID), any(Date.class))).thenReturn(past);
    }

    @Test
    public void testFindProfileOfAUserThatIsNotActiveOrDoesNotExistIsEmpty() {
        // Arrange: hay eventos de ese id; si el service los consultara igual, no seria vacio
        when(userService.findActivePublicById(USER_ID)).thenReturn(Optional.empty());
        lenient().when(eventService.countPastPublicByCreator(eq(USER_ID), any(Date.class))).thenReturn(3L);

        // Act
        final Optional<UserProfile> profile = service.findProfile(USER_ID, null, null, 1);

        // Assert
        assertFalse(profile.isPresent());
    }

    // ---- la pestaña que se muestra ----

    @Test
    public void testAskingForTheUpcomingTabLoadsOnlyTheUpcomingEvents() {
        // Arrange: los realizados tienen otros eventos de senuelo; si el service cargara esa pagina, se veria
        givenAnActiveUser();
        givenCounts(2, 5);
        when(eventService.findUpcomingPublicByCreator(eq(USER_ID), any(Date.class), eq(1), eq(PAGE_SIZE), eq(2L)))
                .thenReturn(pageOf(events(1, 2), 2, 1));
        lenient().when(eventService.findPastPublicByCreator(eq(USER_ID), any(Date.class), anyInt(), eq(PAGE_SIZE),
                anyLong())).thenReturn(pageOf(events(50, 51), 5, 1));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, ProfileTab.UPCOMING, 1).get();

        // Assert
        assertEquals(ProfileTab.UPCOMING, profile.getTab());
        assertEquals(Arrays.asList(1L, 2L), idsOf(profile.getEvents()));
    }

    @Test
    public void testAskingForThePastTabLoadsOnlyThePastEvents() {
        // Arrange: los proximos tienen otros eventos de senuelo
        givenAnActiveUser();
        givenCounts(2, 3);
        when(eventService.findPastPublicByCreator(eq(USER_ID), any(Date.class), eq(1), eq(PAGE_SIZE), eq(3L)))
                .thenReturn(pageOf(events(5, 6, 7), 3, 1));
        lenient().when(eventService.findUpcomingPublicByCreator(eq(USER_ID), any(Date.class), anyInt(), eq(PAGE_SIZE),
                anyLong())).thenReturn(pageOf(events(60, 61), 2, 1));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, ProfileTab.PAST, 1).get();

        // Assert
        assertEquals(ProfileTab.PAST, profile.getTab());
        assertEquals(Arrays.asList(5L, 6L, 7L), idsOf(profile.getEvents()));
    }

    @Test
    public void testBothTabsKeepTheirOwnTotalEvenThoughOnlyOneIsLoaded() {
        // Arrange
        givenAnActiveUser();
        givenCounts(4, 9);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, ProfileTab.PAST, 1).get();

        // Assert
        assertEquals(4, profile.getUpcomingCount());
        assertEquals(9, profile.getPastCount());
    }

    @Test
    public void testTheRequestedPageIsTheOneOfTheTabThatIsShown() {
        // Arrange: el finder solo responde con la pagina 3 y con el total contado (15); otra combinacion no matchea
        givenAnActiveUser();
        givenCounts(15, 0);
        when(eventService.findUpcomingPublicByCreator(eq(USER_ID), any(Date.class), eq(3), eq(PAGE_SIZE), eq(15L)))
                .thenReturn(pageOf(events(30), 15, 3));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, ProfileTab.UPCOMING, 3).get();

        // Assert
        assertEquals(3, profile.getEvents().getPage());
        assertEquals(15, profile.getEvents().getTotalElements());
    }

    @Test
    public void testWithoutAskingForATabItOpensTheUpcomingOneWhenThePersonHasUpcomingEvents() {
        // Arrange
        givenAnActiveUser();
        givenCounts(2, 8);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals(ProfileTab.UPCOMING, profile.getTab());
    }

    @Test
    public void testWithoutAskingForATabItOpensThePastOneWhenThereAreNoUpcomingEventsButThereArePastOnes() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 8);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals(ProfileTab.PAST, profile.getTab());
    }

    @Test
    public void testWithoutAskingForATabAndWithoutEventsItOpensTheUpcomingOne() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals(ProfileTab.UPCOMING, profile.getTab());
    }

    @Test
    public void testTheTabTheUrlAsksForWinsOverTheDefaultOne() {
        // Arrange: por defecto abriria la de realizados (no hay proximos), pero la URL pide los proximos
        givenAnActiveUser();
        givenCounts(0, 8);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, ProfileTab.UPCOMING, 1).get();

        // Assert
        assertEquals(ProfileTab.UPCOMING, profile.getTab());
    }

    // ---- reseñas ----

    @Test
    public void testFindProfileBringsTheAverageRatingAndTheReviewCountOfTheOrganizedEvents() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 2);
        when(reviewService.findRatingSummaryByCreator(USER_ID)).thenReturn(new RatingSummary(6, 4.5));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals(4.5, profile.getAverageRating(), 0.0001);
        assertEquals(6, profile.getReviewCount());
        assertTrue(profile.isHasReviews());
    }

    @Test
    public void testFindProfileWithoutReviewsHasAZeroAverage() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 2);
        when(reviewService.findRatingSummaryByCreator(USER_ID)).thenReturn(new RatingSummary(0, 0));

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals(0, profile.getAverageRating(), 0.0001);
        assertFalse(profile.isHasReviews());
    }

    // ---- "supereventista" ----

    @Test
    public void testAPersonWhoOrganizedTheMinimumNumberOfEventsIsASuperOrganizer() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, UserProfileService.SUPER_ORGANIZER_MIN_EVENTS);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertTrue(profile.isSuperOrganizer());
    }

    @Test
    public void testAPersonWithOneEventLessThanTheMinimumIsNotASuperOrganizer() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, UserProfileService.SUPER_ORGANIZER_MIN_EVENTS - 1);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertFalse(profile.isSuperOrganizer());
    }

    @Test
    public void testManyUpcomingEventsDoNotMakeASuperOrganizerOnTheirOwn() {
        // Arrange: solo cuentan los realizados, no los que todavia no pasaron
        givenAnActiveUser();
        givenCounts(40, 2);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertFalse(profile.isSuperOrganizer());
    }

    // ---- perfil propio ----

    @Test
    public void testTheProfileIsOwnWhenTheViewerIsThatSamePerson() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, USER_ID, null, 1).get();

        // Assert
        assertTrue(profile.isOwn());
    }

    @Test
    public void testTheProfileIsNotOwnForAnotherViewer() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, OTHER_ID, null, 1).get();

        // Assert
        assertFalse(profile.isOwn());
    }

    @Test
    public void testTheProfileIsNotOwnForAnAnonymousViewer() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertFalse(profile.isOwn());
    }

    @Test
    public void testFindProfileCarriesThePersonsNameAndBio() {
        // Arrange
        when(userService.findActivePublicById(USER_ID))
                .thenReturn(Optional.of(new PublicUser(USER_ID, "Tomas Rivas", "Fotografo y productor", null)));
        givenCounts(0, 0);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertEquals("Tomas Rivas", profile.getUser().getName());
        assertEquals("Fotografo y productor", profile.getUser().getBio());
    }

    // ---- findCoverImage() ----

    private static Image coverImage() {
        final Image image = new Image(new byte[] {1, 2, 3}, "image/png", "portada.png", 3);
        image.setId(33L);
        return image;
    }

    @Test
    public void testFindCoverImageReturnsTheImageTheUserChose() {
        // Arrange: solo el id de la portada del usuario devuelve la imagen
        when(userService.findActivePublicById(USER_ID))
                .thenReturn(Optional.of(new PublicUser(USER_ID, "Tomas Rivas", null, 33L)));
        when(imageService.findById(33L)).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(Long.valueOf(33L), result.get().getId());
    }

    @Test
    public void testFindCoverImageIsEmptyWhenTheUserChoseNone() {
        // Arrange: hay una imagen cualquiera en la base; si el service la devolviera igual, no estaria vacio
        givenAnActiveUser();
        lenient().when(imageService.findById(anyLong())).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    public void testFindCoverImageOfAnAccountThatIsNotActiveIsEmpty() {
        // Arrange: la cuenta suspendida (o sin confirmar) no la devuelve la lectura de cuentas activas, aunque la
        // imagen exista
        when(userService.findActivePublicById(USER_ID)).thenReturn(Optional.empty());
        lenient().when(imageService.findById(33L)).thenReturn(Optional.of(coverImage()));

        // Act
        final Optional<Image> result = service.findCoverImage(USER_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- si quien mira ya sigue a esta persona (el boton Seguir / Dejar de seguir) ----

    @Test
    public void testAViewerWhoFollowsThePersonSeesItAsFollowed() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);
        when(followService.isFollowing(OTHER_ID, USER_ID)).thenReturn(true);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, OTHER_ID, null, 1).get();

        // Assert
        assertTrue(profile.isFollowedByViewer());
    }

    // Anonimo: no sigue a nadie (aunque el senuelo diga lo contrario para un id cualquiera).
    @Test
    public void testAnAnonymousViewerDoesNotFollow() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);
        lenient().when(followService.isFollowing(anyLong(), anyLong())).thenReturn(true);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, null, null, 1).get();

        // Assert
        assertFalse(profile.isFollowedByViewer());
    }

    // En el propio perfil no hay "seguir": aunque el dato dijera que si, queda en false.
    @Test
    public void testTheOwnProfileIsNeverFollowed() {
        // Arrange
        givenAnActiveUser();
        givenCounts(0, 0);
        lenient().when(followService.isFollowing(USER_ID, USER_ID)).thenReturn(true);

        // Act
        final UserProfile profile = service.findProfile(USER_ID, USER_ID, null, 1).get();

        // Assert
        assertFalse(profile.isFollowedByViewer());
    }
}
