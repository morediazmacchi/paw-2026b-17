package ar.edu.itba.paw.webapp.util;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.util.HashMap;
import java.util.Map;

// field -> mensaje ya resuelto en el idioma actual, para mostrar al lado de
// cada campo del form (ver *FieldErrors en los JSP). Es el unico lugar que
// resuelve cada FieldError contra el MessageSource: los controllers solo le
// pasan el BindingResult de su form.
public final class FormErrors {

    private FormErrors() {
    }

    public static Map<String, String> of(final BindingResult errors, final MessageSource messageSource) {
        final Map<String, String> fieldErrors = new HashMap<>();
        for (final FieldError fieldError : errors.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), messageSource.getMessage(fieldError, LocaleContextHolder.getLocale()));
        }
        return fieldErrors;
    }
}
