package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Valida que los rangos del filtro de /eventos no esten al reves: "precio hasta" no puede ser menor que "precio desde",
// ni "fecha hasta" anterior a "fecha desde" (ver SearchRangesValidator). Va a nivel clase porque compara DOS campos
// entre si, y marca el campo "hasta" (precioMax / fechaHasta), que es donde la pantalla muestra el error.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SearchRangesValidator.class)
public @interface ValidSearchRanges {
    String message() default "{event.list.priceRangeInvalid}";
    String priceMessage() default "{event.list.priceRangeInvalid}";
    String dateMessage() default "{event.list.dateRangeInvalid}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
