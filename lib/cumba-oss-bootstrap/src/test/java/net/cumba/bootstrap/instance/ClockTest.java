package net.cumba.bootstrap.instance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** The real clock: the JVM's monotonic time, and a sleep that really passes it. */
class ClockTest
{

    @Test
    void theSystemClockIsTheMonotonicClockAndItsSleepPassesTime() throws Exception
    {
        long before = System.nanoTime();
        long reading = Clock.SYSTEM.nanoTime();
        long after = System.nanoTime();
        assertTrue(before <= reading && reading <= after,
                "a System.nanoTime() reading: " + before + " <= " + reading + " <= " + after);

        long start = Clock.SYSTEM.nanoTime();
        Clock.SYSTEM.sleep(50);
        long slept = TimeUnit.NANOSECONDS.toMillis(Clock.SYSTEM.nanoTime() - start);
        assertTrue(slept >= 50, "slept " + slept + " ms");
    }
}
