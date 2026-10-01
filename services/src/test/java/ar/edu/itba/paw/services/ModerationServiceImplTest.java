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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
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

    // resolve()/resolveAllPendingForEvent() son void: se capturan los
    // argumentos con doAnswer() sobre el stub, nunca con Mockito.verify().
    // "resolve" distingue DISMISSED de ACTIONED -- ambos devuelven el mismo
    // ReportResolutionResult.RESOLVED, asi que sin capturar el status estos
    // dos casos serian indistinguibles para el test.
    private Object[] captureResolveCall() {
        final Object[] captured = new Object[3];
        doAnswer(invocation -> {
            captured[0] = invocation.getArgument(0);
            captured[1] = invocation.getArgument(1);
            captured[2] = invocation.getArgument(2);
            return null;
        }).when(mockEventReportDao).resolve(anyLong(), any(ReportStatus.class), anyLong());
        return captured;
    }

    // ---- report() ----

    @Test
    public void testReportCreatesAPendingReport() {
        // Arrange
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final EventReport[] created = new EventReport[1];
        doAnswer(invocation -> {
            created[0] = invocation.getArgument(0);
            return null;
        }).when(mockEventReportDao).create(any(EventReport.class));

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, someUser(REPORTER_ID), REASON);

        // Assert
        assertEquals(ReportResult.CREATED, result);
        assertEquals(EVENT_ID, created[0].getEventId());
        assertEquals(REPORTER_ID, created[0].getReporterUserId());
        assertEquals(REASON, created[0].getReason());
    }

    @Test
    public void testReportByTheEventsOwnCreatorReturnsOwnEvent() {
        // Arrange
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, someUser(CREATOR_ID), REASON);

        // Assert
        assertEquals(ReportResult.OWN_EVENT, result);
    }

    @Test
    public void testReportTwiceBySameReporterWhileTheFirstIsStillPendingReturnsAlreadyReported() {
        // Arrange
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        when(mockEventReportDao.existsPendingByEventAndReporter(EVENT_ID, REPORTER_ID)).thenReturn(true);

        // Act
        final ReportResult result = moderationService.report(EVENT_ID, someUser(REPORTER_ID), REASON);

        // Assert
        assertEquals(ReportResult.ALREADY_REPORTED, result);
    }

    // ---- requestChanges() ----

    @Test
    public void testRequestChangesMovesThePendingReportToChangesRequested() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Object[] requestChangesArgs = new Object[3];
        doAnswer(invocation -> {
            requestChangesArgs[0] = invocation.getArgument(0);
            requestChangesArgs[1] = invocation.getArgument(1);
            requestChangesArgs[2] = invocation.getArgument(2);
            return null;
        }).when(mockEventReportDao).requestChanges(anyLong(), anyString(), anyLong());

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(REPORT_ID, requestChangesArgs[0]);
        assertEquals(CHANGES_MESSAGE, requestChangesArgs[1]);
        assertEquals(ADMIN_ID, requestChangesArgs[2]);
    }

    @Test
    public void testRequestChangesNotifiesTheCreatorByMailAndInbox() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final Event event = someEvent();
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(event));
        final boolean[] creatorNotified = {false};
        doAnswer(invocation -> {
            creatorNotified[0] = true;
            return null;
        }).when(mockNotificationService).notifyEventChangesRequested(event, CHANGES_MESSAGE);
        final String[] inboxRecipient = new String[1];
        doAnswer(invocation -> {
            inboxRecipient[0] = invocation.getArgument(0);
            return null;
        }).when(mockInboxService).add(any(), any(), any(), any(), any());

        // Act
        moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertTrue(creatorNotified[0]);
        assertEquals(CREATOR_EMAIL, inboxRecipient[0]);
    }

    @Test
    public void testRequestChangesWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testRequestChangesWhenAlreadyHasChangesRequestedReturnsNotFound() {
        // Arrange
        // No se puede "pedir cambios" dos veces sin resolver el primer pedido.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));

        // Act
        final ReportResolutionResult result = moderationService.requestChanges(REPORT_ID, ADMIN_ID, CHANGES_MESSAGE);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    // ---- listChangesRequested() ----

    @Test
    public void testListChangesRequestedDelegatesToTheDao() {
        // Arrange
        when(mockEventReportDao.findChangesRequested())
                .thenReturn(java.util.Collections.singletonList(someChangesRequestedReport()));

        // Act
        final int size = moderationService.listChangesRequested().size();

        // Assert
        assertEquals(1, size);
    }

    // ---- dismiss() ----

    @Test
    public void testDismissResolvesAPendingReportAsDismissed() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final Object[] resolveArgs = captureResolveCall();

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(REPORT_ID, resolveArgs[0]);
        assertEquals(ReportStatus.DISMISSED, resolveArgs[1]);
        assertEquals(ADMIN_ID, resolveArgs[2]);
    }

    @Test
    public void testDismissAlsoResolvesAReportWithChangesRequested() {
        // Arrange
        // El creador ya corrigio: el admin descarta el pedido de cambios.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));
        final Object[] resolveArgs = captureResolveCall();

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(ReportStatus.DISMISSED, resolveArgs[1]);
    }

    @Test
    public void testDismissWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

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
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(resolved));

        // Act
        final ReportResolutionResult result = moderationService.dismiss(REPORT_ID, ADMIN_ID);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    // ---- removeEvent() ----

    @Test
    public void testRemoveEventRemovesTheEventAndResolvesTheReport() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Object[] removeByAdminArgs = new Object[2];
        doAnswer(invocation -> {
            removeByAdminArgs[0] = invocation.getArgument(0);
            removeByAdminArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockEventService).removeByAdmin(anyLong(), anyString());
        final Object[] resolveArgs = captureResolveCall();
        final Object[] resolveAllArgs = new Object[3];
        doAnswer(invocation -> {
            resolveAllArgs[0] = invocation.getArgument(0);
            resolveAllArgs[1] = invocation.getArgument(1);
            resolveAllArgs[2] = invocation.getArgument(2);
            return null;
        }).when(mockEventReportDao).resolveAllPendingForEvent(anyLong(), any(ReportStatus.class), anyLong());
        // suspendOrganizer=false: userService.suspend() no deberia llamarse.
        lenient().doThrow(new AssertionError("no deberia suspender al organizador"))
                .when(mockUserService).suspend(anyLong());

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(EVENT_ID, removeByAdminArgs[0]);
        assertEquals(REASON, removeByAdminArgs[1]);
        assertEquals(REPORT_ID, resolveArgs[0]);
        assertEquals(ReportStatus.ACTIONED, resolveArgs[1]);
        assertEquals(EVENT_ID, resolveAllArgs[0]);
        assertEquals(ReportStatus.ACTIONED, resolveAllArgs[1]);
    }

    @Test
    public void testRemoveEventAlsoRemovesAReportWithChangesRequested() {
        // Arrange
        // El creador NO corrigio: el admin da de baja aunque ya le habian pedido cambios.
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(someChangesRequestedReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final Object[] removeByAdminArgs = new Object[2];
        doAnswer(invocation -> {
            removeByAdminArgs[0] = invocation.getArgument(0);
            removeByAdminArgs[1] = invocation.getArgument(1);
            return null;
        }).when(mockEventService).removeByAdmin(anyLong(), anyString());
        final Object[] resolveArgs = captureResolveCall();

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(EVENT_ID, removeByAdminArgs[0]);
        assertEquals(REASON, removeByAdminArgs[1]);
        assertEquals(ReportStatus.ACTIONED, resolveArgs[1]);
    }

    @Test
    public void testRemoveEventWithSuspendOrganizerAlsoSuspendsTheCreator() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(someEvent()));
        final long[] suspendedId = new long[1];
        doAnswer(invocation -> {
            suspendedId[0] = invocation.getArgument(0);
            return null;
        }).when(mockUserService).suspend(anyLong());

        // Act
        moderationService.removeEvent(REPORT_ID, ADMIN_ID, true);

        // Assert
        assertEquals(CREATOR_ID, suspendedId[0]);
    }

    @Test
    public void testRemoveEventWhenTheReportDoesNotExistReturnsNotFound() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.empty());

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.NOT_FOUND, result);
    }

    @Test
    public void testRemoveEventWhenTheEventWasAlreadyCancelledStillResolvesTheReportButDoesNotCancelAgain() {
        // Arrange
        when(mockEventReportDao.findById(REPORT_ID)).thenReturn(Optional.of(somePendingReport()));
        final Event alreadyCancelled = someEvent();
        alreadyCancelled.setCancelledAt(new java.util.Date());
        when(mockEventService.findById(EVENT_ID)).thenReturn(Optional.of(alreadyCancelled));
        final Object[] resolveArgs = captureResolveCall();
        // Ya esta cancelado: no deberia volver a pedirse removeByAdmin.
        lenient().doThrow(new AssertionError("no deberia volver a cancelar un evento ya cancelado"))
                .when(mockEventService).removeByAdmin(anyLong(), anyString());

        // Act
        final ReportResolutionResult result = moderationService.removeEvent(REPORT_ID, ADMIN_ID, false);

        // Assert
        assertEquals(ReportResolutionResult.RESOLVED, result);
        assertEquals(ReportStatus.ACTIONED, resolveArgs[1]);
    }

    // ---- listPending() ----

    @Test
    public void testListPendingDelegatesToTheDao() {
        // Arrange
        when(mockEventReportDao.findPending()).thenReturn(java.util.Collections.singletonList(somePendingReport()));

        // Act
        final int size = moderationService.listPending().size();

        // Assert
        assertEquals(1, size);
    }
}
