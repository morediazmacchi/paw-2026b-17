package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.EventCategory;
import ar.edu.itba.paw.model.EventVisibility;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

// Armador de eventos para los tests de similitud: solo con lo que S(a, b) lee (categorias,
// precio, barrio, fecha, organizador) mas lo que decide si un evento se sugiere (visibilidad).
// La fecha se escribe en hora de Buenos Aires, que es la que usa EventSimilarity.
final class SimilarityFixtures {

    static final Category MUSICA = ModelFixtures.category(1, "Musica");
    static final Category DEPORTES = ModelFixtures.category(2, "Deportes");
    static final Category ARTE = ModelFixtures.category(3, "Arte");
    static final Category OTROS = ModelFixtures.category(6, "Otros");

    private SimilarityFixtures() {
    }

    static Builder event(final long id) {
        return new Builder(id);
    }

    // Una fecha en hora de Buenos Aires, p. ej. at("2026-10-23T21:00").
    static Date at(final String localDateTime) {
        return Date.from(LocalDateTime.parse(localDateTime).atZone(EventSimilarity.ZONE).toInstant());
    }

    static final class Builder {
        private final Event event = new Event();

        private Builder(final long id) {
            event.setId(id);
            event.setApprovalStatus(EventApprovalStatus.APPROVED);
            event.setPrice(BigDecimal.ZERO);
            event.setEventDate(SimilarityFixtures.at("2026-10-23T21:00"));
        }

        Builder categories(final Category... categories) {
            for (final Category category : categories) {
                event.addCategory(new EventCategory(event, category, null));
            }
            return this;
        }

        Builder price(final String price) {
            event.setPrice(new BigDecimal(price));
            return this;
        }

        Builder barrio(final Barrio barrio) {
            event.setNeighborhood(barrio);
            return this;
        }

        Builder at(final String localDateTime) {
            event.setEventDate(SimilarityFixtures.at(localDateTime));
            return this;
        }

        Builder creator(final long creatorId) {
            event.setCreator(ModelFixtures.user(creatorId));
            return this;
        }

        Builder privateEvent() {
            event.setVisibility(EventVisibility.PRIVATE);
            return this;
        }

        Builder approval(final EventApprovalStatus status) {
            event.setApprovalStatus(status);
            return this;
        }

        Event build() {
            return event;
        }
    }
}
