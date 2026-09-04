// Implementa el contrato CategoryService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.persistence.CategoryDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryDao categoryDao;

    @Autowired
    public CategoryServiceImpl(final CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    @Override
    public List<Category> findAll() {
        return categoryDao.findAll();
    }
}
