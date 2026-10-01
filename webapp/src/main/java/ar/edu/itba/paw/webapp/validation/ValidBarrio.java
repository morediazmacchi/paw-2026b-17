package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Valida que barrio sea uno de los valores de Barrio. Ver BarrioValidator.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BarrioValidator.class)
public @interface ValidBarrio {
    String message() default "Elegí un barrio de la lista";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
