package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.cumba.web.api.http.HttpRequest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers {@link ApiCache#toCacheKey(net.cumba.web.api.http.HttpRequest)} taking the query string
 * into account: normalisation, the legacy path-only read fallback, and the length threshold past
 * which the tail of a key is hashed.
 *
 * <p>
 * See {@code plans/PLAN-api-cache-key-query-strings.md}. Two requests differing only in query
 * string used to share one cache entry, so the first response was served for the second.
 * </p>
 */
class ApiCacheKeyQueryTest
{

    private static final String BASE = "http://example.com";

    private static HttpRequest requestFor(String aPath)
    {
        return HttpRequest.get(URI.create(BASE + aPath)).build();
    }


    private static String keyFor(ApiCache aCache, String aPath)
    {
        return aCache.toCacheKey(requestFor(aPath));
    }

    /**
     * A cache that declares {@code expand} non-semantic. The <b>mechanism</b> is what this test
     * pins; the real contents of the set are decided in the plan's phase 2 and are empty today.
     */
    private static final class ExpandIgnoringCache extends FileApiCache
    {

        ExpandIgnoringCache(Path aCacheDir)
        {
            super(aCacheDir, ".json");
        }


        @Override
        public Set<String> nonSemanticQueryParameters()
        {
            return Set.of("expand");
        }
    }


    /**
     * Minimal cache exercising {@link ApiCache}'s own default {@code get} and {@code invalidate} —
     * {@link FileApiCache} overrides both, so they would otherwise go untested.
     */
    private static final class InMemoryApiCache implements ApiCache
    {

        private final Map<String, String> entries = new HashMap<>();

        @Override
        public Optional<String> read(String aPath)
        {
            return Optional.ofNullable(entries.get(aPath));
        }


        @Override
        public void write(String aPath, String aContent)
        {
            entries.put(aPath, aContent);
        }


        @Override
        public boolean invalidate(String aPath)
        {
            return entries.remove(aPath) != null;
        }
    }


    @Nested
    class KeyDerivation
    {

        @Test
        void queryLessRequestKeepsTodaysKey(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals("/mdr/adam", keyFor(cache, "/mdr/adam"));
        }


        @Test
        void differentQueriesProduceDifferentKeys(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            String alpha = keyFor(cache, "/mdr/search?q=alpha");
            String beta = keyFor(cache, "/mdr/search?q=beta");
            assertNotEquals(alpha, beta);
            assertNotEquals(cache.toCacheFileName(alpha), cache.toCacheFileName(beta));
        }


        @Test
        void parameterOrderDoesNotMatter(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals(keyFor(cache, "/mdr/x?a=1&b=2"), keyFor(cache, "/mdr/x?b=2&a=1"));
        }


        @Test
        void emptyQueryIsTreatedAsAbsent(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals("/mdr/x", keyFor(cache, "/mdr/x?"));
        }


        @Test
        void emptyPairsAreDropped(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals(keyFor(cache, "/mdr/x?a=1&b=2"), keyFor(cache, "/mdr/x?a=1&&b=2"));
        }


        @Test
        void repeatedParametersArePreserved(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            String key = keyFor(cache, "/mdr/x?ct=a&ct=b");
            assertEquals("/mdr/x?ct=a&ct=b", key);
            assertNotEquals(key, keyFor(cache, "/mdr/x?ct=a"));
        }


        @Test
        void opaqueUriYieldsRoot(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            HttpRequest request = HttpRequest.get(URI.create("mailto:user@example.com")).build();
            assertEquals("/", cache.toCacheKey(request));
        }


        @Test
        void nonSemanticParametersAreExcluded(@TempDir Path aTempDir)
        {
            ExpandIgnoringCache cache = new ExpandIgnoringCache(aTempDir);
            assertEquals("/mdr/products", keyFor(cache, "/mdr/products?expand=true"));
            // …and only for the declared parameter
            assertEquals("/mdr/products?q=x", keyFor(cache, "/mdr/products?expand=true&q=x"));
        }


        @Test
        void encodedSeparatorsInsideAValueAreNotSplit(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            // %26 is an '&' belonging to the value, not a pair separator; the pair name is "q"
            assertEquals("/mdr/x?q=a%26b=c", keyFor(cache, "/mdr/x?q=a%26b=c"));
            assertNotEquals(keyFor(cache, "/mdr/x?q=a%26b=c"), keyFor(cache, "/mdr/x?b=c&q=a"));
        }


        @Test
        void valuelessAndNamelessPairsSurvive(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals("/mdr/x?flag", keyFor(cache, "/mdr/x?flag"));
            assertEquals("/mdr/x?=v", keyFor(cache, "/mdr/x?=v"));
            assertNotEquals(keyFor(cache, "/mdr/x?flag"), keyFor(cache, "/mdr/x"));
        }


        @Test
        void semicolonIsNotAPairSeparator(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            // Only '&' separates pairs — a ';' stays inside the value it was written in
            assertEquals("/mdr/x?a=1;b=2", keyFor(cache, "/mdr/x?a=1;b=2"));
            assertNotEquals(keyFor(cache, "/mdr/x?a=1;b=2"), keyFor(cache, "/mdr/x?a=1&b=2"));
        }


        @Test
        void parameterNamesAreCaseSensitive(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertNotEquals(keyFor(cache, "/mdr/x?Expand=true"),
                    keyFor(cache, "/mdr/x?expand=true"));
        }


        @Test
        void nonSemanticSetIsEmptyByDefault(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals(Set.of(), cache.nonSemanticQueryParameters());
            assertEquals("/mdr/products?expand=true", keyFor(cache, "/mdr/products?expand=true"));
        }
    }


    @Nested
    class Storage
    {

        @Test
        void writeUnderOneQueryIsNotReadableUnderAnother(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            cache.put(requestFor("/mdr/search?q=a"), new CacheEntry("alpha"));

            Optional<CacheEntry> other = cache.get(requestFor("/mdr/search?q=b"));
            assertTrue(other.isEmpty(),
                    "a different query must not hit the entry written for ?q=a");
            assertEquals("alpha", cache.get(requestFor("/mdr/search?q=a")).orElseThrow().content());
        }
    }


    /**
     * The legacy fallback keeps caches written before the query participated in the key readable.
     * It is read-only: nothing writes under the legacy key, so the legacy set drains and is never
     * extended.
     */
    @Nested
    class LegacyKeyFallback
    {

        @Test
        void legacyPathOnlyEntryIsStillReadable(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            // Written the way the live cache at /data/cdisc.metadata.library-cache holds it
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");

            Optional<CacheEntry> hit = cache.get(requestFor("/api/mdr/adam/adam-2-1?expand=true"));
            assertTrue(hit.isPresent(), "a path-only entry must remain reachable");
            assertEquals("legacy-body", hit.get().content());
        }


        @Test
        void newWriteIsNotReadableUnderTheLegacyKey(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            cache.put(requestFor("/api/mdr/adam/adam-2-1?expand=true"), new CacheEntry("fresh"));

            assertTrue(cache.read("/api/mdr/adam/adam-2-1").isEmpty(),
                    "writes must not extend the legacy key set");
            assertEquals("fresh", cache.read("/api/mdr/adam/adam-2-1?expand=true").orElseThrow());
        }


        @Test
        void freshEntryWinsOverTheLegacyOne(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");
            cache.put(requestFor("/api/mdr/adam/adam-2-1?expand=true"), new CacheEntry("fresh"));

            assertEquals("fresh", cache.get(requestFor("/api/mdr/adam/adam-2-1?expand=true"))
                    .orElseThrow().content());
        }


        @Test
        void invalidateAlsoDropsTheLegacyEntry(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");

            assertTrue(cache.invalidate(requestFor("/api/mdr/adam/adam-2-1?expand=true")));
            assertTrue(cache.read("/api/mdr/adam/adam-2-1").isEmpty(),
                    "the legacy entry must be gone from storage, not merely unreachable");
            assertTrue(cache.get(requestFor("/api/mdr/adam/adam-2-1?expand=true")).isEmpty(),
                    "an invalidated request must not keep serving the legacy entry");
        }


        @Test
        void defaultImplementationFallsBackToTheLegacyKey() throws IOException
        {
            InMemoryApiCache cache = new InMemoryApiCache();
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");

            assertEquals("legacy-body", cache.get(requestFor("/api/mdr/adam/adam-2-1?expand=true"))
                    .orElseThrow().content());
        }


        @Test
        void defaultImplementationPrefersTheCurrentKey() throws IOException
        {
            InMemoryApiCache cache = new InMemoryApiCache();
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");
            cache.put(requestFor("/api/mdr/adam/adam-2-1?expand=true"), new CacheEntry("fresh"));

            assertEquals("fresh", cache.get(requestFor("/api/mdr/adam/adam-2-1?expand=true"))
                    .orElseThrow().content());
            assertEquals("legacy-body", cache.read("/api/mdr/adam/adam-2-1").orElseThrow());
        }


        @Test
        void defaultImplementationInvalidatesBothKeys() throws IOException
        {
            InMemoryApiCache cache = new InMemoryApiCache();
            cache.write("/api/mdr/adam/adam-2-1", "legacy-body");
            cache.put(requestFor("/api/mdr/adam/adam-2-1?expand=true"), new CacheEntry("fresh"));

            assertTrue(cache.invalidate(requestFor("/api/mdr/adam/adam-2-1?expand=true")));
            assertTrue(cache.read("/api/mdr/adam/adam-2-1").isEmpty());
            assertTrue(cache.read("/api/mdr/adam/adam-2-1?expand=true").isEmpty());
        }


        /**
         * The fallback's known limitation, pinned deliberately: the legacy key of a request and the
         * current key of the <i>query-less</i> form of the same path are the same string, so a body
         * fetched without a query is served to a request that carries one. This is what the cache
         * already did before the query participated in the key — not a regression — and it is why
         * the query only truly participates once the stored entries have been re-keyed. If this
         * test ever fails, the fallback has been narrowed and the plan's phase 4 note is stale.
         */
        @Test
        void aQueryLessEntryIsStillServedToAQueryBearingRequest(@TempDir Path aTempDir)
            throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            cache.put(requestFor("/mdr/products"), new CacheEntry("not-expanded"));

            assertEquals("not-expanded",
                    cache.get(requestFor("/mdr/products?expand=true")).orElseThrow().content());
        }


        @Test
        void legacyKeyIsTodaysPathOnlyDerivation(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            assertEquals("/mdr/search", cache.toLegacyCacheKey(requestFor("/mdr/search?q=alpha")));
            assertEquals("/", cache.toLegacyCacheKey(
                    HttpRequest.get(URI.create("mailto:user@example.com")).build()));
        }
    }


    /**
     * A file-backed cache names files after the key, so an unbounded key can outgrow the 255-byte
     * file-name limit. Only the tail is hashed — the head stays readable so the directory can be
     * inspected by hand.
     */
    @Nested
    class LengthThreshold
    {

        private static final int MAX = ApiCache.MAX_ENCODED_CACHE_KEY_LENGTH;

        private static String pathOfLength(int aLength)
        {
            return "/" + "a".repeat(aLength - 1);
        }


        /**
         * Measures a key the way {@code FileApiCache.toCacheFileName} will: slashes become
         * underscores, then the whole thing is URL-encoded. Computed here independently of the
         * implementation so the bound is asserted, not restated.
         */
        private static int encodedLength(String aKey)
        {
            return URLEncoder.encode(aKey.replace('/', '_'), StandardCharsets.UTF_8).length();
        }


        @Test
        void keyAtTheThresholdIsKeptVerbatim(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            String path = pathOfLength(MAX);
            assertEquals(MAX, encodedLength(path), "fixture must sit exactly on the threshold");
            assertEquals(path, keyFor(cache, path));
        }


        @Test
        void keyPastThresholdHashesTheTail(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            String path = pathOfLength(MAX + 1);
            String key = keyFor(cache, path);

            assertNotEquals(path, key);
            int head = MAX - encodedLength(ApiCache.CACHE_KEY_HASH_SEPARATOR)
                    - ApiCache.CACHE_KEY_HASH_LENGTH;
            assertEquals(path.substring(0, head), key.substring(0, head),
                    "the head of the key must stay readable");
            assertTrue(key.startsWith(path.substring(0, head) + ApiCache.CACHE_KEY_HASH_SEPARATOR));
        }


        @Test
        void shortenedKeyRespectsTheEncodedBound(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            // '~' costs three encoded characters, not one — the bound is on the encoded length, so
            // an all-ASCII fixture is exactly the case where the difference would go unnoticed.
            for (String path : new String[]
            {
                    pathOfLength(MAX + 1), pathOfLength(MAX * 3), "/mdr/search?q=" + "日".repeat(50)
            })
            {
                String key = keyFor(cache, path);
                assertTrue(encodedLength(key) <= MAX, () -> "encoded key length "
                        + encodedLength(key) + " exceeds the " + MAX + " bound for " + key);
            }
        }


        @Test
        void hashedKeysAreStableAndDistinguishTheHiddenTail(@TempDir Path aTempDir)
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            String head = "/mdr/search?q=" + "x".repeat(MAX);
            String first = keyFor(cache, head + "aaa");
            String second = keyFor(cache, head + "bbb");

            assertTrue(first.contains(ApiCache.CACHE_KEY_HASH_SEPARATOR),
                    "the fixture must actually be shortened, or this test proves nothing");
            assertTrue(first.length() < head.length(), "a shortened key must be shorter");
            assertNotEquals(first, second, "keys sharing a head must differ in their digest");
            assertEquals(first, keyFor(cache, head + "aaa"));
        }


        @Test
        void hashedFileNameFitsTheFileSystemLimit(@TempDir Path aTempDir)
        {
            GzipFileApiCache cache = new GzipFileApiCache(aTempDir, ".json");
            // A user-supplied search term, three UTF-8 bytes per character — nine characters each
            // once URL-encoded, which is the worst case for the file name.
            String key = keyFor(cache, "/mdr/search?q=" + "日".repeat(200));
            String fileName = cache.toCacheFileName(key) + ".meta";
            assertTrue(fileName.length() <= 255,
                    "file name must stay within the 255-byte limit, was " + fileName.length());
        }


        @Test
        void hashedKeyRoundTripsThroughTheCache(@TempDir Path aTempDir) throws IOException
        {
            FileApiCache cache = new FileApiCache(aTempDir, ".json");
            HttpRequest request = requestFor("/mdr/search?q=" + "z".repeat(MAX));
            cache.put(request, new CacheEntry("body"));

            assertEquals("body", cache.get(request).orElseThrow().content());
            assertFalse(cache.get(requestFor("/mdr/search?q=" + "y".repeat(MAX))).isPresent());
        }
    }
}
