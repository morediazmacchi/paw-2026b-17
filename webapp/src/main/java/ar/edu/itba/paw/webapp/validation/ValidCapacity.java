package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Un cupo maximo de "0" solo es valido si vino del atajo "Sin limite" (ver quick-options.js), nunca
// si la persona lo escribio a mano: la cantidad de gente tiene que ser mayor a 0. El atajo escribe "0"
// en el campo visible Y prende un campo oculto aparte (mismo criterio que locationManual para el pin
// del mapa); sin ese campo prendido, un "0" es un dato invalido, no "sin limite". Va a nivel clase
// (no de campo: necesita comparar el cupo con el campo del atajo) con los nombres configurables,
// igual que PasswordsMatch.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCapacityValidator.class)
public @interface ValidCapacity {
    String message() default "{ValidCapacity.message}";
    String capacity() default "maxCapacity";
    String unlimited() default "maxCapacityUnlimited";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
