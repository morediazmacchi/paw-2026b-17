package ar.edu.itba.paw.webapp.validation;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

public class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Object> {

    private String passwordField;
    private String confirmationField;

    @Override
    public void initialize(final PasswordsMatch annotation) {
        this.passwordField = annotation.password();
        this.confirmationField = annotation.confirmation();
    }

    @Override
    public boolean isValid(final Object form, final ConstraintValidatorContext context) {
        if (form == null) {
            return true;
        }
        // BeanWrapper (no reflection a mano): lee el getter de cada campo por
        // nombre, asi un mismo validator sirve para forms que llaman distinto
        // a sus campos de contraseña (ver PasswordsMatch.password()/confirmation()).
        final BeanWrapper wrapper = new BeanWrapperImpl(form);
        final Object password = wrapper.getPropertyValue(passwordField);
        final Object confirmation = wrapper.getPropertyValue(confirmationField);
        if (Objects.equals(password, confirmation)) {
            return true;
        }
        // El error tiene que marcar confirmarPassword (no la clase entera): es
        // el mismo campo que antes marcaba el rejectValue() a mano, para que
        // el asterisco rojo en el JSP caiga en el input correcto.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmationField)
                .addConstraintViolation();
        return false;
    }
}
