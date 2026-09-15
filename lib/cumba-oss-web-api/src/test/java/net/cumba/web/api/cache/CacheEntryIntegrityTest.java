package net.cumba.web.api.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import net.cumba.web.api.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the cache paths that hand back a plausible wrong answer rather than an error: a body whose
 * metadata sidecar never landed, the value semantics of {@link CacheEntry}, and the accessors and
 * defaults nothing had reached.
 */
class CacheEntryIntegrityTest
{

    /** Exposes the protected path helpers so a test can block the sidecar write. */
    private static final class Probe extends FileApiCache
    {

        Probe(Path aDir)
        {
            super(aDir, ".json");
        }


        Path metaOf(String aPath)
        {
            return metaFilePath(cacheDir().resolve(toCacheFileName(aPath)));
        }


        Path bodyOf(String aPath)
        {
            return cacheDir().resolve(toCacheFileName(aPath));
        }


        CacheValidator exposedValidator()
        {
            return validator();
        }
    }

    @Test
    void aBodyWhoseSidecarCannotBeWrittenIsNotLeftBehind(@TempDir Path aDir) throws IOException
    {
        Probe cache = new Probe(aDir);

        // Block the sidecar: Files.move onto a NON-EMPTY directory fails, so writeMetaFile
        // cannot land while the body write succeeds.
        Path meta = cache.metaOf("/p");
        Files.createDirectories(meta);
        Files.write(meta.resolve("blocker"), new byte[]
        {
                1
        });

        cache.writeEntry("/p",
                new CacheEntry(404, Map.of("ETag", List.of("v1")), "gone".getBytes(UTF_8())));

        assertFalse(Files.exists(cache.bodyOf("/p")),
                "the body must not survive its own metadata failing to write");
        assertTrue(cache.read("/p").isEmpty());
        assertTrue(cache.readEntry("/p").isEmpty(),
                "a body with no sidecar would be served back as a 200 with no headers");
    }


    @Test
    void acompleteEntryKeepsItsStatusAndHeaders(@TempDir Path aDir) throws IOException
    {
        Probe cache = new Probe(aDir);
        cache.writeEntry("/p",
                new CacheEntry(404, Map.of("ETag", List.of("v1")), "body".getBytes(UTF_8())));

        CacheEntry read = cache.readEntry("/p").orElseThrow();
        assertEquals(404, read.statusCode(), "the stored status must survive the round trip");
        assertEquals(List.of("v1"), read.headers().get("ETag"));
        assertEquals("body", new String(read.content(), UTF_8()));
    }


    @Test
    void invalidateRemovesBothTheBodyAndItsSidecar(@TempDir Path aDir) throws IOException
    {
        Probe cache = new Probe(aDir);
        cache.writeEntry("/p", new CacheEntry(200, Map.of(), "b".getBytes(UTF_8())));
        assertTrue(Files.exists(cache.bodyOf("/p")));
        assertTrue(Files.exists(cache.metaOf("/p")));

        assertTrue(cache.invalidate("/p"), "an entry was there, so something was removed");
        assertFalse(Files.exists(cache.bodyOf("/p")));
        assertFalse(Files.exists(cache.metaOf("/p")), "the sidecar must not outlive its body");

        assertFalse(cache.invalidate("/p"), "nothing left to remove the second time");
    }


    @Test
    void theConfiguredValidatorIsReadableBySubclasses(@TempDir Path aDir)
    {
        CacheValidator validator = new TtlCacheValidator(300_000L);
        assertEquals(validator, new FileApiCache(aDir, ".json", validator)
        {

            CacheValidator peek()
            {
                return validator();
            }
        }.peek());
        assertEquals(null, new Probe(aDir).exposedValidator(), "none configured");
    }


