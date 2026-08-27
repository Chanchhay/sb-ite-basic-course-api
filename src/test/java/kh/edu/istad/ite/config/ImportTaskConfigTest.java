package kh.edu.istad.ite.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * That the import pool actually accepts work.
 *
 * Not a formality. A {@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
 * builds its threads in {@code afterPropertiesSet}, so one created inside a
 * {@code @Bean} method and returned only wrapped is never initialised — and
 * every submission throws "ThreadPoolTaskExecutor not initialized". That failure
 * is invisible until the first import: the request that starts one fails with a
 * server error, and the job is left in a checking state that nothing will ever
 * move it out of, because the thread that would have done so never existed.
 * These tests exist because that shipped once.
 */
class ImportTaskConfigTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(ImportTaskConfig.class);

    @Test
    void runsWorkHandedToTheImportExecutor() {
        contexts.run(context -> {
            TaskExecutor executor = context.getBean("importTaskExecutor", TaskExecutor.class);
            CountDownLatch ran = new CountDownLatch(1);

            executor.execute(ran::countDown);

            assertThat(ran.await(5, TimeUnit.SECONDS))
                    .as("work handed to the import executor should actually run")
                    .isTrue();
        });
    }

    /** Imports run on their own threads, not on whatever the rest of the app shares. */
    @Test
    void runsOnAThreadOfItsOwn() {
        contexts.run(context -> {
            TaskExecutor executor = context.getBean("importTaskExecutor", TaskExecutor.class);
            AtomicReference<String> threadName = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);

            executor.execute(() -> {
                threadName.set(Thread.currentThread().getName());
                ran.countDown();
            });

            assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(threadName.get()).startsWith("data-import-");
        });
    }

    /**
     * The commit calls services that decide what may be written by asking who
     * is asking. Without the caller's identity on the worker thread, every one
     * of those writes is refused.
     */
    @Test
    void carriesTheCallersIdentityOntoTheWorkerThread() {
        contexts.run(context -> {
            TaskExecutor executor = context.getBean("importTaskExecutor", TaskExecutor.class);

            var authentication = new org.springframework.security.authentication
                    .UsernamePasswordAuthenticationToken("shopkeeper", "n/a", java.util.List.of());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            AtomicReference<Object> seen = new AtomicReference<>();
            CountDownLatch ran = new CountDownLatch(1);

            try {
                executor.execute(() -> {
                    seen.set(SecurityContextHolder.getContext().getAuthentication());
                    ran.countDown();
                });

                assertThat(ran.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(seen.get()).isEqualTo(authentication);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }
}
