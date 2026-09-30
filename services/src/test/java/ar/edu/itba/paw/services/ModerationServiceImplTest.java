package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventReportDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private EventReportDao mockEventReportDao;
    @Mock
    private EventService mockEventService;
    @Mock
    private UserService mockUserService;
    @Mock
    private NotificationService mockNotificationService;
    @Mock
    private InboxService mockInboxService;

    @InjectMocks
    private ModerationServiceImpl moderationService;

    private static User someUser(final long id) {
        final User user = new User("user" + id + "@mail.com", "Usuario " + id);
        user.setId(id);
        return user;
    }

    private static Event someEvent() {
        final Event event = new Event();
        event.setId(EVENT_ID);
        event.setCreatorId(CREATOR_ID);
        event.setCreatorEmail(CREATOR_EMAIL);
        return event;
    }

    private static EventReport someChangesRequestedReport() {
        final EventReport report = somePendingReport();
        report.setStatus(ReportStatus.CHANGES_REQUESTED);
        return report;
    }

    private static EventReport somePendingReport() {
        final EventReport report = new EventReport(EVENT_ID, REPORTER_ID, REASON);
        report.setId(REPORT_ID);
        return report;
    }

    // ---- report() ----

    @Test
    public void testReportCreatesAPendingReport() {
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final ReportResult result = moderationService.report(EVENT_ID, someUser(REPORTER_ID), REASON);

        assertEquals(ReportResult.CREATED, result);
        verify(mockEventReportDao).create(any(EventReport.class));
    }

    @Test
    public void testReportByTheEventsOwnCreatorReturnsOwnEvent() {
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final ReportResult result = moderationService.report(EVENT_ID, someUser(CREATOR_ID), REASON);

        assertEquals(ReportResult.OWN_EVENT, result);
        verify(mockEventReportDao, never()).create(any());
    }

    @Test
    public void testReportTwiceBySameReporterWhileTheFirstIsStillPendingReturnsAlreadyReported() {
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockEventReportDao.existsPendingByEventAndReporter(EVENT_ID, REPORTER_ID)).thenReturn(true);

        final ReportResult result = moderationService.report(EVENT_ID, someUser(REPORTER_ID), REASON);

        assertEquals(ReportResult.ALREADY_REPORTED, result);
        verify(mockEventReportDao, never()).create(any());
    }

    // ---- requestChanges() ----

    @Test
    public void testRequestChangesMovesThePendingReportToChangesRequested() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventReportDao).requestChanges(REPORT_ID, CHANGES_MESSAGE, ADMIN_ID);
    }

    @Test
    public void testRequestChangesNotifiesTheCreatorByMailAndInbox() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final Event event = someEvent();
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));

        moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        verify(mockNotificationService).notifyEventChangesRequested(event, CHANGES_MESSAGE);
        verify(mockInboxService).add(CREATOR_EMAIL, InboxMessageType.EVENT_CHANGES_REQUESTED, EVENT_ID, null,
                CHANGES_MESSAGE);
    }

    @Test
    public void testRequestChangesWhenTheReportDoesNotExistReturnsNotFound() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        assertEquals(ReportResolutionResult.NOT_FOUND, result);
        verify(mockEventReportDao, never()).requestChanges(eq(REPORT_ID), any(), eq(ADMIN_ID));
    }

    @Test
    public void testRequestChangesWhenAlreadyHasChangesRequestedReturnsNotFound() {
        // No se puede "pedir cambios" dos veces sin resolver el primer pedido.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));

        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        assertEquals(ReportResolutionResult.NOT_FOUND, result);
        verify(mockEventReportDao, never()).requestChanges(eq(REPORT_ID), any(), eq(ADMIN_ID));
    }

    // ---- listChangesRequested() ----

    @Test
    public void testListChangesRequestedDelegatesToTheDao() {
        when(mockEventReportDao.findChangesRequested())
                .thenReturn(java.util.Collections.singletonList(someChangesRequestedReport()));

        assertEquals(1, moderationService.listChangesRequested().size());
    }

    // ---- dismiss() ----

    @Test
    public void testDismissResolvesAPendingReportAsDismissed() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));

        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventReportDao).resolve(REPORT_ID, ReportStatus.DISMISSED, ADMIN_ID);
    }

    @Test
    public void testDismissAlsoResolvesAReportWithChangesRequested() {
        // El creador ya corrigio: el admin descarta el pedido de cambios.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));

        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventReportDao).resolve(REPORT_ID, ReportStatus.DISMISSED, ADMIN_ID);
    }

    @Test
    public void testDismissWhenTheReportDoesNotExistReturnsNotFound() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testDismissWhenAlreadyResolvedReturnsNotFound() {
        final EventReport resolved = somePendingReport();
        resolved.setStatus(ReportStatus.ACTIONED);
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(resolved));

        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        assertEquals(ReportResolutionResult.NOT_FOUND, result);
        verify(mockEventReportDao, never()).resolve(eq(REPORT_ID), any(), eq(ADMIN_ID));
    }

    // ---- removeEvent() ----

    @Test
    public void testRemoveEventRemovesTheEventAndResolvesTheReport() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventService).removeByAdmin(EVENT_ID, REASON);
        verify(mockEventReportDao).resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID);
        verify(mockEventReportDao).resolveAllPendingForEvent(EVENT_ID, ReportStatus.ACTIONED, ADMIN_ID);
        verify(mockUserService, never()).suspend(CREATOR_ID);
    }

    @Test
    public void testRemoveEventAlsoRemovesAReportWithChangesRequested() {
        // El creador NO corrigio: el admin da de baja aunque ya le habian pedido cambios.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventService).removeByAdmin(EVENT_ID, REASON);
        verify(mockEventReportDao).resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID);
    }

    @Test
    public void testRemoveEventWithSuspendOrganizerAlsoSuspendsTheCreator() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        moderationService.removeEvent(REPORT_ID, ADMIN_ID, true);

        verify(mockUserService).suspend(CREATOR_ID);
    }

    @Test
    public void testRemoveEventWhenTheReportDoesNotExistReturnsNotFound() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        assertEquals(ReportResolutionResult.NOT_FOUND, result);
        verify(mockEventService, never()).removeByAdmin(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    public void testRemoveEventWhenTheEventWasAlreadyCancelledStillResolvesTheReportButDoesNotCancelAgain() {
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));

        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        assertEquals(ReportResolutionResult.RESOLVED, result);
        verify(mockEventService, never()).removeByAdmin(EVENT_ID, REASON);
        verify(mockEventReportDao).resolve(REPORT_ID, ReportStatus.ACTIONED, ADMIN_ID);
    }

    // ---- listPending() ----

    @Test
    public void testListPendingDelegatesToTheDao() {
        when(mockEventReportDao.findPending()).thenReturn(java.util.Collections.singletonList(somePendingReport()));

        assertEquals(1, moderationService.listPending().size());
    }
}
