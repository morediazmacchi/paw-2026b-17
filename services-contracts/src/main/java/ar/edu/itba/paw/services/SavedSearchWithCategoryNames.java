// Una busqueda guardada con los nombres de sus categorias ya resueltos (la busqueda solo guarda los ids). Lo
// devuelve MyAccountService.findSavedSearches() / findSavedSearch().
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.SavedSearch;

import java.util.Collections;
import java.util.List;

public class SavedSearchWithCategoryNames {

    private final SavedSearch search;
    private final List<String> categoryNames;

    public SavedSearchWithCategoryNames(final SavedSearch search, final List<String> categoryNames) {
        this.search = search;
        this.categoryNames = Collections.unmodifiableList(categoryNames);
    }

    public SavedSearch getSearch() { return search; }

    // En el orden de sus ids; una categoria que ya no existe no aparece.
    public List<String> getCategoryNames() { return categoryNames; }
}
