package ar.edu.itba.paw.webapp.validation;

import ar.edu.itba.paw.model.Category;
import ar.edu.itba.paw.services.CategoryService;
import ar.edu.itba.paw.webapp.form.CategoriaSeleccionForm;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// @Autowired funciona aca porque LocalValidatorFactoryBean, al ser un bean de
// Spring (lo arma @EnableWebMvc), usa SpringConstraintValidatorFactory para
// instanciar los ConstraintValidator: no es un new a mano, Spring lo arma
// como a cualquier otro bean.
public class CategoriasValidator implements ConstraintValidator<ValidCategorias, List<CategoriaSeleccionForm>> {

    @Autowired
    private CategoryService categoryService;

    @Override
    public boolean isValid(final List<CategoriaSeleccionForm> value, final ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        final List<CategoriaSeleccionForm> selected = value.stream()
                .filter(CategoriaSeleccionForm::isSeleccionada)
                .collect(Collectors.toList());
        if (selected.isEmpty()) {
            return fail(context, "{ValidCategorias.none}");
        }
        final Set<Long> existingIds = categoryService.findAll().stream()
                .map(Category::getId)
                .collect(Collectors.toSet());
        final boolean allExist = selected.stream().allMatch(c -> existingIds.contains(c.getCategoryId()));
        if (!allExist) {
            return fail(context, "{ValidCategorias.invalid}");
        }
        return true;
    }

    private static boolean fail(final ConstraintValidatorContext context, final String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
