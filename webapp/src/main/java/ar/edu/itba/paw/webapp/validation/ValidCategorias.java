package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Al menos una categoria tildada, y que las tildadas existan de verdad en la
// base (contra CategoryService.findAll(), el mismo que ya carga el dropdown).
// Ver CategoriasValidator.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CategoriasValidator.class)
public @interface ValidCategorias {
    String message() default "Elegí al menos una categoría";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
