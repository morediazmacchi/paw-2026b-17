package ar.edu.itba.paw.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Manda solo el recordatorio "falta menos de 24 hs": nadie aprieta nada, es un
 * hecho del reloj, no una reaccion a una accion de un usuario.
 *
 * En su propia clase para que cada sendReminder() sea una llamada a otro bean:
 * asi pasa por el proxy de Spring y abre su propia transaccion. Un evento que
 * falla no arrastra a los demas.
 */
@Component
public class EventReminderScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventReminderScheduler.class);

    // Cada 15 minutos: el recordatorio sale entre 24 hs y 23:45 hs antes. Mas
    // seguido no le cambia nada a nadie.
    private static final long CADA_QUINCE_MINUTOS = 15 * 60 * 1000L;

    // Un minuto de gracia al arrancar: le da tiempo al contexto de Spring de
    // terminar de levantar antes de la primera corrida.
    private static final long AL_ARRANCAR = 60 * 1000L;

    private final EventReminderService eventReminderService;

    @Autowired
    public EventReminderScheduler(final EventReminderService eventReminderService) {
        this.eventReminderService = eventReminderService;
    }

    @Scheduled(fixedDelay = CADA_QUINCE_MINUTOS, initialDelay = AL_ARRANCAR)
    public int sendDueReminders() {
        int sent = 0;
        for (final Long eventId : eventReminderService.findEventsNeedingReminder()) {
            try {
                if (eventReminderService.sendReminder(eventId)) {
                    sent++;
                }
            } catch (final RuntimeException e) {
                // Adentro del loop: un evento roto no frena a los demas. Y como es
                // un hilo propio sin nadie esperando, si no se loguea aca no se ve.
                LOGGER.error("Fallo el recordatorio de 24 hs del evento {}", eventId, e);
            }
        }
        return sent;
    }
}
