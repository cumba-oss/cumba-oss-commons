package net.cumba.web.api.cache;

import java.util.Optional;

/**
 * A no-op {@link ApiCache} implementation that never caches anything. All reads return empty, all
 * writes are silently discarded, and invalidation always returns {@code false}.
 */
public class NoOpApiCache implements ApiCache
{

    /** Shared singleton instance. */
    public static final NoOpApiCache INSTANCE = new NoOpApiCache();

    @Override
    public Optional<byte[]> read(String aPath)
    {
        return Optional.empty();
    }


    @Override
    public void write(String aPath, byte[] aContent)
    {
        // no-op
    }


    @Override
    public boolean invalidate(String aPath)
    {
        return false;
    }
}
