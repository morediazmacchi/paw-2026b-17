// Implementa el contrato EventReminderService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.InboxMessageType;
import ar.edu.itba.paw.model.RegistrationStatus;
import ar.edu.itba.paw.persistence.EventDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class EventReminderServiceImpl implements EventReminderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventReminderServiceImpl.class);

    static final long ONE_DAY_MILLIS = 24L * 60 * 60 * 1000;

    private final EventDao eventDao;
    private final RegistrationService registrationService;
    private final NotificationService notificationService;
    private final InboxService inboxService;

    @Autowired
    public EventReminderServiceImpl(final EventDao eventDao, final RegistrationService registrationService,
                                    final NotificationService notificationService,
                                    final InboxService inboxService) {
        this.eventDao = eventDao;
        this.registrationService = registrationService;
        this.notificationService = notificationService;
        this.inboxService = inboxService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findEventsNeedingReminder() {
        final Date now = new Date();
        return eventDao.findIdsNeedingReminder(now, new Date(now.getTime() + ONE_DAY_MILLIS));
    }

    @Override
    @Transactional
    public boolean sendReminder(final long eventId) {
        final Date now = new Date();
        // Primero se "reclama" el recordatorio. Si otra corrida ya lo mando (o lo
        // esta mandando), esto devuelve false y no se manda nada: nunca dos veces.
        // Va en la misma transaccion que los avisos: si algo falla, se deshace la
        // marca tambien y la proxima corrida lo vuelve a intentar.
        if (!eventDao.markReminderSent(eventId, now)) {
            return false;
        }
        final Event event = eventDao.findById(eventId).orElse(null);
        // isApproved(): mismo motivo que isCancelled() -- aunque findIdsNeedingReminder()
        // ya filtra por aprobado, esto cubre la ventana entre esa consulta y este punto
        // (si justo en el medio un admin lo rechaza, no se manda nada).
        if (event == null || event.isCancelled() || !event.isApproved() || !event.getEventDate().after(now)) {
            return false;
        }

        // Solo a los confirmados: son los que tienen un lugar. Lista de espera y
        // pendientes todavia no tienen a que ir.
        // Mails todos en UNA tarea (en otro hilo, despues del commit); la bandeja, todos juntos en una sola sentencia.
        notificationService.notifyEventReminder(event, registrationService.findConfirmedByEventId(eventId));
        inboxService.addForRegistrants(eventId, InboxMessageType.EVENT_REMINDER, RegistrationStatus.withConfirmedPlace());
        notificationService.notifyOrganizerEventReminder(event);
        inboxService.add(event.getCreatorEmail(), InboxMessageType.EVENT_TOMORROW, eventId, null, null);

        LOGGER.info("Recordatorio de 24 hs mandado para el evento {}", eventId);
        return true;
    }
}
