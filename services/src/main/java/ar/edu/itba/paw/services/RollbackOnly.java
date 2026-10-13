package ar.edu.itba.paw.services;

import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Para los services que atrapan un error del insert (por ejemplo un UNIQUE violado por dos
 * pedidos simultaneos) y devuelven un resultado en vez de dejar escapar la excepcion.
 *
 * Con JPA, cuando Hibernate falla un insert la transaccion queda marcada "solo rollback", aunque
 * el service atrape la excepcion. Si despues el metodo termina normal, Spring intenta commitear,
 * se encuentra con esa marca y tira UnexpectedRollbackException (un 500). Marcarla nosotros
 * explicitamente dice "ya se que esta transaccion se descarta": Spring hace el rollback sin
 * quejarse y el resultado que devolvimos llega al controller.
 */
final class RollbackOnly {

    private RollbackOnly() {
    }

    /** Descarta la transaccion actual si hay una (en un test unitario con mocks no hay ninguna). */
    static void ifInTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }
}
