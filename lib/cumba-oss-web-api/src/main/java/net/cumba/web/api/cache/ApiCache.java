package net.cumba.web.api.cache;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import net.cumba.web.api.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * Abstraction for API response caching. Implementations store and retrieve responses keyed by HTTP
 * request. The cache is best-effort — failures during write or invalidation should not prevent
 * normal operation.
 *
 * <p>
 * The primary API is request-aware: {@link #get(HttpRequest)},
 * {@link #put(HttpRequest, CacheEntry)}, and {@link #invalidate(HttpRequest)}. These methods
 * receive the full HTTP request, allowing the cache to derive its own cache key (via
 * {@link #toCacheKey(HttpRequest)}) and to pass request context to the {@link CacheValidator}.
 * </p>
 *
 * <p>
 * Path-based methods ({@link #read(String)}, {@link #write(String, String)},
 * {@link #invalidate(String)}) are retained as the low-level storage API that file-based
 * implementations build on.
 * </p>
 *
 * @see FileApiCache
 * @see GzipFileApiCache
 * @see NoOpApiCache
 */
public interface ApiCache
{

    /**
     * Maximum length of a cache key, measured in the characters a file-backed implementation would
     * need to name a file after it. Keys longer than this have their tail replaced by a hash — see
     * {@link #toCacheKey(HttpRequest)}.
     *
     * <p>
     * The common file-name limit is 255 bytes; the remaining headroom covers the cache file
     * extension (e.g. {@code .json.gz}) and the {@code .meta} sidecar suffix.
     * </p>
     */
    int MAX_ENCODED_CACHE_KEY_LENGTH = 200;

    /**
     * Number of hexadecimal characters of the key digest retained when a key is shortened.
     */
    int CACHE_KEY_HASH_LENGTH = 16;

    /**
     * Separator placed between the readable head of a shortened key and its digest. Only keys past
     * {@link #MAX_ENCODED_CACHE_KEY_LENGTH} are shortened, so a shorter key that happens to contain
     * this character is left alone and cannot be confused with a shortened one.
     */
    String CACHE_KEY_HASH_SEPARATOR = "~";

    // --- Request-aware API ---

    /**
     * Retrieves a cached response for the given HTTP request. The cache derives the storage key
     * from the request via {@link #toCacheKey(HttpRequest)} and may consult a
     * {@link CacheValidator} to determine if the cached entry is still valid.
     *
     * <p>
     * On a miss, the {@linkplain #toLegacyCacheKey(HttpRequest) legacy path-only key} is tried as
     * well, so entries written before query strings participated in the key remain readable. The
     * fallback itself is <b>read-only</b> — it never writes.
     * </p>
     *
     * <p>
     * <b>It does not follow that the legacy entries go away.</b> A legacy hit is a hit, so a client
     * that only writes after a miss never writes the entry back under the current key. And because
     * the current key of a <i>query-less</i> request is the path itself, a legacy entry cannot be
     * told apart from one written today for the unparameterised form of the same endpoint: a body
     * fetched without a query will be served to a request that carries one. That is what the cache
     * already did before the query participated in the key, so it is not a regression — but the
     * query only truly participates once the stored entries have been re-keyed. See
     * {@code plans/PLAN-api-cache-key-query-strings.md} § 5, phase 1.
     * </p>
     *
     * @param aRequest
     *            the HTTP request to look up.
     * @return the cached entry, or empty if not in cache or if the entry has been invalidated.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    default Optional<CacheEntry> get(HttpRequest aRequest) throws IOException
    {
        String cacheKey = toCacheKey(aRequest);
        Optional<CacheEntry> entry = readEntry(cacheKey);
        if (entry.isEmpty())
        {
            String legacyKey = toLegacyCacheKey(aRequest);
            if (!legacyKey.equals(cacheKey))
            {
                entry = readEntry(legacyKey);
            }
        }
        return entry;
    }


    /**
     * Stores a response in the cache, keyed by the given HTTP request.
     *
     * @param aRequest
     *            the HTTP request that produced the response.
     * @param aEntry
     *            the response to cache (status code, headers, and body).
     */
    default void put(HttpRequest aRequest, CacheEntry aEntry)
    {
        writeEntry(toCacheKey(aRequest), aEntry);
    }


    /**
     * Removes the cached entry for the given HTTP request.
     *
     * <p>
     * Any entry held under the {@linkplain #toLegacyCacheKey(HttpRequest) legacy path-only key} is
     * removed as well — otherwise {@link #get(HttpRequest)} would keep serving it through the
     * legacy read fallback after the caller asked for it to be dropped. Note that this reaches
     * further than the request names: the legacy key of {@code /p?x=1} is {@code /p}, so the entry
     * for the query-less form of the same path goes too.
     * </p>
     *
     * @param aRequest
     *            the HTTP request whose cached response should be removed.
     * @return {@code true} if a cache entry was removed, {@code false} otherwise.
     * @throws IOException
     *             in case of an I/O error.
     */
    default boolean invalidate(HttpRequest aRequest) throws IOException
    {
        String cacheKey = toCacheKey(aRequest);
        boolean removed = invalidate(cacheKey);
        String legacyKey = toLegacyCacheKey(aRequest);
        if (!legacyKey.equals(cacheKey))
        {
            removed |= invalidate(legacyKey);
        }
        return removed;
    }


    /**
     * Derives a cache key from an HTTP request: the URI path, plus a normalised query string when
     * one is present.
     *
     * <p>
     * The query <b>must</b> participate in the key. Two requests to the same path with different
     * queries — {@code /mdr/search?q=alpha} and {@code /mdr/search?q=beta} — return different
     * documents, and keying on the path alone silently serves the first for the second.
     * </p>
     *
     * <p>
     * A request with no query yields exactly the path, so entries written before this behaviour
     * existed remain reachable under the same key. Query-bearing requests do not; for those, see
     * the legacy read fallback in {@link #get(HttpRequest)}.
     * </p>
     *
     * <p>
     * Keys longer than {@link #MAX_ENCODED_CACHE_KEY_LENGTH} have their tail replaced by a digest
     * of the whole key, keeping the head readable — a cache directory of opaque digests cannot be
     * inspected or grepped when something goes wrong. Implementations may override this to
     * incorporate request headers (e.g. {@code Accept}) or other request properties into the key.
     * </p>
     *
     * @param aRequest
     *            the HTTP request.
     * @return the cache key.
     */
    default String toCacheKey(HttpRequest aRequest)
    {
        String path = aRequest.uri().getPath();
        String key = path != null && !path.isEmpty() ? path : "/";
        String query = normaliseQuery(aRequest.uri().getRawQuery());
        return shortenCacheKey(query.isEmpty() ? key : key + "?" + query);
    }


    /**
     * Derives the cache key this interface produced before query strings participated in it: the
     * URI path alone.
     *
     * <p>
     * Used only as a read-side fallback for entries written under the old scheme — see
     * {@link #get(HttpRequest)}. Nothing writes under this key.
     * </p>
     *
     * @param aRequest
     *            the HTTP request.
     * @return the legacy cache key.
     */
    default String toLegacyCacheKey(HttpRequest aRequest)
    {
        String path = aRequest.uri().getPath();
        return path != null ? path : "/";
    }


    /**
     * Normalises a raw query string for use in a cache key: drops empty pairs and
     * {@linkplain #nonSemanticQueryParameters() non-semantic} parameters, then sorts what remains
     * so that {@code ?a=1&b=2} and {@code ?b=2&a=1} agree.
     *
     * <p>
     * Repeated parameters are kept — sorting preserves every occurrence — since a repeated
     * parameter generally selects more content rather than replacing it. Their <i>order</i> is not
     * kept: {@code ?sort=name&sort=date} and {@code ?sort=date&sort=name} yield one key. No
     * endpoint in this codebase gives that order a meaning.
     * </p>
     *
     * <p>
     * Percent-encoding is left exactly as received — the key is opaque, and
     * {@code FileApiCache.toCacheFileName} encodes it again on the way to disk.
     * </p>
     *
     * @param aRawQuery
     *            the raw query string, or {@code null}.
     * @return the normalised query, or {@code ""} when there is nothing to add.
     */
    default String normaliseQuery(@Nullable String aRawQuery)
    {
        if (aRawQuery == null || aRawQuery.isEmpty())
        {
            return "";
        }
        Set<String> ignored = nonSemanticQueryParameters();
        List<String> pairs = new ArrayList<>();
        for (String pair : aRawQuery.split("&", -1))
        {
            if (pair.isEmpty())
            {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq < 0 ? pair : pair.substring(0, eq);
            if (!ignored.contains(name))
            {
                pairs.add(pair);
            }
        }
        Collections.sort(pairs);
        return String.join("&", pairs);
    }


    /**
     * Returns the query parameters that do not affect the response body and are therefore excluded
     * from the cache key.
     *
     * <p>
     * Empty by default — the conservative direction, since wrongly treating a parameter as
     * non-semantic merges two genuinely different bodies under one key, which is the very defect
     * this method exists to avoid. See {@code plans/PLAN-api-cache-key-query-strings.md} § 2 before
     * adding to it.
     * </p>
     *
     * @return the parameter names to ignore.
     */
    default Set<String> nonSemanticQueryParameters()
    {
        return Set.of();
    }


    /**
     * Bounds the length of a cache key by replacing its tail with a digest of the whole key.
     *
     * <p>
     * File-backed implementations name a file after the key, and file names are limited (255 bytes
     * on ext4) — a user-supplied query such as {@code /mdr/search?q=…} can reach that. Only the
     * tail is hashed: the readable head is what makes a cache directory diagnosable by hand.
     * </p>
     *
     * @param aKey
     *            the derived cache key.
     * @return the key itself when short enough, otherwise its head plus a digest.
     */
    static String shortenCacheKey(String aKey)
    {
        if (encodedKeyLength(aKey) <= MAX_ENCODED_CACHE_KEY_LENGTH)
        {
            return aKey;
        }
        // The separator is measured encoded too — '~' costs three characters as "%7E", so
        // subtracting its plain length would push the result past the bound it exists to enforce.
        int budget = MAX_ENCODED_CACHE_KEY_LENGTH - encodedKeyLength(CACHE_KEY_HASH_SEPARATOR)
                - CACHE_KEY_HASH_LENGTH;
        int end = 0;
        int used = 0;
        while (end < aKey.length())
        {
            int codePoint = aKey.codePointAt(end);
            int width = encodedKeyLength(new String(Character.toChars(codePoint)));
            if (used + width > budget)
            {
                break;
            }
            used += width;
            end += Character.charCount(codePoint);
        }
        return aKey.substring(0, end) + CACHE_KEY_HASH_SEPARATOR + cacheKeyDigest(aKey);
    }


    /**
     * Returns the length a key occupies once encoded for use as a file name, mirroring
     * {@code FileApiCache.toCacheFileName} so that the bound applies to what actually reaches the
     * file system rather than to the raw key.
     *
     * @param aKey
     *            the key, or a fragment of one.
     * @return the encoded length in characters.
     */
    private static int encodedKeyLength(String aKey)
    {
        return URLEncoder.encode(aKey.replace('/', '_'), StandardCharsets.UTF_8).length();
    }


    /**
     * Returns the truncated hexadecimal SHA-256 digest identifying a shortened key.
     *
     * @param aKey
     *            the full key.
     * @return {@link #CACHE_KEY_HASH_LENGTH} hexadecimal characters.
     */
    private static String cacheKeyDigest(String aKey)
    {
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException _)
        {
            throw new IllegalStateException("SHA-256 is required to derive cache keys");
        }
        byte[] hash = digest.digest(aKey.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash).substring(0, CACHE_KEY_HASH_LENGTH);
    }

    // --- Path-based storage API ---


    /**
     * Reads cached content for the given endpoint path.
     *
     * @param aPath
     *            the normalized endpoint path (e.g., "/mdr/adam/adam-2-1").
     * @return the cached content, or empty if not in cache.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    Optional<String> read(String aPath) throws IOException;


    /**
     * Writes content to the cache.
     *
     * @param aPath
     *            the normalized endpoint path.
     * @param aContent
     *            the content to cache.
     */
    void write(String aPath, String aContent);


    /**
     * Removes the cached entry for the given endpoint path.
     *
     * @param aPath
     *            the endpoint path.
     * @return {@code true} if a cache entry was removed, {@code false} otherwise.
     * @throws IOException
     *             in case of an I/O error.
     */
    boolean invalidate(String aPath) throws IOException;


    /**
     * Returns the timestamp of the cached entry for the given endpoint path, typically the time at
     * which the entry was written.
     *
     * @param aPath
     *            the normalized endpoint path.
     * @return the cache entry timestamp in milliseconds since the epoch, or empty if the entry does
     *         not exist or the implementation does not support timestamps.
     * @throws IOException
     *             in case of an I/O error.
     */
    default OptionalLong cacheTimestamp(String aPath) throws IOException
    {
        return OptionalLong.empty();
    }


    /**
     * Reads a cached response as a {@link CacheEntry}, including HTTP status code, response
     * headers, and body content.
     *
     * <p>
     * The default implementation delegates to {@link #read(String)} and wraps the content in a
     * {@code CacheEntry} with status 200 and empty headers. Implementations that store response
     * metadata should override this method.
     * </p>
     *
     * @param aPath
     *            the normalized endpoint path.
     * @return the cached entry, or empty if not in cache.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    default Optional<CacheEntry> readEntry(String aPath) throws IOException
    {
        return read(aPath).map(CacheEntry::new);
    }


    /**
     * Writes a {@link CacheEntry} (body content plus HTTP metadata) to the cache.
     *
     * <p>
     * The default implementation delegates to {@link #write(String, String)}, discarding the
     * metadata. Implementations that support metadata storage should override this method.
     * </p>
     *
     * @param aPath
     *            the normalized endpoint path.
     * @param aEntry
     *            the cache entry to write.
     */
    default void writeEntry(String aPath, CacheEntry aEntry)
    {
        write(aPath, aEntry.content());
    }
}
