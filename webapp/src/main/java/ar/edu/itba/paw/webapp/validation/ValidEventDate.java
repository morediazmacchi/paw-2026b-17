package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Valida que eventDate ("yyyy-MM-dd'T'HH:mm") sea una fecha real, futura y
// no este a mas de 2 anios de hoy. El @Pattern de EventForm.eventDate solo
// valida la forma (digitos en las posiciones correctas): sin este validator,
// "2026-13-45T99:99" pasa el @Pattern igual y SimpleDateFormat lo "desborda"
// en silencio a otra fecha. Ver EventDateValidator.
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventDateValidator.class)
public @interface ValidEventDate {
    String message() default "{ValidEventDate.format}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
