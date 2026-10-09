package net.cumba.bootstrap.instance;

/**
 * Time and sleeping, injected so every timeout of the hand-off runs on a fake clock in a unit test
 * (no real sleep, no mutant that only a time-out can kill).
 */
public interface Clock
{

    /** The JVM's own monotonic clock and {@link Thread#sleep(long)}. */
    Clock SYSTEM = new Clock()
    {

        @Override
        public long nanoTime()
        {
            return System.nanoTime();
        }


        @Override
        public void sleep(long aMillis) throws InterruptedException
        {
            Thread.sleep(aMillis);
        }
    };

    /**
     * A monotonic reading, in nanoseconds.
     *
     * @return the reading
     */
    long nanoTime();


    /**
     * Pauses the calling thread.
     *
     * @param aMillis
     *            how long, in milliseconds
     * @throws InterruptedException
     *             when interrupted while sleeping
     */
    void sleep(long aMillis) throws InterruptedException;
}