    @Test
    void cacheTimestampDefaultsToUnsupportedRatherThanToZero() throws IOException
    {
        ApiCache noTimestamps = new ApiCache()
        {

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
        };
        assertEquals(OptionalLong.empty(), noTimestamps.cacheTimestamp("/p"),
                "'not supported' must not be reported as the epoch");
    }


    @Test
    void fileCacheTimestampIsPresentOnlyForAnEntryThatExists(@TempDir Path aDir) throws IOException
    {
        FileApiCache cache = new FileApiCache(aDir, ".json");
        assertEquals(OptionalLong.empty(), cache.cacheTimestamp("/p"));
        cache.write("/p", "b".getBytes(UTF_8()));
        assertTrue(cache.cacheTimestamp("/p").isPresent());
    }


    @Test
    void aParameterWithAnEmptyNameIsMatchedByThatEmptyName()
    {
        // The parameter name is the text before the first '=', so "=v" is a parameter named "".
        ApiCache cache = new ApiCache()
        {

            @Override
            public Set<String> nonSemanticQueryParameters()
            {
                return Set.of("");
            }


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
        };
        assertEquals("a=1", cache.normaliseQuery("=v&a=1"));
        assertEquals("a=1&b=2", cache.normaliseQuery("b=2&a=1"), "pairs are sorted");
        assertEquals("", cache.normaliseQuery(null));
        assertEquals("", cache.normaliseQuery(""));
    }

    @org.junit.jupiter.api.Nested
    class EntryValueSemantics
    {

        private final CacheEntry base = new CacheEntry(200, Map.of("A", List.of("1")),
                "body".getBytes(UTF_8()));

        @Test
        void anEntryEqualsItself()
        {
            assertEquals(base, base);
            assertTrue(base.equals(base));
        }


        @Test
        void equalEntriesCompareEqualEvenWithDistinctByteArrays()
        {
            CacheEntry twin = new CacheEntry(200, Map.of("A", List.of("1")),
                    "body".getBytes(UTF_8()));
            assertEquals(base, twin, "the body is compared by value, not by array identity");
            assertEquals(base.hashCode(), twin.hashCode());
        }


        @Test
        void everyFieldParticipatesInEquality()
        {
            assertNotEquals(base,
                    new CacheEntry(404, Map.of("A", List.of("1")), "body".getBytes(UTF_8())),
                    "status code");
            assertNotEquals(base,
                    new CacheEntry(200, Map.of("A", List.of("2")), "body".getBytes(UTF_8())),
                    "headers");
            assertNotEquals(base,
                    new CacheEntry(200, Map.of("A", List.of("1")), "other".getBytes(UTF_8())),
                    "body bytes");
        }


        @Test
        void hashCodeFollowsTheContentRatherThanBeingAConstant()
        {
            CacheEntry other = new CacheEntry(200, Map.of("A", List.of("1")),
                    "different".getBytes(UTF_8()));
            assertNotEquals(base.hashCode(), other.hashCode());
        }


        @Test
        void anythingThatIsNotACacheEntryIsNotEqualToOne()
        {
            assertNotEquals(base, "body");
            assertNotEquals(base, null);
            assertFalse(base.equals(Integer.valueOf(200)));
        }


        @Test
        void ofCapturesTheResponseStatusAndHeaders()
        {
            HttpResponse response = new HttpResponse(404, Map.of("X", List.of("y")), null);
            CacheEntry entry = CacheEntry.of(response, "b".getBytes(UTF_8()));
            assertEquals(404, entry.statusCode());
            assertEquals(List.of("y"), entry.headers().get("X"));
            assertEquals("b", new String(entry.content(), UTF_8()));
        }


        @Test
        void toStringReportsTheBodySizeRatherThanTheBody()
        {
            assertTrue(base.toString().contains("4 bytes"), base.toString());
            assertFalse(base.toString().contains("body"), "the body itself must not be printed");
        }
    }

    private static java.nio.charset.Charset UTF_8()
    {
        return StandardCharsets.UTF_8;
    }

}
