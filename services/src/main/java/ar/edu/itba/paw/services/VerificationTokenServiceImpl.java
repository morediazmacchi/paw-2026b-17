// Implementa el contrato VerificationTokenService (modulo services-contracts).
package ar.edu.itba.paw.services;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.User;
import ar.edu.itba.paw.model.VerificationToken;
import ar.edu.itba.paw.persistence.VerificationTokenDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

@Service
public class VerificationTokenServiceImpl implements VerificationTokenService {

    private final VerificationTokenDao tokenDao;

    @Autowired
    public VerificationTokenServiceImpl(final VerificationTokenDao tokenDao) {
        this.tokenDao = tokenDao;
    }

    @Override
    @Transactional
    public VerificationToken create(final String token, final User user, final TokenPurpose purpose,
                                    final Date expiresAt) {
        return tokenDao.create(token, user, purpose, expiresAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VerificationToken> findByToken(final String token) {
        return tokenDao.findByToken(token);
    }

    @Override
    @Transactional
    public boolean delete(final String token) {
        return tokenDao.delete(token);
    }

    @Override
    @Transactional
    public int deleteByUserAndPurpose(final long userId, final TokenPurpose purpose) {
        return tokenDao.deleteByUserAndPurpose(userId, purpose);
    }
}
