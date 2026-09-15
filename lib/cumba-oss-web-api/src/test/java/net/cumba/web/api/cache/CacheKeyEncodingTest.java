package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the Q14 ruling (2026-09-11): the cache key encoding is injective, and stays injective on a
 * case-insensitive file system.
 *
 * <p>
 * Measured before the fix: {@code toCacheFileName("/a/b")} and {@code toCacheFileName("/a_b")} both
 * answered {@code a_b.json}, so one endpoint's body was served for another with nothing failing —
 * {@code '/'} was mapped to {@code '_'} and {@code URLEncoder} then left that {@code '_'} alone. A
 * leading {@code '/'} was stripped as well, and letters were passed through, which collides on
 * Windows.
 * </p>
 */
class CacheKeyEncodingTest
{

    /**
     * Keys chosen to exercise every arm of the encoding at once: the historical collision pair, the
     * strip pair, upper case, the escape character itself, the space that {@code URLEncoder} used
     * to turn into {@code '+'}, the separator used by shortened keys, and multi-byte UTF-8.
     */
    private static final List<String> KEYS = List.of("/a/b", "/a_b", "a_b", "/a", "a", "/",
            "/mdr/ct/packages/sdtmct-2024-09-27/codelists/C66781/terms",
            "/mdr/ct/packages/sdtmct-2024-09-27/codelists/c66781/terms",
            "/mdr/search?q=hello world", "/mdr/search?q=a+b", "/mdr/search?q=100%", "/a~b",
            "/mdr/search?q=日本語", "/A", "/a.b-c");

    private static FileApiCache cache(Path aDir)
    {
        return new FileApiCache(aDir, ".json");
    }


    @Test
    void everyKeyGetsItsOwnFileName(@TempDir Path aTempDir)
    {
        FileApiCache cache = cache(aTempDir);
        Map<String, String> byName = new HashMap<>();
        for (String key : KEYS)
        {
            String previous = byName.put(cache.toCacheFileName(key), key);
            assertNull(previous, () -> "two keys share one file name: " + key + " and " + previous);
        }
    }


    @Test
    void theHistoricalCollisionIsGone(@TempDir Path aTempDir)
    {
        FileApiCache cache = cache(aTempDir);
        assertNotEquals(cache.toCacheFileName("/a/b"), cache.toCacheFileName("/a_b"),
                "a path separator and a literal underscore must not name one file");
        assertNotEquals(cache.toCacheFileName("/a"), cache.toCacheFileName("a"),
                "nothing is stripped, so these stay distinct too");
    }


    @Test
    void encodingRoundTrips()
    {
        for (String key : KEYS)
        {
            assertEquals(key, ApiCache.decodeKeyFromFileName(ApiCache.encodeKeyForFileName(key)),
                    "the encoding must lose nothing");
        }
    }


    @Test
    void theEncodedNameHasNoUpperCaseCharacter()
    {
        // This is what makes the mapping injective on a case-insensitive file system: folding the
        // name cannot change it, so two keys that differ only in case still name two files.
        for (String key : KEYS)
        {
            String encoded = ApiCache.encodeKeyForFileName(key);
            assertEquals(encoded, encoded.toLowerCase(Locale.ROOT), key);
        }
        assertEquals("%41", ApiCache.encodeKeyForFileName("A"));
        assertNotEquals(ApiCache.encodeKeyForFileName("/A"), ApiCache.encodeKeyForFileName("/a"));
    }


    @Test
    void exactlyTheseBytesStandForThemselves()
    {
        // The literal alphabet, asserted end to end rather than by example: it is what bounds the
        // name length and what keeps the name readable, and a single character slipping out of it
        // changes every file name in the cache.
        String literals = "abcdefghijklmnopqrstuvwxyz0123456789-.";
        assertEquals(literals, ApiCache.encodeKeyForFileName(literals));

        for (char ch : "ABYZ_%+ ~?=&:@\\".toCharArray())
        {
            String encoded = ApiCache.encodeKeyForFileName(String.valueOf(ch));
            assertEquals(3, encoded.length(),
                    "expected an escape for '" + ch + "', got " + encoded);
            assertTrue(encoded.startsWith("%"), encoded);
        }
        assertEquals("_", ApiCache.encodeKeyForFileName("/"), "the one byte that is not escaped");
    }


    @Test
    void anEscapeWhoseCaseWasFoldedStillDecodes()
    {
        // Only the hex digits of an escape can come back in a different case — every literal the
        // encoder emits is already lower case — and both readings give the same byte.
        assertEquals("J", ApiCache.decodeKeyFromFileName("%4a"));
        assertEquals("J", ApiCache.decodeKeyFromFileName("%4A"));
    }


    @Test
    void theReadableHeadSurvives(@TempDir Path aTempDir)
    {
        // The lower-case path segments are what make a cache directory diagnosable by hand, and
        // they are still there — only the upper-case letters and the punctuation are escaped.
        assertEquals("_mdr_ct_packages_sdtmct-2024-09-27.json",
                cache(aTempDir).toCacheFileName("/mdr/ct/packages/sdtmct-2024-09-27"));
    }


    @Test
    void aBrokenNameIsRejectedRatherThanGuessedAt()
    {
        assertThrows(IllegalArgumentException.class, () -> ApiCache.decodeKeyFromFileName("ab%4"));
        assertThrows(IllegalArgumentException.class, () -> ApiCache.decodeKeyFromFileName("ab%"));
        assertThrows(IllegalArgumentException.class, () -> ApiCache.decodeKeyFromFileName("a%zzb"));
    }


    @Test
    void theBodyWrittenUnderAKeyIsTheBodyReadBack(@TempDir Path aTempDir) throws IOException
    {
        // The end-to-end statement of the same thing: two keys that used to share a file now keep
        // their own bodies.
        FileApiCache cache = cache(aTempDir);
        cache.write("/a/b", "slash".getBytes(StandardCharsets.UTF_8));
        cache.write("/a_b", "underscore".getBytes(StandardCharsets.UTF_8));

        assertEquals("slash", new String(cache.read("/a/b").orElseThrow(), StandardCharsets.UTF_8));
        assertEquals("underscore",
                new String(cache.read("/a_b").orElseThrow(), StandardCharsets.UTF_8));
    }


    @Test
    void theLengthBoundMeasuresWhatReachesTheFileSystem(@TempDir Path aTempDir)
    {
        // encodedKeyLength is the encoder itself, so a key shortened to the bound really does
        // produce a file name within it — upper case costs three characters each, and measuring
        // the raw key instead would let that past.
        String key = ApiCache.shortenCacheKey("/mdr/search?q=" + "A".repeat(300));
        String fileName = cache(aTempDir).toCacheFileName(key) + ".meta";
        assertTrue(fileName.length() <= 255, "file name was " + fileName.length());
        assertTrue(key.contains(ApiCache.CACHE_KEY_HASH_SEPARATOR),
                "the fixture must actually be shortened, or this test proves nothing");
    }
}
