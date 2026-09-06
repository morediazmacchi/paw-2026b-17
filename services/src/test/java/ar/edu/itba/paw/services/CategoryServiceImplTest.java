package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.persistence.CategoryDao;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CategoryServiceImplTest {

    @Mock
    private CategoryDao mockCategoryDao;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    public void testFindAllReturnsWhatTheDaoReturns() {
        final List<Category> categories = Arrays.asList(new Category("Musica"), new Category("Deportes"));
        when(mockCategoryDao.findAll()).thenReturn(categories);

        final List<Category> result = categoryService.findAll();

        assertEquals(2, result.size());
        assertEquals("Musica", result.get(0).getName());
    }

    @Test
    public void testFindAllReturnsAnEmptyListWhenThereAreNoCategories() {
        when(mockCategoryDao.findAll()).thenReturn(Collections.emptyList());

        final List<Category> result = categoryService.findAll();

        assertTrue(result.isEmpty());
    }
}
