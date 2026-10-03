// Datos de un evento que chocan contra una restriccion de la base al guardarse
// (categoria que dejo de existir entre que se cargo el form y se mando el POST,
// u otra violacion de integridad). Es rara -- no es un resultado de negocio
// esperado como EMAIL_TAKEN o CAPACITY_TOO_LOW, que pasan todo el tiempo en el
// uso normal -- por eso se modela como excepcion y no como un resultado
// tipado. Vive en model (no en services, no en webapp) porque la tira
// EventServiceImpl.create() y la atrapa EventController, y los dos ya
// dependen de este modulo.
package ar.edu.itba.paw.exception;

public class InvalidEventDataException extends RuntimeException {

    public InvalidEventDataException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
