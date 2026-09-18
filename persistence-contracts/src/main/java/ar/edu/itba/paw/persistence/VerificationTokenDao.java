// Contrato de persistencia para VerificationToken. Implementacion:
// VerificationTokenJdbcDao (modulo persistence).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.VerificationToken;

import java.util.Optional;

public interface VerificationTokenDao {
    void create(VerificationToken token);

    Optional<VerificationToken> findByToken(String token);

    void delete(String token);

    // Antes de mandar un link nuevo (registro de nuevo, "reenviar", "olvide mi
    // contraseña" de nuevo) se invalidan los anteriores de ese mismo proposito:
    // solo el ultimo link que se mando tiene que funcionar.
    void deleteByUserAndPurpose(long userId, TokenPurpose purpose);
}
