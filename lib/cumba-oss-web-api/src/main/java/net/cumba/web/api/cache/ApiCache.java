package net.cumba.web.api.cache;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import net.cumba.web.api.http.HttpResponse;
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
 * Path-based methods ({@link #read(String)}, {@link #write(String, byte[])},
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
     * {@code plans/done/PLAN-api-cache-key-query-strings.md} § 5, phase 1.
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
     * Serves a cached response <b>without materialising its body</b>, as an {@link HttpResponse}
     * whose {@link HttpResponse#body() body} is a live stream over the cache's own storage.
     *
     * <p>
     * This is the low-memory counterpart of {@link #get(HttpRequest)}. {@code get} has to read the
     * whole body into a {@link CacheEntry}, a full-size copy of data that is already on disk in
     * exactly the form the parser wants. For a multi-megabyte CDISC Library response that copy is
     * pure garbage. Streaming skips it.
     * </p>
     *
     * <p>
     * <b>The returned response owns an open resource.</b> Unlike the buffered response
     * {@code get(...)} yields, closing it is not optional: it holds a file handle until
     * {@link HttpResponse#close()} runs. Callers must use try-with-resources.
     * </p>
     *
     * <p>
     * Returning {@link Optional#empty()} is always allowed and always safe — it means "no streaming
     * answer available, ask {@link #get(HttpRequest)}". Implementations return empty for a cache
     * miss, and must also return empty when a configured {@link CacheValidator}
     * {@linkplain CacheValidator#needsContent() needs the entry content}, since a streaming read
     * cannot supply it. The default implementation always returns empty, so every existing
     * {@code ApiCache} keeps working unchanged.
     * </p>
     *
     * @param aRequest
     *            the HTTP request to look up.
     * @return a response streaming the cached body, or empty when this cache cannot serve the
     *         request that way.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    default Optional<HttpResponse> openStream(HttpRequest aRequest) throws IOException
    {
        return Optional.empty();
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
     * this method exists to avoid. See {@code plans/done/PLAN-api-cache-key-query-strings.md} § 2
     * before adding to it.
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
     * Returns the length a key occupies once encoded for use as a file name.
     *
     * <p>
     * It calls the encoder itself rather than reproducing it, so the bound applies to what actually
     * reaches the file system and cannot drift away from it — the two were a mirrored pair of
     * expressions until Q14 changed the encoding, and a mirror is exactly the kind of check that
     * stops checking without saying so.
     * </p>
     *
     * @param aKey
     *            the key, or a fragment of one.
     * @return the encoded length in characters.
     */
    private static int encodedKeyLength(String aKey)
    {
        return encodeKeyForFileName(aKey).length();
    }


    /**
     * Encodes a cache key into the body of a file name — everything before the extension.
     *
     * <p>
     * <b>Q14 (2026-09-11).</b> The previous encoding mapped {@code '/'} to {@code '_'} and then
     * URL-encoded the result, but {@code '_'} is URL-safe and was left alone, so {@code /a/b} and
     * {@code /a_b} produced <b>the same file</b> and one endpoint's body was served for another
     * with nothing failing. It also stripped a leading {@code '/'}, collapsing {@code /a} onto
     * {@code a}, and it passed letters through unchanged — which collides on a case-insensitive
     * file system, and {@code win_x64} is a shipped platform.
     * </p>
     *
     * <p>
     * The encoding here is injective, and demonstrably so. Each byte of the UTF-8 key maps to one
     * of three disjoint output forms:
     * </p>
     * <ul>
     * <li>{@code a}–{@code z}, {@code 0}–{@code 9}, {@code -} and {@code .} stand for
     * themselves;</li>
     * <li>{@code '/'} — and only {@code '/'} — becomes {@code '_'};</li>
     * <li>every other byte becomes {@code %} followed by two <i>lower-case</i> hex digits.</li>
     * </ul>
     * <p>
     * No form can be mistaken for another ({@code '%'} itself is escaped, and the escape is
     * fixed-width), so the output is uniquely decodable left to right — see
     * {@link #decodeKeyFromFileName(String)} — and therefore injective. Nothing is stripped, so
     * {@code /a} and {@code a} stay distinct too.
     * </p>
     *
     * <p>
     * <b>Case-insensitive file systems.</b> Upper-case letters are escaped rather than passed
     * through, and the hex digits are lower case, so the encoded name <b>contains no upper-case
     * character at all</b>. Case-folding is the identity on it, which makes the mapping injective
     * on Windows and on a default macOS volume exactly as it is on ext4 — a mapping that is
     * injective only until the file system folds it is not injective where it matters. It costs
     * three characters per upper-case letter, which is why {@code ADSL} reads as
     * {@code %41%44%53%4c}; the lower-case path segments around it stay readable, which is what
     * makes a cache directory diagnosable by hand.
     * </p>
     *
     * <p>
     * ⚠ Cache files written by an earlier version are not readable under this encoding and are
     * simply never hit again; the ruling that asked for this fix waived backward compatibility
     * explicitly. A stale directory costs disk, not correctness.
     * </p>
     *
     * @param aKey
     *            the cache key.
     * @return the encoded file-name body.
     */
    static String encodeKeyForFileName(String aKey)
    {
        StringBuilder encoded = new StringBuilder(aKey.length() + 8);
        for (byte raw : aKey.getBytes(StandardCharsets.UTF_8))
        {
            char ch = (char) (raw & 0xFF);
            if (ch >= 'a' && ch <= 'z' || ch >= '0' && ch <= '9' || ch == '-' || ch == '.')
            {
                encoded.append(ch);
            }
            else if (ch == '/')
            {
                encoded.append('_');
            }
            else
            {
                encoded.append('%').append(HexFormat.of().toHexDigits(raw));
            }
        }
        return encoded.toString();
    }


    /**
     * Recovers the cache key from a file name body produced by
     * {@link #encodeKeyForFileName(String)} — the inverse, and the evidence that the encoding loses
     * nothing.
     *
     * <p>
     * Upper-case hex digits are accepted, so a name read back from a file system that folded its
     * case still decodes.
     * </p>
     *
     * @param aName
     *            the file name body, without the extension.
     * @return the cache key.
     * @throws IllegalArgumentException
     *             if the name is not something this encoder could have produced.
     */
    static String decodeKeyFromFileName(String aName)
    {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream(aName.length());
        int at = 0;
        while (at < aName.length())
        {
            char ch = aName.charAt(at);
            if (ch == '_')
            {
                decoded.write('/');
                at++;
            }
            else if (ch == '%')
            {
                if (at + 2 >= aName.length())
                {
                    throw new IllegalArgumentException("truncated escape in cache name: " + aName);
                }
                try
                {
                    decoded.write(HexFormat.fromHexDigits(aName, at + 1, at + 3));
                }
                catch (NumberFormatException aNotHex)
                {
                    throw new IllegalArgumentException("bad escape in cache name: " + aName,
                            aNotHex);
                }
                at += 3;
            }
            else
            {
                decoded.write(ch);
                at++;
            }
        }
        return decoded.toString(StandardCharsets.UTF_8);
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
     * Reads cached content for the given endpoint path, as the raw bytes that were stored.
     *
     * <p>
     * The bytes are returned undecoded — see {@link CacheEntry} for why this API deals in
     * {@code byte[]} rather than {@code String}, and for how to decode them safely when characters
     * are what you actually want.
     * </p>
     *
     * @param aPath
     *            the normalized endpoint path (e.g., "/mdr/adam/adam-2-1").
     * @return the cached content, or empty if not in cache.
     * @throws IOException
     *             in case of an I/O error reading the cache.
     */
    Optional<byte[]> read(String aPath) throws IOException;


    /**
     * Writes content to the cache, byte for byte.
     *
     * <p>
     * Implementations must store and return exactly these bytes: a body is not text as far as this
     * API is concerned, and no charset is applied on the way in or out.
     * </p>
     *
     * @param aPath
     *            the normalized endpoint path.
     * @param aContent
     *            the content to cache, as raw bytes.
     */
    void write(String aPath, byte[] aContent);


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
     * The default implementation delegates to {@link #write(String, byte[])}, discarding the
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
