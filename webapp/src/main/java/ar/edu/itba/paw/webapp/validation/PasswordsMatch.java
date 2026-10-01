package ar.edu.itba.paw.webapp.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Valida que dos campos de contraseña de un form coincidan. Va a nivel clase
// (no de campo: necesita comparar DOS campos entre si) con los nombres
// configurables, porque "password"/"confirmarPassword" no se llaman igual en
// todos los forms (ver ChangePasswordForm: "newPassword"). Reemplaza el
// if/rejectValue a mano que estaba repetido en cada controller de contraseña
// (RegisterController, LoginController, PasswordResetController,
// ProfileController): esa logica es del form, no del controller.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordsMatchValidator.class)
public @interface PasswordsMatch {
    String message() default "Las contraseñas no coinciden";
    String password() default "password";
    String confirmation() default "confirmarPassword";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
