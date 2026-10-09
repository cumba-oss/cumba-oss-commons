package net.cumba.bootstrap.instance;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A clock the test drives: {@link #sleep} advances it by the requested time (no real pause) and
 * every {@link #nanoTime} reading may advance it by a configured step, which is how a test makes
 * reply-driven loops (the start cap) see time pass without a thread hand-shake.
 */
final class FakeClock implements Clock
{

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);

    private volatile long stepPerReadingMillis;

    private final AtomicLong sleeps = new AtomicLong();

    private volatile Runnable onSleep = () ->
    {
    };

    /**
     * A hook run on every {@link #sleep}: "meanwhile, elsewhere" (a primary writing its endpoint).
     */
    void onSleep(Runnable aHook)
    {
        onSleep = aHook;
    }


    /** The time each {@link #nanoTime} call advances the clock by, after the reading. */
    void stepPerReading(long aMillis)
    {
        stepPerReadingMillis = aMillis;
    }


    void advance(long aMillis)
    {
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(aMillis));
    }


    long sleeps()
    {
        return sleeps.get();
    }


    @Override
    public long nanoTime()
    {
        return nanos.getAndAdd(TimeUnit.MILLISECONDS.toNanos(stepPerReadingMillis));
    }


    @Override
    public void sleep(long aMillis)
    {
        sleeps.incrementAndGet();
        advance(aMillis);
        onSleep.run();
    }
}
