package net.cumba.web.api.cache;

import net.cumba.web.api.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * Strategy interface for validating cache entries before they are served. Implementations decide
 * whether a cached response is still valid based on the original request, the cached entry, and the
 * time the entry was cached.
 *
 * <p>
 * The validator receives the full {@link HttpRequest} and {@link CacheEntry}, allowing
 * sophisticated validation strategies that consider request headers, cached response headers,
 * endpoint path, and cache age.
 * </p>
 *
 * <p>
 * Common strategies:
 * <ul>
 * <li><b>TTL-based</b> — invalidate entries older than a configured timeout (see
 * {@link TtlCacheValidator}).</li>
 * <li><b>Header-based</b> — respect HTTP cache headers like {@code Cache-Control}, {@code ETag}, or
 * {@code Last-Modified} from the cached response.</li>
 * <li><b>Request-aware</b> — bypass cache when the request includes {@code Cache-Control: no-cache}
 * or similar directives.</li>
 * <li><b>Callback-based</b> — delegate the decision to application-specific logic supplied as a
 * lambda or method reference.</li>
 * </ul>
 *
 * <p>
 * Example usage:
 *
 * <pre>
 *
 * // TTL: expire after 1 hour
 * CacheValidator ttl = new TtlCacheValidator(3_600_000L);
 *
 * // Callback: always refetch paths starting with "/volatile"
 * CacheValidator callback = (request, entry,
 *         ts) -&gt; !request.uri().getPath().startsWith("/volatile");
 *
 * FileApiCache cache = new FileApiCache(dir, ".json", callback);
 * </pre>
 *
 * @see TtlCacheValidator
 * @see FileApiCache
 */
@FunctionalInterface
public interface CacheValidator
{

    /**
     * Determines whether a cached entry is still valid for the given request.
     *
     * @param aRequest
     *            the HTTP request for which the cached entry is being considered, or {@code null}
     *            when validation is triggered by a path-based read that carries no request context.
     * @param aEntry
     *            the cached entry (includes status code, response headers, and the body as raw
     *            bytes -- decode with an explicit charset, see {@link CacheEntry}).
     * @param aCacheTimestamp
     *            the time the cache entry was created, in milliseconds since the epoch (as returned
     *            by {@link ApiCache#cacheTimestamp(String)}).
     * @return {@code true} if the cached entry should be used, {@code false} if it should be
     *         invalidated and re-fetched.
     */
    boolean isValid(@Nullable HttpRequest aRequest, CacheEntry aEntry, long aCacheTimestamp);


    /**
     * Declares whether {@link #isValid} actually inspects {@link CacheEntry#content()}.
     *
     * <p>
     * Materialising the content is what forces a cached response to be read into memory in full
     * before validation can even be asked — for a multi-megabyte body that is a copy the caller may
     * never need. A validator that decides on the timestamp, the request or the cached headers
     * alone should return {@code false}, which lets a cache serve the entry as a stream (see
     * {@link ApiCache#openStream(HttpRequest)}) instead of a fully buffered {@code CacheEntry}. The
     * entry passed to {@link #isValid} then carries the real status code and headers but an
     * <b>empty</b> content array.
     * </p>
     *
     * <p>
     * The default is {@code true} — the conservative direction. A validator that inspects the body
     * and wrongly declared otherwise would silently decide on zero bytes, which is the failure this
     * default exists to avoid. Overriding it is an opt-in optimisation, never a requirement.
     * </p>
     *
     * @return {@code true} if {@link #isValid} needs the entry's body content, {@code false} if
     *         status code, headers, request and timestamp suffice.
     */
    default boolean needsContent()
    {
        return true;
    }
}
