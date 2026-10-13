// Implementa el contrato CategoryDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Category;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

@Repository
public class CategoryJpaDao implements CategoryDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Category> findAll() {
        return em.createQuery("FROM Category c ORDER BY c.name", Category.class).getResultList();
    }
}
