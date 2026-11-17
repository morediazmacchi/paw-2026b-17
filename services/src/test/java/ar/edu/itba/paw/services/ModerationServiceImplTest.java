package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.AdminEventSort;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.ReportStatusCounts;
import ar.edu.itba.paw.model.ReportStatusFilter;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.EventReportDao;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

// Unidad 5 - Unit Testing: todas las dependencias son @Mock; los comandos del dao
// (resolve, requestChanges, ...) devuelven boolean/int y el test los stubea con
// argumentos especificos (si el service no los invoca, Mockito falla por stub sin
// usar). Los avisos (inbox y mail) son "disparar y olvidar" (void) y no se testean
// aca: verificar que se llamaron seria testear la implementacion de
// ModerationServiceImpl, no el contrato de ModerationService -- y su propio
// comportamiento ya lo cubren InboxServiceImplTest/NotificationServiceImplTest.
@RunWith(MockitoJUnitRunner.class)
public class ModerationServiceImplTest {

    private static final long EVENT_ID = 1;
    private static final long REPORT_ID = 2;
    private static final long REPORTER_ID = 3;
    private static final long CREATOR_ID = 4;
    private static final long ADMIN_ID = 5;
    private static final String REASON = "Contenido inapropiado";

    private static final String CREATOR_EMAIL = "creador@mail.com";
    private static final String CHANGES_MESSAGE = "La direccion no coincide con el barrio elegido";

    @Mock
    private EventReportDao reportDao;
    @Mock
    private EventService eventService;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private InboxService inboxService;
    @Mock
    private RegistrationService registrationService;

    private ModerationServiceImpl moderationService;

    @Before
    public void setUp() {
        moderationService = new ModerationServiceImpl(reportDao, eventService, userService, notificationService, inboxService,
                registrationService);
    }

    private static User someUser(final long id) {
        final User user = new User("user" + id + "@mail.com", "Usuario " + id);
        user.setId(id);
        return user;
    }

