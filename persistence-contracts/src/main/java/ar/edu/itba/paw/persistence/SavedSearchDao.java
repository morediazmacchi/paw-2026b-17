// Contrato de persistencia para las busquedas guardadas. Implementacion: SavedSearchJpaDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.SavedSearch;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface SavedSearchDao {

    // Recibe los filtros y es el DAO quien arma la busqueda guardada y la devuelve con su id (correccion 11).
    SavedSearch create(long userId, Collection<Long> categoryIds, Collection<Barrio> barrios,
                       BigDecimal minPrice, BigDecimal maxPrice, Date fromDate, Date toDate);

    // Si esa persona ya guardo una busqueda con esos filtros (ver SavedSearch.keyOf()).
    boolean existsByKey(long userId, String filtersKey);

    // Cuantas busquedas guardadas tiene (un COUNT, sin traer las filas): para armar las paginas de "Mis listas".
    long countByUserId(long userId);

    // Una pagina (page desde 1) de las busquedas guardadas de esa persona, las mas nuevas primero, con sus filtros
    // cargados. Dos consultas: la primera trae solo los ids de la pagina y la segunda carga esas busquedas.
    List<SavedSearch> findByUserId(long userId, int page, int pageSize);

    // Borra la busqueda (junto con sus filtros). true si la borro; false si no existe. De quien es NO se mira aca:
    // lo decide @PreAuthorize en el controller (ver SavedSearchSecurity).
    boolean deleteById(long id);

    // El id de la persona duenia de la busqueda (una sola columna, sin cargar la busqueda ni sus filtros), o vacio si
    // la busqueda no existe. Lo usa la autorizacion (ver SavedSearchSecurity).
    Optional<Long> findUserIdById(long id);

    // Los ids mayores a afterId, de menor a mayor, a lo sumo limit: para recorrer todas las busquedas guardadas de a
    // tandas sin traerlas juntas a memoria.
    List<Long> findIdsAfter(long afterId, int limit);

    // La busqueda con su duenio y sus filtros en UNA consulta (JOIN FETCH): para avisar hacen falta los tres, y
    // como son relaciones lazy, sin esto serian cuatro consultas por busqueda.
    Optional<SavedSearch> findByIdWithFilters(long id);

    // Anota hasta cuando ya se le aviso. true si se actualizo; false si la busqueda no existe.
    boolean markChecked(long id, Date checkedAt);
}
