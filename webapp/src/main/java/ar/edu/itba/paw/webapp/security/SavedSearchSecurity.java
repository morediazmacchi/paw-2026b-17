package ar.edu.itba.paw.webapp.security;

import ar.edu.itba.paw.services.SavedSearchService;
import ar.edu.itba.paw.webapp.auth.PlancitoUserDetails;
import org.springframework.security.core.Authentication;

// Reglas de ownership de las busquedas guardadas ("Mis listas"), para usar desde @PreAuthorize en ProfileController:
// @PreAuthorize("@savedSearchSecurity.isOwner(#id, authentication)"). Solo lee del service (una sola columna: el
// id de la duenia), nunca del DAO.
public class SavedSearchSecurity {

    private final SavedSearchService savedSearchService;

    public SavedSearchSecurity(final SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    // La persona logueada es la duenia de la busqueda. Una busqueda que no existe da false, igual que una ajena:
    // ErrorAdvice las convierte en el mismo 404 (no se revela cuales ids existen).
    public boolean isOwner(final long savedSearchId, final Authentication authentication) {
        return PlancitoUserDetails.from(authentication)
                .flatMap(user -> savedSearchService.findOwnerId(savedSearchId)
                        .map(ownerId -> ownerId == user.getId()))
                .orElse(false);
    }
}
