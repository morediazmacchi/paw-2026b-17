// Contrato de persistencia para Category. Implementacion: CategoryJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Category;

import java.util.List;

public interface CategoryDao {
    List<Category> findAll();
}
