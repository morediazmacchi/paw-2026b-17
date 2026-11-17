package ar.edu.itba.paw.webapp.validation;

// Un formulario de filtros con rangos "desde/hasta" que se pueden poner al reves (precio, fecha). Lo valida
// @ValidSearchRanges sin conocer el form concreto (el paquete de validadores no depende del de forms).
public interface FilterRanges {

    // El precio maximo es menor que el minimo.
    boolean isPriceRangeInvalid();

    // La fecha "hasta" es anterior a la fecha "desde".
    boolean isDateRangeInvalid();
}
