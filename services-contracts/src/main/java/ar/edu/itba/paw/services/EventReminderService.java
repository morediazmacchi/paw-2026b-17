// Contrato del recordatorio "falta menos de 24 hs". Implementacion: EventReminderServiceImpl (modulo services).
package ar.edu.itba.paw.services;

import java.util.List;

public interface EventReminderService {

    /** Los eventos que empiezan en menos de 24 hs y todavia no tuvieron su recordatorio. */
    List<Long> findEventsNeedingReminder();

    /**
     * Manda el recordatorio de ese evento: mail + aviso en la bandeja a cada
     * inscripto confirmado y a quien lo organiza. Nunca lo manda dos veces para
     * el mismo evento.
     *
     * @return true si lo mando esta llamada; false si ya estaba mandado o si el
     *         evento ya no corresponde (no existe, se cancelo, ya empezo).
     */
    boolean sendReminder(long eventId);
}
