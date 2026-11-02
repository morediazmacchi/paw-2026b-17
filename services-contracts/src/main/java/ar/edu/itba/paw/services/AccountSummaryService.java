// Resumen de la cuenta para el menu lateral de "Mi cuenta". Implementacion: AccountSummaryServiceImpl.
package ar.edu.itba.paw.services;

import org.springframework.transaction.annotation.Transactional;

public interface AccountSummaryService {

    /**
     * Cuantas inscripciones, favoritos, eventos creados e invitaciones tiene la persona, en una sola
     * llamada. Los favoritos son solo los que todavia puede ver (ver FavoriteService.countViewable()), por
     * eso hace falta saber si es admin (los admins ven todos los eventos). Todo son COUNT en la base.
     */
    @Transactional(readOnly = true)
    AccountCounts countsFor(long userId, boolean isAdmin);
}
