package kh.edu.istad.ite.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextTaskExecutor;

/**
 * The threads a data migration runs on.
 *
 * Checking and committing ten thousand rows takes longer than a browser will
 * wait, so both happen off the request thread and the screen polls for
 * progress. A pool of its own rather than a global {@code @Async}: an import is
 * the longest-running thing this application does, and a shop migrating its
 * catalogue must not be able to occupy every worker the rest of the
 * application shares.
 *
 * Small pool, bounded queue, and it runs the work on the calling thread when
 * both are full. That is deliberate — the alternative is silently dropping an
 * import a shop has been told is running.
 */
@Configuration
public class ImportTaskConfig {

    /**
     * The pool itself, as a bean in its own right.
     *
     * It has to be one. A {@link ThreadPoolTaskExecutor} builds its underlying
     * thread pool in {@code afterPropertiesSet}, which only happens to beans —
     * so a pool created here, wrapped, and returned only as the wrapper is
     * never initialised, and every {@code execute} on it throws
     * "ThreadPoolTaskExecutor not initialized". Nothing about the wrapper
     * hints at that: the import simply never starts, the request that asked
     * for it fails, and the job sits in a checking state forever because the
     * thread that would have moved it on never existed.
     *
     * Being a bean also means Spring shuts it down with the application rather
     * than leaving its threads running.
     */
    @Bean("importExecutorPool")
    public ThreadPoolTaskExecutor importExecutorPool(ThreadPoolTaskExecutorBuilder builder) {
        return builder
                .corePoolSize(2)
                .maxPoolSize(4)
                .queueCapacity(50)
                .threadNamePrefix("data-import-")
                .build();
    }

    /**
     * The pool, wrapped so the signed-in user travels with the task.
     *
     * The commit calls the ordinary catalogue and inventory services, and those
     * decide what may be written by asking who is asking; on a bare pool thread
     * there is nobody there, and every write would be refused.
     */
    @Bean("importTaskExecutor")
    public TaskExecutor importTaskExecutor(
            @Qualifier("importExecutorPool") ThreadPoolTaskExecutor pool
    ) {
        return new DelegatingSecurityContextTaskExecutor(pool);
    }
}
