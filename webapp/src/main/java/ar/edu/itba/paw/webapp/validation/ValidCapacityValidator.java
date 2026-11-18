package ar.edu.itba.paw.webapp.validation;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class ValidCapacityValidator implements ConstraintValidator<ValidCapacity, Object> {

    private String capacityField;
    private String unlimitedField;

    @Override
    public void initialize(final ValidCapacity annotation) {
        this.capacityField = annotation.capacity();
        this.unlimitedField = annotation.unlimited();
    }

    @Override
    public boolean isValid(final Object form, final ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }
        // BeanWrapper (no reflection a mano): lee los getters por nombre, mismo criterio que
        // PasswordsMatchValidator.
        final BeanWrapper wrapper = new BeanWrapperImpl(form);
        final Object capacity = wrapper.getPropertyValue(capacityField);
        // Si todavia no es "0" (vacio, otro numero, o no matchea el @Pattern del campo), nada que
        // decidir aca: que lo diga el @NotBlank/@Pattern/@Digits del campo mismo.
        if (!"0".equals(capacity)) {
            return true;
        }
        final Object unlimited = wrapper.getPropertyValue(unlimitedField);
        if ("true".equals(unlimited)) {
            // Vino del atajo "Sin limite" (quick-options.js prendio el campo oculto): valido.
            return true;
        }
        // "0" escrito a mano, sin el atajo: invalido. El error marca el campo del cupo (no la
        // clase entera), igual que PasswordsMatchValidator marca confirmarPassword.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(capacityField)
                .addConstraintViolation();
        return false;
    }
}
