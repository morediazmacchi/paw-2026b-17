package ar.edu.itba.paw.webapp.validation;

import ar.edu.itba.paw.model.Barrio;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class BarrioValidator implements ConstraintValidator<ValidBarrio, String> {

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // @NotBlank ya se encarga de esto
        }
        try {
            Barrio.valueOf(value);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }
}
