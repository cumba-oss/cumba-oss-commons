package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;

// the Closeables here are counters behind a lambda, not resources
@SuppressWarnings("PMD.CloseResource")
class CloseWatchdogTest
{

    @Test
    void anArmedGuardClosesTheTargetWhenFired()
    {
        ManualScheduler scheduler = new ManualScheduler();
        CloseWatchdog watchdog = new CloseWatchdog(scheduler);
        AtomicInteger closed = new AtomicInteger();
        Closeable target = closed::incrementAndGet;
        CloseWatchdog.Guard guard = watchdog.guard(target, 1234);
        assertEquals(List.of(1234L), scheduler.delays());
        assertEquals(1, scheduler.fireAll());
        assertEquals(1, closed.get(), "the deadline passed: the target is closed");
        guard.close();
    }


    @Test
    void aClosedGuardCancelsTheClose()
    {
        ManualScheduler scheduler = new ManualScheduler();
        CloseWatchdog watchdog = new CloseWatchdog(scheduler);
        AtomicInteger closed = new AtomicInteger();
        Closeable target = closed::incrementAndGet;
        CloseWatchdog.Guard g = watchdog.guard(target, 10);
        assertEquals(1, scheduler.pending());
        g.close();
        assertEquals(0, scheduler.pending());
        assertEquals(0, scheduler.fireAll());
        assertEquals(0, closed.get(), "the call returned in time: nothing is closed");
    }


    @Test
    void aFailingCloseIsSwallowed()
    {
        ManualScheduler scheduler = new ManualScheduler();
        CloseWatchdog watchdog = new CloseWatchdog(scheduler);
        Closeable target = () ->
        {
            throw new IOException("already closed");
        };
        watchdog.guard(target, 10);
        try (LogCapture log = LogCapture.on(CloseWatchdog.class))
        {
            assertEquals(1, scheduler.fireAll());
            log.assertLogged(Level.FINE, "watchdog close: java.io.IOException: already closed");
        }
    }


    @Test
    void theDaemonSchedulerRunsOnADaemonThread() throws Exception
    {
        CountDownLatch ran = new CountDownLatch(1);
        AtomicBoolean daemon = new AtomicBoolean();
        Closeable target = () ->
        {
            daemon.set(Thread.currentThread().isDaemon());
            ran.countDown();
        };
        CloseWatchdog.daemon().guard(target, 1);
        assertTrue(ran.await(10, TimeUnit.SECONDS), "the daemon scheduler fired");
        assertTrue(daemon.get(), "the watchdog thread must never hold the JVM");
        // a cancelled task does not fire
        AtomicBoolean fired = new AtomicBoolean();
        Closeable late = () -> fired.set(true);
        CloseWatchdog.daemon().guard(late, 50).close();
        Thread.sleep(120);
        assertFalse(fired.get());
    }
}
