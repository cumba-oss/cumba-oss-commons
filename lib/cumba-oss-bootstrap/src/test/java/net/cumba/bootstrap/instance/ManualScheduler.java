package net.cumba.bootstrap.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/** A watchdog scheduler whose tasks run only when the test fires them. */
final class ManualScheduler implements CloseWatchdog.Scheduler
{

    private record Task(Runnable task, CompletableFuture<Void> handle)
    {
    }

    private final List<Task> tasks = new ArrayList<>();

    private final List<Long> delays = new ArrayList<>();

    @Override
    public synchronized Future<?> schedule(Runnable aTask, long aDelayMillis)
    {
        CompletableFuture<Void> handle = new CompletableFuture<>();
        tasks.add(new Task(aTask, handle));
        delays.add(aDelayMillis);
        return handle;
    }


    /** Runs every scheduled task that was not cancelled; returns how many ran. */
    synchronized int fireAll()
    {
        int fired = 0;
        for (Task t : tasks)
        {
            if (!t.handle().isCancelled())
            {
                t.task().run();
                fired++;
            }
        }
        tasks.clear();
        return fired;
    }


    synchronized int pending()
    {
        int n = 0;
        for (Task t : tasks)
        {
            if (!t.handle().isCancelled())
            {
                n++;
            }
        }
        return n;
    }


    synchronized List<Long> delays()
    {
        return List.copyOf(delays);
    }
}
