package net.cumba.web.api.cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.cumba.web.api.http.HttpResponse;

/**
 * Immutable representation of a cached HTTP response, capturing the status code, response headers,
 * and body content as a string. This is the serializable form of an {@link HttpResponse} — the body
 * is stored as a string rather than a stream so it can be persisted to disk and reconstructed
 * later.
 *
 * <p>
 * Use {@link #of(HttpResponse, String)} to capture a live HTTP response, and
 * {@link #toHttpResponse()} to reconstruct one from cache.
 * </p>
 *
 * @param statusCode
 *            the HTTP status code (e.g., 200).
 * @param headers
 *            the response headers (immutable, defensively copied).
 * @param content
 *            the response body as a string (an empty string for an empty body; never {@code null}).
 * @see ApiCache
 */
public record CacheEntry(int statusCode, Map<String, List<String>> headers, String content)
{

    /**
     * Compact constructor that defensively copies the headers map and its value lists to guarantee
     * immutability, and enforces the non-null {@code content} contract.
     */
    public CacheEntry
    {
        Objects.requireNonNull(content, "content");
        if (headers != null)
        {
            var copy = new LinkedHashMap<String, List<String>>();
            headers.forEach((k, v) -> copy.put(k, List.copyOf(v)));
            headers = Collections.unmodifiableMap(copy);
        }
        else
        {
            headers = Collections.emptyMap();
        }
    }


    /**
     * Convenience constructor for cached content without HTTP metadata. Assumes status 200 and
     * empty headers. This is used as a fallback when reading cache files that have no accompanying
     * metadata (e.g., pre-existing cache entries).
     *
     * @param content
     *            the cached body content.
     */
    public CacheEntry(String content)
    {
        this(200, Map.of(), content);
    }


    /**
     * Reconstructs an {@link HttpResponse} from this cache entry. The body is provided as a
     * {@link ByteArrayInputStream} so it can be read by the caller.
     *
     * @return a new {@link HttpResponse} with buffered body.
     */
    public HttpResponse toHttpResponse()
    {
        InputStream body = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        return new HttpResponse(statusCode, new LinkedHashMap<>(headers), body);
    }


    /**
     * Captures a live {@link HttpResponse} as a {@code CacheEntry}. The body must already have been
     * read as a string by the caller.
     *
     * @param aResponse
     *            the HTTP response (headers and status code are captured).
     * @param aBody
     *            the response body as a string (already read from the response stream).
     * @return a new cache entry.
     */
    public static CacheEntry of(HttpResponse aResponse, String aBody)
    {
        return new CacheEntry(aResponse.statusCode(), aResponse.headers(), aBody);
    }
}
