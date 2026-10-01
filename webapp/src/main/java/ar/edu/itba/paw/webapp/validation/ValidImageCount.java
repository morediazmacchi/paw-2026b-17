package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Valida que no se suban mas de "max" imagenes. Las "vacias" (el picker manda
// un MultipartFile por cada slot del dropzone, tildado o no) no cuentan. Ver
// ImageCountValidator. El default esta atado al calculo de multipart-config en
// web.xml (ver el comentario de EventController.MAX_IMAGES).
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ImageCountValidator.class)
public @interface ValidImageCount {
    int max() default 5;
    String message() default "Podés subir hasta 5 fotos por evento.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