    private static Event someEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreator(ModelFixtures.user(CREATOR_ID));
        event.setCreatorEmail(CREATOR_EMAIL);
        return event;
    }

    private static EventReport somePendingReport() {
        final EventReport report = new EventReport(ModelFixtures.event(EVENT_ID), ModelFixtures.user(REPORTER_ID), REASON);
        report.setId(REPORT_ID);
        return report;
    }

    private static EventReport someChangesRequestedReport() {
        final EventReport report = somePendingReport();
        report.setStatus(ReportStatus.CHANGES_REQUESTED);
        return report;
    }

    // El reporte esta guardado y el evento existe: lo comun a casi todos los tests.
    private void reportOf(final EventReport report) {
        when(reportDao.findById(REPORT_ID)).thenReturn(Optional.of(report));
    }

    private void eventExists(final Event event) {
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    // ---- report() ----

    @Test
    public void testReportReturnsCreatedWhenTheEventExistsAndTheReporterHasNoPendingReport() {
        // Arrange
        eventExists(someEvent());
        when(reportDao.existsPendingByEventAndReporter(EVENT_ID, REPORTER_ID)).thenReturn(false);

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, REPORTER_ID, REASON);

        // Assert
        assertEquals(ReportResult.CREATED, result);
    }

    @Test
    public void testReportWithABlankReasonReturnsInvalid() {
        // Arrange: el evento existe; si el service creara el reporte igual, el resultado seria CREATED
        lenient().when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        lenient().when(reportDao.existsPendingByEventAndReporter(EVENT_ID, REPORTER_ID)).thenReturn(false);

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, REPORTER_ID, "  ");

        // Assert
        assertEquals(ReportResult.INVALID, result);
    }

    @Test
    public void testReportWithAReasonLongerThanTheMaximumReturnsInvalid() {
        // Arrange
        lenient().when(eventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final String tooLong = new String(new char[ModerationService.MAX_REPORT_REASON_LENGTH + 1]).replace('\0', 'a');

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, REPORTER_ID, tooLong);

        // Assert
        assertEquals(ReportResult.INVALID, result);
    }

    @Test
    public void testRequestChangesWithABlankMessageReturnsInvalid() {
        // Arrange: el reporte esta pendiente; si el service pidiera los cambios igual, el resultado seria RESOLVED
        lenient().when(reportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, " ");

        // Assert
        assertEquals(ReportResolutionResult.INVALID, result);
    }

    @Test
    public void testRequestChangesWithAMessageLongerThanTheMaximumReturnsInvalid() {
        // Arrange
        lenient().when(reportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final String tooLong = new String(new char[ModerationService.MAX_CHANGES_MESSAGE_LENGTH + 1]).replace('\0', 'a');

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, tooLong);

        // Assert
        assertEquals(ReportResolutionResult.INVALID, result);
    }

    @Test
    public void testReportTwiceBySameReporterWhileTheFirstIsStillPendingReturnsAlreadyReported() {
        // Arrange
        eventExists(someEvent());
        when(reportDao.existsPendingByEventAndReporter(EVENT_ID, REPORTER_ID)).thenReturn(true);

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, REPORTER_ID, REASON);

        // Assert
        assertEquals(ReportResult.ALREADY_REPORTED, result);
    }

    @Test(expected = EventNotFoundException.class)
    public void testReportOfAnEventThatDoesNotExistThrowsEventNotFoundException() {
        // Arrange
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());

        // Act
        moderationService.report(EVENT_ID, REPORTER_ID, REASON);

        // Assert: la excepcion esperada en @Test(expected=...) ES el assert.
    }

    // ---- requestChanges() ----

    @Test
    public void testRequestChangesMovesThePendingReportToChangesRequested() {
        // Arrange
        reportOf(somePendingReport());
        eventExists(someEvent());
        when(reportDao.requestChanges(REPORT_ID, CHANGES_MESSAGE, ADMIN_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testRequestChangesWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(reportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testRequestChangesWhenAlreadyHasChangesRequestedReturnsNotFound() {
        // Arrange
        // No se puede "pedir cambios" dos veces sin resolver el primer pedido.
        reportOf(someChangesRequestedReport());

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testRequestChangesWhenTheEventNoLongerExistsResolvesTheReportAsActioned() {
        // Arrange
        reportOf(somePendingReport());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());
        when(reportDao.resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    // ---- searchPage() ----

    @Test
    public void testSearchPageReturnsTheRequestedPageWithItsTotals() {
        // Arrange: 25 en total, de a ModerationServiceImpl.PAGE_SIZE por pagina -> 3 paginas
        final List<EventReport> pageTwo = Collections.singletonList(someChangesRequestedReport());
        when(reportDao.countSearch(null, ReportStatusFilter.ALL)).thenReturn(25L);
        when(reportDao.search(null, ReportStatusFilter.ALL, 2, ModerationServiceImpl.PAGE_SIZE)).thenReturn(pageTwo);

        // Act
        final PageResult<EventReport> result = moderationService.searchPage(null, ReportStatusFilter.ALL, 2);

        // Assert
        assertEquals(pageTwo, result.getContent());
        assertEquals(2, result.getPage());
        assertEquals(3, result.getTotalPages());
        assertEquals(25L, result.getTotalElements());
    }

    @Test
    public void testSearchPageShowsTheLastPageWhenThePageIsBeyondIt() {
        // Arrange: 25 en total -> la ultima pagina es la 3
        final List<EventReport> lastPage = Collections.singletonList(someChangesRequestedReport());
        when(reportDao.countSearch(null, ReportStatusFilter.ALL)).thenReturn(25L);
        when(reportDao.search(null, ReportStatusFilter.ALL, 3, ModerationServiceImpl.PAGE_SIZE)).thenReturn(lastPage);

        // Act
        final PageResult<EventReport> result = moderationService.searchPage(null, ReportStatusFilter.ALL, 99);

        // Assert
        assertEquals(3, result.getPage());
        assertEquals(lastPage, result.getContent());
    }

    @Test
    public void testSearchPageShowsTheFirstPageWhenThePageIsBelowOne() {
        // Arrange
        final List<EventReport> firstPage = Collections.singletonList(someChangesRequestedReport());
        when(reportDao.countSearch(null, ReportStatusFilter.ALL)).thenReturn(25L);
        when(reportDao.search(null, ReportStatusFilter.ALL, 1, ModerationServiceImpl.PAGE_SIZE)).thenReturn(firstPage);

        // Act
        final PageResult<EventReport> result = moderationService.searchPage(null, ReportStatusFilter.ALL, -3);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(firstPage, result.getContent());
    }

    @Test
    public void testSearchPageWithNoResultsReturnsAnEmptyFirstPage() {
        // Arrange
        when(reportDao.countSearch("nada", ReportStatusFilter.RESOLVED)).thenReturn(0L);
        when(reportDao.search("nada", ReportStatusFilter.RESOLVED, 1, ModerationServiceImpl.PAGE_SIZE))
                .thenReturn(Collections.emptyList());

        // Act
        final PageResult<EventReport> result = moderationService.searchPage("nada", ReportStatusFilter.RESOLVED, 5);

        // Assert
        assertEquals(1, result.getPage());
        assertEquals(0, result.getTotalPages());
        assertTrue(result.getContent().isEmpty());
    }

    // ---- dismiss() ----

    @Test
    public void testDismissResolvesAPendingReportAsDismissed() {
        // Arrange
        reportOf(somePendingReport());
        when(reportDao.resolve(REPORT_ID, ReportStatus.DISMISSED, ADMIN_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testDismissAlsoResolvesAReportWithChangesRequested() {
        // Arrange
        // El creador ya corrigio: el admin descarta el pedido de cambios.
        reportOf(someChangesRequestedReport());
        when(reportDao.resolve(REPORT_ID, ReportStatus.DISMISSED, ADMIN_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testDismissWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(reportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testDismissWhenAlreadyResolvedReturnsNotFound() {
        // Arrange
        final EventReport resolved = somePendingReport();
        resolved.setStatus(ReportStatus.ACTIONED);
        reportOf(resolved);

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    // ---- removeEvent() ----

    // Baja del evento por moderacion: el caso feliz, con todos los comandos que tiene que ejecutar.
    private void removableEvent(final Event event) {
        reportOf(somePendingReport());
        eventExists(event);
        when(reportDao.resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(true);
        when(reportDao.resolveAllPendingForEvent(EVENT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(1);
    }

    @Test
    public void testRemoveEventRemovesTheEventWithTheReportsReason() {
        // Arrange
        removableEvent(someEvent());
        when(eventService.removeByAdmin(EVENT_ID, REASON)).thenReturn(EventCancelResult.CANCELLED);

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testRemoveEventAlsoRemovesAReportWithChangesRequested() {
        // Arrange
        // El creador NO corrigio: el admin da de baja aunque ya le habian pedido cambios.
        reportOf(someChangesRequestedReport());
        eventExists(someEvent());
        when(eventService.removeByAdmin(EVENT_ID, REASON)).thenReturn(EventCancelResult.CANCELLED);
        when(reportDao.resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(true);
        when(reportDao.resolveAllPendingForEvent(EVENT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(0);

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testRemoveEventWithSuspendOrganizerAlsoSuspendsTheCreator() {
        // Arrange
        removableEvent(someEvent());
        when(eventService.removeByAdmin(EVENT_ID, REASON)).thenReturn(EventCancelResult.CANCELLED);
        when(userService.suspend(CREATOR_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, true);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testRemoveEventWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(reportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testRemoveEventWhenTheEventWasAlreadyCancelledStillResolvesTheReport() {
        // Arrange
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new Date());
        removableEvent(alreadyCancelled);

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    @Test
    public void testRemoveEventWhenTheEventNoLongerExistsResolvesTheReportAsActioned() {
        // Arrange
        reportOf(somePendingReport());
        when(eventService.findById(EVENT_ID)).thenReturn(Optional.empty());
        when(reportDao.resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID)).thenReturn(true);

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
    }

    // ---- searchQueue() ----

    private void searchQueueOf(final EventReport report) {
        when(reportDao.countSearch(null, ReportStatusFilter.ALL)).thenReturn(1L);
        when(reportDao.search(null, ReportStatusFilter.ALL, 1, ModerationServiceImpl.PAGE_SIZE))
                .thenReturn(Collections.singletonList(report));
        lenient().when(reportDao.countStatuses()).thenReturn(new ReportStatusCounts(1, 1, 0, 0, 0));
    }

    @Test
    public void testSearchQueueKeepsThePageOfReports() {
        // Arrange
        final EventReport report = somePendingReport();
        searchQueueOf(report);

        // Act
        final ReportQueuePage result = moderationService.searchQueue(null, ReportStatusFilter.ALL, 1);

        // Assert
        assertEquals(Collections.singletonList(report), result.getReports().getContent());
        assertEquals(1, result.getReports().getPage());
    }

    @Test
    public void testSearchQueueHoldsTheCountsOfEachTab() {
        // Arrange
        final ReportStatusCounts counts = new ReportStatusCounts(4, 1, 1, 1, 1);
        searchQueueOf(somePendingReport());
        when(reportDao.countStatuses()).thenReturn(counts);

        // Act
        final ReportQueuePage result = moderationService.searchQueue(null, ReportStatusFilter.ALL, 1);

        // Assert
        assertSame(counts, result.getCounts());
    }

    @Test
    public void testSearchQueueWithoutReportsIsEmpty() {
        // Arrange
        when(reportDao.countSearch("nada", ReportStatusFilter.ALL)).thenReturn(0L);
        lenient().when(reportDao.countStatuses()).thenReturn(new ReportStatusCounts(0, 0, 0, 0, 0));

        // Act
        final ReportQueuePage result = moderationService.searchQueue("nada", ReportStatusFilter.ALL, 1);

        // Assert
        assertTrue(result.getReports().getContent().isEmpty());
    }

    // ---- markViewed() ----

    @Test
    public void testMarkViewedReturnsTrueWhenTheDaoMarkedIt() {
        // Arrange
        when(reportDao.markViewed(REPORT_ID, ADMIN_ID)).thenReturn(true);

        // Act
        final boolean result = moderationService.markViewed(REPORT_ID, ADMIN_ID);

        // Assert
        assertTrue(result);
    }

    @Test
    public void testMarkViewedReturnsFalseWhenTheReportWasAlreadyViewed() {
        // Arrange
        when(reportDao.markViewed(REPORT_ID, ADMIN_ID)).thenReturn(false);

        // Act
        final boolean result = moderationService.markViewed(REPORT_ID, ADMIN_ID);

        // Assert
        assertFalse(result);
    }

    // ---- findReportTarget() ----

    @Test
    public void testFindReportTargetReturnsWhatTheDaoFinds() {
        // Arrange
        final ReportTarget target = new ReportTarget(EVENT_ID, CREATOR_ID);
        when(reportDao.findTargetById(REPORT_ID)).thenReturn(Optional.of(target));

        // Act
        final Optional<ReportTarget> result = moderationService.findReportTarget(REPORT_ID);

        // Assert
        assertSame(target, result.get());
    }

    @Test
    public void testFindReportTargetOfAReportThatDoesNotExistIsEmpty() {
        // Arrange
        when(reportDao.findTargetById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final Optional<ReportTarget> result = moderationService.findReportTarget(REPORT_ID);

        // Assert
        assertFalse(result.isPresent());
    }

    // ---- findAllEventsPage() ----

    @Test
    public void testFindAllEventsPageAddsTheRegistrationCountsToThePageOfEvents() {
        // Arrange: el stub de las estadisticas solo responde con LA pagina que da EventService (mismo objeto)
        final PageResult<Event> events = new PageResult<>(
                Collections.singletonList(ModelFixtures.event(EVENT_ID)), 1L, 1, 12);
        final PageResult<EventWithRegistrationStats> withStats = new PageResult<>(
                Collections.singletonList(new EventWithRegistrationStats(ModelFixtures.event(EVENT_ID), 5L, 3L)),
                1L, 1, 12);
        when(eventService.findAllEventsPage("feria", AdminEventSort.TITLE_ASC, 1)).thenReturn(events);
        when(registrationService.withRegistrationStats(events)).thenReturn(withStats);

        // Act
        final PageResult<EventWithRegistrationStats> result =
                moderationService.findAllEventsPage("feria", AdminEventSort.TITLE_ASC, 1);

        // Assert
        assertEquals(5L, result.getContent().get(0).getConfirmedCount());
        assertEquals(Long.valueOf(3), result.getContent().get(0).getPendingCount());
    }
}
