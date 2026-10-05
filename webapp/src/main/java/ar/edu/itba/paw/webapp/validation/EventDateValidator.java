package ar.edu.itba.paw.webapp.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class EventDateValidator implements ConstraintValidator<ValidEventDate, String> {

    // Mismo regex que el @Pattern de EventForm.eventDate: si ni siquiera
    // tiene esa forma, se deja que ese @Pattern reporte el error de formato
    // en vez de competir por el mismo campo con un mensaje distinto.
    private static final String SHAPE_REGEX = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}$";

    @Override
    public boolean isValid(final String value, final ConstraintValidatorContext context) {
        if (value == null || value.isEmpty() || !value.matches(SHAPE_REGEX)) {
            return true;
        }
        // Sin setLenient(false), SimpleDateFormat "desborda" las fechas imposibles
        // en silencio: 2026-13-45T99:99 pasa el regex de arriba y se convierte en
        // 2027-02-18 04:39 sin tirar ningun error.
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
        format.setLenient(false);
        final Date parsed;
        try {
            parsed = format.parse(value);
        } catch (final ParseException e) {
            return fail(context, "{ValidEventDate.format}");
        }
        if (parsed.before(new Date())) {
            return fail(context, "{ValidEventDate.past}");
        }
        if (parsed.after(maxEventDate())) {
            return fail(context, "{ValidEventDate.tooFar}");
        }
        return true;
    }

    private static Date maxEventDate() {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 2);
        return calendar.getTime();
    }

    private static boolean fail(final ConstraintValidatorContext context, final String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
