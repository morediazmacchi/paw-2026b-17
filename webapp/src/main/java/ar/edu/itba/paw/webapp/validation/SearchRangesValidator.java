package ar.edu.itba.paw.webapp.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class SearchRangesValidator implements ConstraintValidator<ValidSearchRanges, FilterRanges> {

    private String priceMessage;
    private String dateMessage;

    @Override
    public void initialize(final ValidSearchRanges annotation) {
        this.priceMessage = annotation.priceMessage();
        this.dateMessage = annotation.dateMessage();
    }

    @Override
    public boolean isValid(final FilterRanges filters, final ConstraintValidatorContext context) {
        if (filters == null) {
            return true;
        }
        final boolean priceInvalid = filters.isPriceRangeInvalid();
        final boolean dateInvalid = filters.isDateRangeInvalid();
        if (!priceInvalid && !dateInvalid) {
            return true;
        }
        // Un error por cada rango al reves, en el campo "hasta" de cada uno (no en la clase entera).
        context.disableDefaultConstraintViolation();
        if (priceInvalid) {
            context.buildConstraintViolationWithTemplate(priceMessage)
                    .addPropertyNode("precioMax").addConstraintViolation();
        }
        if (dateInvalid) {
            context.buildConstraintViolationWithTemplate(dateMessage)
                    .addPropertyNode("fechaHasta").addConstraintViolation();
        }
        return false;
    }
}
