package ar.edu.itba.paw.services.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * El executor de los @Async (los mails), pero que espera al COMMIT.
 *
 * Los notify* se llaman desde adentro de metodos @Transactional. Sin esto, el
 * mail salia apenas se llamaba al notify, ANTES del commit: si despues la
 * transaccion hacia rollback, el mail ya se habia mandado ("te anotaste" a una
 * inscripcion que nunca quedo guardada).
 *
 * Como funciona:
 * - Si hay una transaccion abierta en este hilo, no manda la tarea al pool
 *   todavia: la anota para afterCommit(). Si hay rollback, afterCommit() no
 *   se llama nunca y el mail no sale.
 * - Si no hay transaccion, la manda al pool enseguida, como antes.
 *
 * Esta en un solo lugar (el executor) y no en cada notify*: todos los mails
 * pasan por aca.
 */
public class AfterCommitTaskExecutor implements TaskExecutor, DisposableBean {

    private final ThreadPoolTaskExecutor pool;

    public AfterCommitTaskExecutor(final ThreadPoolTaskExecutor pool) {
        this.pool = pool;
    }

    @Override
    public void execute(final Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pool.execute(task);
                }
            });
        } else {
            pool.execute(task);
        }
    }

    // Al bajar la app, apaga los hilos del pool (antes lo hacia Spring solo,
    // porque el pool era el bean).
    @Override
    public void destroy() {
        pool.shutdown();
    }
}
