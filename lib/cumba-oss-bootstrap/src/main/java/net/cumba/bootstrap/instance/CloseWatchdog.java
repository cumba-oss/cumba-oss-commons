package net.cumba.bootstrap.instance;

import java.io.Closeable;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A Unix-domain {@link java.nio.channels.SocketChannel} has no {@code SO_TIMEOUT} (its
 * {@code socket()} adaptor is unsupported), so every blocking connect, write and read of the
 * hand-off is guarded by this watchdog: a scheduled task closes the channel when the deadline
 * passes, which ends the blocked call with an {@link java.nio.channels.AsynchronousCloseException}
 * - the time-out path. Chosen over a {@code Selector} loop: the protocol is strictly
 * request/response, so the blocking code stays linear.
 *
 * <p>
 * The scheduler is a seam: the real one runs on a daemon thread (it must never hold the JVM), a
 * test's fires its tasks by hand.
 */
public final class CloseWatchdog
{

    private static final System.Logger LOGGER = System.getLogger(CloseWatchdog.class.getName());

    /** Runs a task after a delay; the real one on a daemon thread. */
    public interface Scheduler
    {

        /**
         * Schedules a task.
         *
         * @param aTask
         *            the task
         * @param aDelayMillis
         *            the delay
         * @return a handle to cancel it
         */
        Future<?> schedule(Runnable aTask, long aDelayMillis);
    }


    /** A guard: closing it cancels the scheduled close. */
    public static final class Guard implements AutoCloseable
    {

        private final Future<?> task;

        private Guard(Future<?> aTask)
        {
            task = aTask;
        }


        @Override
        public void close()
        {
            task.cancel(false);
        }
    }

    private static final Scheduler DAEMON = daemonScheduler();

    private final Scheduler scheduler;

    /**
     * A watchdog over a scheduler.
     *
     * @param aScheduler
     *            the scheduler
     */
    public CloseWatchdog(Scheduler aScheduler)
    {
        scheduler = aScheduler;
    }


    /** The watchdog over the shared daemon scheduler. */
    public static CloseWatchdog daemon()
    {
        return new CloseWatchdog(DAEMON);
    }


    /**
     * Arms the watchdog: {@code aTarget} is closed when {@code aTimeoutMillis} pass before the
     * returned guard is closed.
     *
     * @param aTarget
     *            the channel to close on time-out
     * @param aTimeoutMillis
     *            the deadline
     * @return the guard; close it as soon as the guarded call returned
     */
    public Guard guard(Closeable aTarget, long aTimeoutMillis)
    {
        return new Guard(scheduler.schedule(() -> closeQuietly(aTarget), aTimeoutMillis));
    }


    private static void closeQuietly(Closeable aTarget)
    {
        try
        {
            aTarget.close();
        }
        catch (IOException e)
        {
            LOGGER.log(Level.DEBUG, "Single instance: watchdog close: " + e);
        }
    }


    // The executor lives for the JVM's life (one daemon thread); nothing closes it by design.
    @SuppressWarnings(
    {
            "PMD.CloseResource", "resource"
    })
    private static Scheduler daemonScheduler()
    {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, r ->
        {
            Thread t = new Thread(r, "instance-watchdog");
            t.setDaemon(true);
            return t;
        });
        executor.setRemoveOnCancelPolicy(true);
        return (task, delay) -> executor.schedule(task, delay, TimeUnit.MILLISECONDS);
    }
}
