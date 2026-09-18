// Los dos usos de verification_token: confirmar el mail al registrarse, o
// autorizar un cambio de contraseña pedido desde "Olvidé mi contraseña".
package ar.edu.itba.paw.model;

public enum TokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
