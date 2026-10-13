package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.Event;
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
        return event;
    }

    static User user(final long id) {
        final User user = new User();
        user.setId(id);
        return user;
    }

    // Category no tiene setId (el id lo pone la base) ni constructor publico vacio. Para que un test
    // pueda decir "existe la categoria con tal id" se arma una Category real y se le escribe el id
    // por reflection. Se hace asi (y no con un mock) porque Mockito no puede mockear clases
    // concretas en las versiones nuevas de Java (ej. 26).
    static Category category(final long id) {
        try {
            final Category category = new Category("categoria-" + id);
            final java.lang.reflect.Field idField = Category.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(category, id);
            return category;
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo armar la categoria de prueba", e);
        }
    }
}
