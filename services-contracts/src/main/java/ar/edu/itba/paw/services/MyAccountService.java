// Arma las pantallas de "Mi cuenta" que juntan datos de varios services: un metodo por pantalla, y el controller
// solo pasa el resultado a la vista. Implementacion: MyAccountServiceImpl (modulo services). Va aparte (como
// EventDetailService) porque depende de muchos services y ninguno lo inyecta: asi no se arman ciclos.
package ar.edu.itba.paw.services;

import java.util.Optional;

public interface MyAccountService {

    /**
     * La bandeja: una pagina de avisos (con los eventos a los que se refieren) y una pagina de los grupos de
     * chat de la persona, cada una con su propio indice. Una pagina invalida se ajusta, igual que en cada lista.
     */
    MyInboxView findInbox(long userId, int page, int groupsPage);

    /**
     * "Mis eventos": una pagina de los eventos que creo, con sus conteos de inscripciones, y su reputacion como
     * organizador con una pagina de reseñas (cada una con su indice).
     */
    MyEventsView findMyEvents(long userId, int page, int reviewsPage);

    /**
     * Los pedidos pendientes de un evento (aceptar o rechazar): el evento y una pagina de pedidos, con los
     * lugares que quedan. Vacio si el evento no existe. Quien la puede ver (su organizador) lo decide
     * @PreAuthorize en el controller.
     */
    Optional<PendingRequestsView> findPendingRequests(long eventId, int page);

    /**
     * La pantalla de invitar: el evento y una pagina de las invitaciones ya enviadas. Vacio si el evento no
     * existe. Quien la puede ver (su organizador) lo decide @PreAuthorize en el controller.
     */
    Optional<InviteView> findInviteScreen(long eventId, int page);

    /** "Mis listas": una pagina de las busquedas guardadas de la persona, con los nombres de sus categorias. */
    PageResult<SavedSearchWithCategoryNames> findSavedSearches(long userId, int page);

    /**
     * Una busqueda guardada de esa persona con los nombres de sus categorias (la confirmacion de "desuscribirme"
     * del mail). Vacio si no existe o es de otra persona: las dos se tratan igual.
     */
    Optional<SavedSearchWithCategoryNames> findSavedSearch(long userId, long savedSearchId);
}
