package ar.edu.itba.paw.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avisa de los eventos nuevos de las busquedas guardadas. Corre solo, en su propio hilo y fuera de cualquier request:
 * aprobar un evento no tiene que esperar a que se recorran todas las busquedas guardadas.
 *
 * Recorre las busquedas de a tandas (TANDA ids por consulta), nunca todas juntas en memoria. Cada notifyNewEvents()
 * es una llamada a otro bean: pasa por el proxy de Spring y abre su propia transaccion, asi una busqueda que falla no
 * arrastra a las demas (mismo criterio que EventReminderScheduler).
 */
@Component
public class SavedSearchScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SavedSearchScheduler.class);

    // Cada 15 minutos: un evento nuevo llega a quien lo busca, como mucho, un cuarto de hora despues de aprobado.
    private static final long CADA_QUINCE_MINUTOS = 15 * 60 * 1000L;

    // Dos minutos de gracia al arrancar (uno mas que el recordatorio, para que no arranquen juntos).
    private static final long AL_ARRANCAR = 2 * 60 * 1000L;

    // Cuantas busquedas guardadas se traen por consulta.
    static final int TANDA = 100;

    private final SavedSearchService savedSearchService;

    @Autowired
    public SavedSearchScheduler(final SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    @Scheduled(fixedDelay = CADA_QUINCE_MINUTOS, initialDelay = AL_ARRANCAR)
    public int notifyNewEvents() {
        // Sin eventos aprobados desde la marca mas vieja no hay nada que avisar: se evita mirar cada busqueda (3
        // consultas por busqueda) cuando en este rato no se aprobo nada. Las marcas no se corren: cada busqueda sigue
        // contando desde la suya, asi que un evento aprobado despues entra igual en la proxima ventana.
        if (!savedSearchService.mayHaveNewEvents()) {
            return 0;
        }
        int notified = 0;
        long lastId = 0;
        List<Long> batch;
        do {
            batch = savedSearchService.findIdsAfter(lastId, TANDA);
            for (final Long id : batch) {
                try {
                    if (savedSearchService.notifyNewEvents(id)) {
                        notified++;
                    }
                } catch (final RuntimeException e) {
                    // Adentro del loop: una busqueda rota no frena a las demas. Es un hilo sin nadie esperando, si no
                    // se loguea aca no se ve.
                    LOGGER.error("Fallo el aviso de eventos nuevos de la busqueda guardada {}", id, e);
                }
                lastId = id;
            }
        } while (batch.size() == TANDA);
        return notified;
    }
}
