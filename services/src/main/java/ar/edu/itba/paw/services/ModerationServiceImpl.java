// Implementa el contrato ModerationService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.persistence.EventReportDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ModerationServiceImpl implements ModerationService {

    private final EventReportDao eventReportDao;
    private final EventService eventService;
    private final UserService userService;

    @Autowired
    public ModerationServiceImpl(final EventReportDao eventReportDao, final EventService eventService,
                                  final UserService userService) {
        this.eventReportDao = eventReportDao;
        this.eventService = eventService;
        this.userService = userService;
    }

    @Override
    @Transactional
    public ReportResult report(final long eventId, final User reporter, final String reason) {
        // Quien llama (EventController) ya resolvio el evento antes de pedir el
        // reporte: si no existiera no habria llegado hasta aca. Mismo criterio
        // que RegistrationServiceImpl para este caso "no deberia pasar".
        final Event event = eventService.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Evento inexistente: " + eventId));
        if (Long.valueOf(reporter.getId()).equals(event.getCreatorId())) {
            return ReportResult.OWN_EVENT;
        }
        if (eventReportDao.existsPendingByEventAndReporter(eventId, reporter.getId())) {
            return ReportResult.ALREADY_REPORTED;
        }
        eventReportDao.create(new EventReport(eventId, reporter.getId(), reason));
        return ReportResult.CREATED;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventReport> listPending() {
        return eventReportDao.findPending();
    }

    @Override
    @Transactional
    public ReportResolutionResult dismiss(final long reportId, final long adminUserId) {
        final Optional<EventReport> report = eventReportDao.findById(reportId).filter(EventReport::isPending);
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
        final Optional<EventReport> report = eventReportDao.findById(reportId).filter(EventReport::isPending);
        if (!report.isPresent()) {
            return ReportResolutionResult.NOT_FOUND;
        }
        final EventReport eventReport = report.get();
        final Optional<Event> event = eventService.findById(eventReport.getEventId());
        if (!event.isPresent()) {
            // El evento se borro de otra forma entre medio (no deberia pasar,
            // son soft-delete): igual resolvemos el reporte, no queda nada mas
            // que hacer con el.
            eventReportDao.resolve(reportId, ReportStatus.ACTIONED, adminUserId);
            return ReportResolutionResult.RESOLVED;
        }
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
