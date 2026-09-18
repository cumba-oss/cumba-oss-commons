package net.cumba.web.api.cache;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.cumba.web.api.http.HttpResponse;

/**
 * Immutable representation of a cached HTTP response, capturing the status code, response headers,
 * and body content as raw bytes. This is the serializable form of an {@link HttpResponse} — the
 * body is held as a {@code byte[]} rather than a stream so it can be persisted to disk and
 * reconstructed later.
 *
 * <p>
 * <b>Bytes, not {@code String}.</b> The body is whatever the server sent, byte for byte. It is not
 * decoded, and this class does not know or guess a charset. Every cache in this package stores and
 * returns those same bytes, so a body survives a cache round trip unchanged even when it is not
 * valid text at all. A consumer that wants characters decodes explicitly, naming the charset:
 * {@code new String(entry.content(), StandardCharsets.UTF_8)}. Never
 * {@code new String(entry.content())} — that applies the platform default charset, which differs
 * between machines and silently corrupts every non-ASCII body.
 * </p>
 *
 * <p>
 * <b>The array is shared, not copied.</b> Neither the constructor nor {@link #content()} copies it.
 * A cached CDISC Library response runs to tens of megabytes, and copying it on construction and
 * again on every read is precisely the cost this type exists to avoid. The array is therefore
 * treated as immutable by convention: do not modify what you pass in, and do not modify what you
 * get back.
 * </p>
 *
 * <p>
 * Use {@link #of(HttpResponse, byte[])} to capture a live HTTP response, and
 * {@link #toHttpResponse()} to reconstruct one from cache.
 * </p>
 *
 * @param statusCode
 *            the HTTP status code (e.g., 200).
 * @param headers
 *            the response headers (immutable, defensively copied).
 * @param content
 *            the response body as raw bytes (an empty array for an empty body; never {@code null}).
 * @see ApiCache
 */
// [ArrayRecordComponent] is suppressed deliberately. The check objects to `byte[]` as a record
// component because arrays are mutable and have identity equals/hashCode. Both are already
// answered here: the class javadoc documents the non-copy as intentional (a response body must
// not be duplicated on every read), and equals/hashCode/toString are overridden below to compare
// and render the content by VALUE via java.util.Arrays. Changing the component's type would be a
// published-API change, which this sweep does not make.
@SuppressWarnings("ArrayRecordComponent")
public record CacheEntry(int statusCode, Map<String, List<String>> headers, byte[] content)
{

    /**
     * Compact constructor that defensively copies the headers map and its value lists to guarantee
     * immutability, and enforces the non-null {@code content} contract.
     *
     * <p>
     * The content array is deliberately <b>not</b> copied — see the class javadoc.
     * </p>
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
     *            the cached body content as raw bytes.
     */
    public CacheEntry(byte[] content)
    {
        this(200, Map.of(), content);
    }


    /**
     * Reconstructs an {@link HttpResponse} from this cache entry. The body is provided as a
     * {@link ByteArrayInputStream} over the stored bytes — no copy is made, so the returned stream
     * and this entry share the same array.
     *
     * @return a new {@link HttpResponse} with buffered body.
     */
    public HttpResponse toHttpResponse()
    {
        InputStream body = new ByteArrayInputStream(content);
        return new HttpResponse(statusCode, new LinkedHashMap<>(headers), body);
    }


    /**
     * Captures a live {@link HttpResponse} as a {@code CacheEntry}. The body must already have been
     * read from the response stream by the caller.
     *
     * @param aResponse
     *            the HTTP response (headers and status code are captured).
     * @param aBody
     *            the response body as raw bytes (already read from the response stream).
     * @return a new cache entry.
     */
    public static CacheEntry of(HttpResponse aResponse, byte[] aBody)
    {
        return new CacheEntry(aResponse.statusCode(), aResponse.headers(), aBody);
    }


    /**
     * Compares by value, including the body bytes.
     *
     * <p>
     * The record's generated {@code equals} would compare the {@code content} array by
     * <i>identity</i>, so two entries holding equal bodies read from the same cache file would not
     * be equal. That is not the contract this type had when the body was a {@code String}, and it
     * is not what callers mean.
     * </p>
     *
     * @param aOther
     *            the object to compare with.
     * @return {@code true} if the other object is a {@code CacheEntry} with the same status code,
     *         headers, and body bytes.
     */
    @Override
    public boolean equals(Object aOther)
    {
        if (this == aOther)
        {
            return true;
        }
        if (!(aOther instanceof CacheEntry other))
        {
            return false;
        }
        return statusCode == other.statusCode && headers.equals(other.headers)
                && Arrays.equals(content, other.content);
    }


    /**
     * Hashes by value, consistent with {@link #equals(Object)}.
     *
     * <p>
     * ⚠ This walks the whole body. Cache entries are not intended as hash-map keys; a
     * multi-megabyte body makes that expensive.
     * </p>
     *
     * @return the value hash.
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(statusCode, headers, Arrays.hashCode(content));
    }


    /**
     * Returns a diagnostic description that reports the body's <b>size</b> rather than its content.
     * The generated record {@code toString} would print the array's identity hash, and printing the
     * body itself would dump megabytes into a log line.
     *
     * @return a short description of this entry.
     */
    @Override
    public String toString()
    {
        return "CacheEntry[statusCode=" + statusCode + ", headers=" + headers + ", content="
                + content.length + " bytes]";
    }
}
