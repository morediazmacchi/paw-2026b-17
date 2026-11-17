// Implementa el contrato SavedSearchDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import java.util.stream.Collectors;
import ar.edu.itba.paw.model.Barrio;
import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.model.SavedSearch;
import ar.edu.itba.paw.model.User;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public class SavedSearchJpaDao implements SavedSearchDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public SavedSearch create(final long userId, final Collection<Long> categoryIds,
                              final Collection<Barrio> barrios, final BigDecimal minPrice,
                              final BigDecimal maxPrice, final Date fromDate, final Date toDate) {
        // De la duenia solo hace falta el id (la FK): getReference no consulta ni carga al usuario.
        final User user = em.getReference(User.class, userId);
        // De cada categoria alcanza con una referencia (con su id): una que no existe viola la clave foranea de
        // saved_search_category al guardar.
        final List<Category> categories = categoryIds == null ? Collections.<Category>emptyList()
                : categoryIds.stream().map(id -> em.getReference(Category.class, id)).collect(Collectors.toList());
        final SavedSearch search = new SavedSearch(user, categories, barrios, minPrice, maxPrice, fromDate, toDate);
        em.persist(search);
        // flush: que los INSERT (la busqueda y sus filtros) salgan ahora, asi un UNIQUE violado (otro pedido casi
        // simultaneo con los mismos filtros se nos adelanto) sale de este metodo y no de un flush posterior.
        em.flush();
        return search;
    }

    @Override
    public boolean existsByKey(final long userId, final String filtersKey) {
        return em.createQuery(
                        "SELECT COUNT(s) FROM SavedSearch s WHERE s.user.id = :userId AND s.filtersKey = :filtersKey",
                        Long.class)
                .setParameter("userId", userId)
                .setParameter("filtersKey", filtersKey)
                .getSingleResult() > 0;
    }

    @Override
    public long countByUserId(final long userId) {
        return em.createQuery("SELECT COUNT(s) FROM SavedSearch s WHERE s.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    @Override
    public List<SavedSearch> findByUserId(final long userId, final int page, final int pageSize) {
        // Dos pasos (1+1): primero los ids de la pagina (el id desempata), despues las busquedas con sus filtros.
        final List<Long> ids = em.createQuery(
                        "SELECT s.id FROM SavedSearch s WHERE s.user.id = :userId ORDER BY s.createdAt DESC, s.id DESC",
                        Long.class)
                .setParameter("userId", userId)
                .setFirstResult((page - 1) * pageSize)
                .setMaxResults(pageSize)
                .getResultList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        // DISTINCT: el JOIN con las dos colecciones repite la fila de cada busqueda. El ORDER BY es el MISMO de la
        // consulta de ids (el IN no ordena nada).
        return em.createQuery(
                        "SELECT DISTINCT s FROM SavedSearch s LEFT JOIN FETCH s.categories "
                                + "LEFT JOIN FETCH s.barrios WHERE s.id IN :ids "
                                + "ORDER BY s.createdAt DESC, s.id DESC", SavedSearch.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public boolean deleteById(final long id) {
        final SavedSearch found = em.find(SavedSearch.class, id);
        if (found == null) {
            return false;
        }
        em.remove(found);
        return true;
    }

    @Override
    public Optional<Long> findUserIdById(final long id) {
        // s.user.id no hace JOIN con users: es la propia columna user_id de la busqueda.
        return em.createQuery("SELECT s.user.id FROM SavedSearch s WHERE s.id = :id", Long.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst();
    }

    @Override
    public List<Long> findIdsAfter(final long afterId, final int limit) {
        return em.createQuery("SELECT s.id FROM SavedSearch s WHERE s.id > :afterId ORDER BY s.id ASC", Long.class)
                .setParameter("afterId", afterId)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Optional<Date> findOldestLastCheckedAt() {
        // MIN sobre una tabla vacia devuelve NULL (una fila con null): Optional.ofNullable lo vuelve "vacio".
        return Optional.ofNullable(em.createQuery("SELECT MIN(s.lastCheckedAt) FROM SavedSearch s", Date.class)
                .getSingleResult());
    }

    @Override
    public Optional<SavedSearch> findByIdWithFilters(final long id) {
        // DISTINCT: el JOIN con las dos colecciones repite la fila; asi vuelve una sola busqueda.
        return em.createQuery("SELECT DISTINCT s FROM SavedSearch s JOIN FETCH s.user "
                        + "LEFT JOIN FETCH s.categories LEFT JOIN FETCH s.barrios WHERE s.id = :id",
                        SavedSearch.class)
                .setParameter("id", id)
                .getResultList().stream().findFirst();
    }

    @Override
    public boolean markChecked(final long id, final Date checkedAt) {
        final SavedSearch search = em.find(SavedSearch.class, id);
        if (search == null) {
            return false;
        }
        search.setLastCheckedAt(checkedAt);
        return true;
    }
}
