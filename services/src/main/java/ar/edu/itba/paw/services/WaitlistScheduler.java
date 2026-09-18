package ar.edu.itba.paw.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * La unica parte de la lista de espera que no la dispara una persona.
 *
 * Todo lo demas ocurre porque alguien aprieta algo: cancelar, aceptar, rechazar.
 * Que una invitacion se venza, en cambio, es un hecho del reloj: si nadie mas
 * toca ese evento, nadie se entera, y ese cupo queda reservado para alguien que
 * ya no va a contestar. Esta tarea es la que lo destraba.
 *
 * Esta en su propia clase, y no como un metodo mas de WaitlistServiceImpl, por
 * un motivo concreto: si el loop viviera adentro del service, la llamada a
 * reconcile() seria una llamada interna y NO pasaria por el proxy de Spring, asi
 * que se quedaria sin @Transactional. Desde aca, cada reconcile() es una llamada
 * normal a otro bean: abre su propia transaccion, y un evento que falla no
 * arrastra a los demas.
 */
@Component
public class WaitlistScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitlistScheduler.class);

    // Cada 5 minutos alcanza: la ventana para contestar es de 24hs, un rato de
    // demora no le cambia nada a nadie, y no le pegamos a la base al pedo.
    private static final long CADA_CINCO_MINUTOS = 5 * 60 * 1000L;

    // Un minuto de gracia al arrancar, para no competir con el resto del deploy.
    private static final long AL_ARRANCAR = 60 * 1000L;

    private final WaitlistService waitlistService;

    @Autowired
    public WaitlistScheduler(final WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @Scheduled(fixedDelay = CADA_CINCO_MINUTOS, initialDelay = AL_ARRANCAR)
    public void reconcileAll() {
        final List<Long> eventIds = waitlistService.findEventsNeedingReconcile();
        if (eventIds.isEmpty()) {
            return;
        }
        LOGGER.debug("Revisando la lista de espera de {} eventos", eventIds.size());

        for (final Long eventId : eventIds) {
            try {
                waitlistService.reconcile(eventId);
            } catch (final RuntimeException e) {
                // Adrede adentro del loop: un evento roto no puede frenar la cola.
                // Y estamos en un hilo propio, sin nadie esperando: si no lo
                // atrapamos y lo logueamos aca, el error no aparece en ningun lado.
                LOGGER.error("Fallo el reconcile automatico del evento {}", eventId, e);
            }
        }
    }
}
