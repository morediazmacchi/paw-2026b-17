// Implementa el contrato ModerationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.ReportTarget;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.exception.EventNotFoundException;
import ar.edu.itba.paw.persistence.EventReportDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ModerationServiceImpl implements ModerationService {

    static final int PAGE_SIZE = 10;

    private final EventReportDao eventReportDao;
    private final EventService eventService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final InboxService inboxService;

    @Autowired
    public ModerationServiceImpl(final EventReportDao eventReportDao, final EventService eventService,
                                  final UserService userService, final NotificationService notificationService,
                                  final InboxService inboxService) {
        this.eventReportDao = eventReportDao;
        this.eventService = eventService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.inboxService = inboxService;
    }

    @Override
    @Transactional
    public ReportResult report(final long eventId, final User reporter, final String reason) {
        // Evento inexistente: el 404 ya lo da @PreAuthorize en el controller antes de llegar aca; esto solo
        // se alcanza si desaparece justo en el medio (carrera). Es la excepcion de siempre (ErrorAdvice
        // la mapea a 404), no un resultado: los resultados son para conflictos de negocio.
        final Event event = eventService.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (Long.valueOf(reporter.getId()).equals(event.getCreatorId())) {
            return ReportResult.OWN_EVENT;
        }
        if (eventReportDao.existsPendingByEventAndReporter(eventId, reporter.getId())) {
            return ReportResult.ALREADY_REPORTED;
        }
        eventReportDao.create(new EventReport(event, reporter, reason));
        return ReportResult.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EventReport> findPendingPage(final int page) {
        final long total = eventReportDao.countPending();
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(eventReportDao.findPending(effectivePage, PAGE_SIZE), total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<EventReport> findChangesRequestedPage(final int page) {
        final long total = eventReportDao.countChangesRequested();
        final int effectivePage = PageResult.clampPage(page, total, PAGE_SIZE);
        return new PageResult<>(eventReportDao.findChangesRequested(effectivePage, PAGE_SIZE),
                total, effectivePage, PAGE_SIZE);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReportTarget> findReportTarget(final long reportId) {
        return eventReportDao.findTargetById(reportId);
    }

    @Override
    @Transactional
    public ReportResolutionResult requestChanges(final long reportId, final long adminUserId, final String message) {
        // Solo desde PENDING (no isOpen): no tiene sentido "pedir cambios" de
        // nuevo sobre un reporte que ya esta esperando una revision, primero
        // hay que resolver ese pedido (dismiss/removeEvent).
        final Optional<EventReport> report = eventReportDao.findById(reportId).filter(EventReport::isPending);
        if (!report.isPresent()) {
            return ReportResolutionResult.NOT_FOUND;
        }
        final Optional<Event> event = eventService.findById(report.get().getEventId());
        if (!event.isPresent()) {
            // A diferencia de report(), aca NO se lanza EventNotFoundException: esto no es una accion de
            // una persona sobre un evento que tiene que ver (no hay 404 que mostrar), es un admin
            // resolviendo la cola de moderacion. Si el evento ya no esta (son soft-delete, no deberia
            // pasar), no hay a quien avisarle ni que corregir: igual se resuelve el reporte para que
            // salga de la cola.
            eventReportDao.resolve(reportId, ReportStatus.ACTIONED, adminUserId);
            return ReportResolutionResult.RESOLVED;
        }
        eventReportDao.requestChanges(reportId, message, adminUserId);
        notificationService.notifyEventChangesRequested(event.get(), message);
        inboxService.add(event.get().getCreatorEmail(), InboxMessageType.EVENT_CHANGES_REQUESTED,
                event.get().getId(), null, message);
        return ReportResolutionResult.RESOLVED;
    }

    @Override
    @Transactional
    public ReportResolutionResult dismiss(final long reportId, final long adminUserId) {
        final Optional<EventReport> report = eventReportDao.findById(reportId).filter(EventReport::isOpen);
        if (!report.isPresent()) {
            return ReportResolutionResult.NOT_FOUND;
        }
        eventReportDao.resolve(reportId, ReportStatus.DISMISSED, adminUserId);
        return ReportResolutionResult.RESOLVED;
    }

    @Override
    @Transactional
    public ReportResolutionResult removeEvent(final long reportId, final long adminUserId,
                                               final boolean suspendOrganizer) {
        final Optional<EventReport> report = eventReportDao.findById(reportId).filter(EventReport::isOpen);
        if (!report.isPresent()) {
            return ReportResolutionResult.NOT_FOUND;
        }
        final EventReport eventReport = report.get();
        final Optional<Event> event = eventService.findById(eventReport.getEventId());
        if (!event.isPresent()) {
            // A diferencia de report(), aca NO se lanza EventNotFoundException: esto no es una accion de
            // una persona sobre un evento que tiene que ver (no hay 404 que mostrar), es un admin
            // resolviendo un reporte. Si el evento ya no esta (son soft-delete, no deberia pasar), no
            // queda nada que remover: igual se resuelve el reporte para que salga de la cola.
            eventReportDao.resolve(reportId, ReportStatus.ACTIONED, adminUserId);
            return ReportResolutionResult.RESOLVED;
        }
        // (Que un admin no resuelva un reporte sobre SU propio evento, ni se suspenda a si mismo via
        // suspendOrganizer, lo decide @PreAuthorize en AdminController: ver AdminSecurity.canResolveReport.)
        // Ya cancelado (el organizador lo dio de baja el mismo mientras se
        // revisaba, o un admin ya actuo sobre otro reporte del mismo evento):
        // no hay nada que remover, pero el reporte igual queda resuelto.
        if (!event.get().isCancelled()) {
            eventService.removeByAdmin(eventReport.getEventId(), eventReport.getReason());
        }
        eventReportDao.resolve(reportId, ReportStatus.ACTIONED, adminUserId);
        // Cualquier otro reporte pendiente sobre este mismo evento ya no tiene
        // sentido seguir mostrandolo en la cola: el evento ya se removio.
        eventReportDao.resolveAllPendingForEvent(eventReport.getEventId(), ReportStatus.ACTIONED, adminUserId);
        if (suspendOrganizer && event.get().getCreatorId() != null) {
            userService.suspend(event.get().getCreatorId());
        }
        return ReportResolutionResult.RESOLVED;
    }
}
