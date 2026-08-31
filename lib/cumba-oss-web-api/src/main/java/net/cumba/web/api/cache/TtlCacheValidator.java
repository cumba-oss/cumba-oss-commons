package net.cumba.web.api.cache;

import java.util.function.LongSupplier;
import net.cumba.web.api.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * A {@link CacheValidator} that invalidates cache entries older than a configurable timeout. The
 * timeout is specified in milliseconds and compared against the difference between the current
 * system time and the cache entry's timestamp.
 *
 * <p>
 * This validator only considers the cache timestamp — the request and cached entry are ignored.
 * </p>
 *
 * <p>
 * Example:
 *
 * <pre>
 *
 * // Expire entries older than 24 hours
 * CacheValidator validator = new TtlCacheValidator(24 * 60 * 60 * 1000L);
 * </pre>
 *
 * @see CacheValidator
 */
public class TtlCacheValidator implements CacheValidator
{

    private final long timeoutMs;

    private final LongSupplier clock;

    /**
     * Creates a TTL validator with the given timeout.
     *
     * @param aTimeoutMs
     *            the maximum age of a cache entry in milliseconds. Must be positive.
     * @throws IllegalArgumentException
     *             if {@code aTimeoutMs} is not positive.
     */
    public TtlCacheValidator(long aTimeoutMs)
    {
        this(aTimeoutMs, System::currentTimeMillis);
    }


    /**
     * Clock-injecting variant for deterministic tests ({@code Fix #260}): the age is measured as
     * {@code clock − aCacheTimestamp}, so a test that derives a timestamp from its own clock read
     * would otherwise race this class's read — one millisecond tick between the two reads flipped
     * the exact-boundary case.
     */
    TtlCacheValidator(long aTimeoutMs, LongSupplier aClock)
    {
        if (aTimeoutMs <= 0)
        {
            throw new IllegalArgumentException("Timeout must be positive: " + aTimeoutMs);
        }
        this.timeoutMs = aTimeoutMs;
        this.clock = aClock;
    }


    @Override
    public boolean isValid(@Nullable HttpRequest aRequest, CacheEntry aEntry, long aCacheTimestamp)
    {
        return (clock.getAsLong() - aCacheTimestamp) <= timeoutMs;
    }
}
