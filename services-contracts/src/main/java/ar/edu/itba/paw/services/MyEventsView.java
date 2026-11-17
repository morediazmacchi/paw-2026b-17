// "Mis eventos": los eventos creados (con sus conteos de inscripciones) y la reputacion como organizador, dos
// listas paginadas independientes. Lo devuelve MyAccountService.findMyEvents().
package ar.edu.itba.paw.services;

public class MyEventsView {

    private final PageResult<EventWithRegistrationStats> events;
    private final CreatorReputation reputation;

    public MyEventsView(final PageResult<EventWithRegistrationStats> events, final CreatorReputation reputation) {
        this.events = events;
        this.reputation = reputation;
    }

    public PageResult<EventWithRegistrationStats> getEvents() { return events; }

    // Promedio, total y una pagina de las reseñas que recibio como organizador.
    public CreatorReputation getReputation() { return reputation; }
}
