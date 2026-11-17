package ar.edu.itba.paw.webapp.validation;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SearchRangesValidatorTest {

    // Un form de filtros minimo: los rangos los decide cada test.
    @ValidSearchRanges
    private static final class Filters implements FilterRanges {
        private final boolean priceInvalid;
        private final boolean dateInvalid;

        Filters(final boolean priceInvalid, final boolean dateInvalid) {
            this.priceInvalid = priceInvalid;
            this.dateInvalid = dateInvalid;
        }

        @Override
        public boolean isPriceRangeInvalid() { return priceInvalid; }

        @Override
        public boolean isDateRangeInvalid() { return dateInvalid; }
    }

    private static Validator validator;

    @BeforeClass
    public static void createValidator() {
        // Sin expresiones EL: alcanza para validar y no pide un motor de EL en los tests.
        validator = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory().getValidator();
    }

    private static Set<String> invalidFields(final Filters filters) {
        return validator.validate(filters).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void testFiltersWithValidRangesHaveNoErrors() {
        assertTrue(invalidFields(new Filters(false, false)).isEmpty());
    }

    @Test
    public void testAnInvertedPriceRangeMarksTheMaximumPriceField() {
        assertEquals("[precioMax]", invalidFields(new Filters(true, false)).toString());
    }

    @Test
    public void testAnInvertedDateRangeMarksTheEndDateField() {
        assertEquals("[fechaHasta]", invalidFields(new Filters(false, true)).toString());
    }

    @Test
    public void testBothRangesInvertedMarkBothFields() {
        assertEquals("[fechaHasta, precioMax]", invalidFields(new Filters(true, true)).toString());
    }
}
