package kh.edu.istad.ite.features.dataimport.service;

import kh.edu.istad.ite.shared.enums.ImportStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * Frees imports that were left mid-flight.
 *
 * A job is only ever moved out of a working state by the thread doing that
 * work. If the process is restarted — or killed, or redeployed — while a check
 * or an import is running, nothing is left to move it, and the shop is shown a
 * spinner that will never stop. That is not hypothetical: it is exactly what a
 * deploy in the middle of a large import does.
 *
 * So the states are swept twice: once at start-up, where anything still
 * claiming to be working is by definition abandoned, and periodically after
 * that for jobs whose thread died without unwinding.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StuckImportSweeper {

    /**
     * How long a job may claim to be working before it is presumed abandoned.
     *
     * Comfortably longer than a large file takes, because releasing a job that
     * is genuinely still running would let it be started a second time.
     */
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(30);

    private static final String CHECK_MESSAGE =
            "Checking this file was interrupted. Please try again.";
    private static final String COMMIT_MESSAGE =
            "This import was interrupted before it finished. The report shows what was brought in.";

    private final ImportJobStateService jobStateService;

    /**
     * At start-up nothing can still be running, whatever the database says —
     * the threads that were doing the work went with the last process.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void releaseOnStartup() {
        release(LocalDateTime.now());
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void releaseAbandoned() {
        release(LocalDateTime.now().minus(ABANDONED_AFTER));
    }

    /**
     * Tidying up is never worth failing over.
     *
     * This runs at start-up, so an exception escaping it would stop the whole
     * application coming up over housekeeping that the next sweep would have
     * done anyway — and a database briefly out of reach at boot is a poor
     * reason for the till not to open.
     */
    private void release(LocalDateTime olderThan) {
        try {
            int checking = jobStateService.releaseInterrupted(
                    ImportStatus.VALIDATING, ImportStatus.VALIDATION_FAILED, CHECK_MESSAGE, olderThan);

            int committing = jobStateService.releaseInterrupted(
                    ImportStatus.COMMITTING, ImportStatus.FAILED, COMMIT_MESSAGE, olderThan);

            if (checking + committing > 0) {
                log.info("Released {} interrupted import checks and {} interrupted imports",
                        checking, committing);
            }
        } catch (RuntimeException e) {
            log.warn("Could not sweep interrupted imports; will try again on the next run", e);
        }
    }
}
