package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import net.cumba.web.api.http.HttpRequest;
import org.junit.jupiter.api.Test;

class TtlCacheValidatorTest
{

    private static final HttpRequest DUMMY_REQUEST = HttpRequest
            .get(URI.create("https://example.com/any/path")).build();

    private static final CacheEntry DUMMY_ENTRY = new CacheEntry("content");

    /**
     * Fixed clock ({@code Fix #260}): the time-sensitive cases below derive timestamps from the
     * same instant the validator measures against, so every assertion is deterministic — the
     * exact-boundary case used to race the real clock (a one-millisecond tick between the test's
     * read and the validator's flipped it red).
     */
    private static final long NOW = 1_755_000_000_000L;

    private static TtlCacheValidator fixedClockValidator(long aTimeoutMs)
    {
        return new TtlCacheValidator(aTimeoutMs, () -> NOW);
    }


    @Test
    void rejectsZeroTimeout()
    {
        assertThrows(IllegalArgumentException.class, () -> new TtlCacheValidator(0));
    }


    @Test
    void rejectsNegativeTimeout()
    {
        assertThrows(IllegalArgumentException.class, () -> new TtlCacheValidator(-1));
    }


    @Test
    void recentEntryIsValid()
    {
        TtlCacheValidator validator = fixedClockValidator(60_000L);
        assertTrue(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, NOW - 10_000L));
    }


    @Test
    void expiredEntryIsInvalid()
    {
        TtlCacheValidator validator = fixedClockValidator(60_000L);
        assertFalse(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, NOW - 120_000L));
    }


    @Test
    void entryAtExactTimeoutBoundaryIsValid()
    {
        // Age equals timeout — the boundary is inclusive (`<=`), and with the fixed clock the
        // case is exact, not a race.
        TtlCacheValidator validator = fixedClockValidator(60_000L);
        assertTrue(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, NOW - 60_000L));
    }


    @Test
    void entryOneMillisecondPastTheBoundaryIsInvalid()
    {
        // The complementary pin the racy real-clock version could never assert: one millisecond
        // past the timeout is expired.
        TtlCacheValidator validator = fixedClockValidator(60_000L);
        assertFalse(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, NOW - 60_001L));
    }


    @Test
    void defaultConstructorUsesTheRealClock()
    {
        // The production path: a just-written timestamp is far inside any positive timeout, so
        // this stays deterministic without pinning the boundary.
        TtlCacheValidator validator = new TtlCacheValidator(60_000L);
        assertTrue(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, System.currentTimeMillis()));
    }


    @Test
    void requestAndEntryAreIgnored()
    {
        TtlCacheValidator validator = fixedClockValidator(1000L);
        assertTrue(validator.isValid(DUMMY_REQUEST, DUMMY_ENTRY, NOW));
        assertTrue(validator.isValid(DUMMY_REQUEST, new CacheEntry("other"), NOW));
        assertTrue(
                validator.isValid(HttpRequest.get(URI.create("https://example.com/b/c/d")).build(),
                        DUMMY_ENTRY, NOW));
    }
}
