package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
import ar.edu.itba.paw.model.EventApprovalStatus;
import ar.edu.itba.paw.model.User;

// Los modelos ahora se relacionan entre si (una Registration tiene un Event y un User, no
// dos numeros sueltos). Como estos tests no tocan la base, alcanza con un Event / User
// "vacio" que solo conozca su id: es lo unico que los servicios les leen (getEventId(),
// getUserId(), getCreatorId() pasan por ese id).
final class ModelFixtures {

    private ModelFixtures() {
    }

    static Event event(final long id) {
        final Event event = new Event();
        event.setId(id);
        // Estos tests son sobre otros servicios (moderacion, inscripciones, chat,
        // etc.), no sobre la revision previa a publicarse (ver EventApprovalStatus
        // / EventAccessServiceImplTest, que es donde si importa el estado real):
        // se asume ya aprobado, como cualquier evento publicado antes de que
        // existiera esta feature.
        event.setApprovalStatus(EventApprovalStatus.APPROVED);
        return event;
    }

    static User user(final long id) {
        final User user = new User();
        user.setId(id);
        return user;
    }

    // Category no tiene setId (el id lo pone la base) ni constructor publico vacio. Para que un test
    // pueda decir "existe la categoria con tal id" se arma una Category que devuelve ese id desde su
    // getId() publico, sin tocar el campo privado (nada de reflection). No es un mock porque Mockito
    // no puede mockear clases concretas en las versiones nuevas de Java (ej. 26).
    static Category category(final long id) {
        return category(id, "categoria-" + id);
    }

    // Igual, pero con el nombre elegido (para tests que dependen de el, como la categoria "Otros").
    static Category category(final long id, final String name) {
        return new Category(name) {
            @Override
            public Long getId() {
                return id;
            }
        };
    }
}
