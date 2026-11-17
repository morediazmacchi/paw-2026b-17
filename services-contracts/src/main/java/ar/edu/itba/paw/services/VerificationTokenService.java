package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Los links de un solo uso que se mandan por mail (confirmar la cuenta, restablecer la clave). Es dueno de la tabla
 * de tokens: UserService lo usa para emitirlos y validarlos, y no habla con el DAO de tokens.
 */
public interface VerificationTokenService {

    @Transactional
    VerificationToken create(String token, User user, TokenPurpose purpose, Date expiresAt);

    @Transactional(readOnly = true)
    Optional<VerificationToken> findByToken(String token);

    /** Borra ese token (ya se uso). true si existia. */
    @Transactional
    boolean delete(String token);

    /**
     * Antes de mandar un link nuevo se invalidan los anteriores de ese mismo proposito de esa persona: solo el
     * ultimo link que se mando tiene que funcionar. Devuelve cuantos borro.
     */
    @Transactional
    int deleteByUserAndPurpose(long userId, TokenPurpose purpose);
}
