// Implementa el contrato VerificationTokenDao (modulo persistence-contracts) con JPA/Hibernate.
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.TokenPurpose;
import ar.edu.itba.paw.model.VerificationToken;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@Repository
public class VerificationTokenJpaDao implements VerificationTokenDao {

    @PersistenceContext
    private EntityManager em;

    @Override
    public VerificationToken create(final VerificationToken token) {
        em.persist(token);
        // El id es el token (lo asigna el service, no la base): a diferencia de IDENTITY,
        // persist() no inserta enseguida. flush() para que una violacion de FK/PK salga
        // desde este metodo y no mas tarde, al commit.
        em.flush();
        return token;
    }

    @Override
    public Optional<VerificationToken> findByToken(final String token) {
        return Optional.ofNullable(em.find(VerificationToken.class, token));
    }

    @Override
    public boolean delete(final String token) {
        final VerificationToken found = em.find(VerificationToken.class, token);
        if (found == null) {
            return false;
        }
        em.remove(found);
        return true;
    }

    @Override
    public int deleteByUserAndPurpose(final long userId, final TokenPurpose purpose) {
        // Se cargan y se remueven una por una (hay a lo sumo un par): un bulk DELETE dejaria
        // los tokens ya cargados en el EntityManager como "fantasmas" (findByToken() los
        // seguiria devolviendo dentro de la misma transaccion).
        final List<VerificationToken> tokens = em.createQuery(
                        "FROM VerificationToken t WHERE t.user.id = :userId AND t.purpose = :purpose",
                        VerificationToken.class)
                .setParameter("userId", userId)
                .setParameter("purpose", purpose)
                .getResultList();
        tokens.forEach(em::remove);
        return tokens.size();
    }
}
